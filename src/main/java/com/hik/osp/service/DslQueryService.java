package com.hik.osp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hik.osp.dto.ColumnMapping;
import com.hik.osp.dto.MappingConfig;
import com.hik.osp.dto.TableMapping;
import com.hik.osp.dto.request.DslQueryRequest;
import com.hik.osp.dto.request.DslQueryRequest.*;
import com.hik.osp.dto.response.DslQueryResponse;
import com.hik.osp.entity.DbConnectionEntity;
import com.hik.osp.entity.PropertyEntity;
import com.hik.osp.entity.TableImportEntity;
import com.hik.osp.enums.PropertyType;
import com.hik.osp.enums.RelationType;
import com.hik.osp.exception.BadRequestException;
import com.hik.osp.exception.ResourceNotFoundException;
import com.hik.osp.entity.ClassEntity;
import com.hik.osp.entity.OntologyEntity;
import com.hik.osp.repository.ClassRepository;
import com.hik.osp.repository.DbConnectionRepository;
import com.hik.osp.repository.OntologyRepository;
import com.hik.osp.repository.PropertyRepository;
import com.hik.osp.repository.TableImportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DslQueryService {

    private final TableImportRepository tableImportRepository;
    private final DbConnectionRepository dbConnectionRepository;
    private final OntologyRepository ontologyRepository;
    private final PropertyRepository propertyRepository;
    private final ClassRepository classRepository;
    private final ObjectMapper objectMapper;

    // ── Internal structures ──

    private static class EntityInfo {
        String className;
        String tableName;
        String dbConnectionId;
        String primaryKeyColumn;
        Map<String, String> propToCol;
        Map<String, String> colToProp;
    }

    private static class RelationInfo {
        String name;
        String sourceClass;
        String targetClass;
        List<Map<String, String>> mappingRules;
        RelationType relationType;
        String junctionTableName;
        String junctionDomainColumn;
        String junctionRangeColumn;
    }

    private static class PathResult {
        String entityName;
        String tableAlias;
        String joinSql;
    }

    // ── Public entry point ──

    public DslQueryResponse executeQuery(DslQueryRequest request) {
        DslQueryRequest.OntologyInfo ontologyInfo = request.getOntology();
        if (ontologyInfo == null || ontologyInfo.getName() == null || ontologyInfo.getName().isBlank()) {
            throw new BadRequestException("Ontology name is required");
        }

        OntologyEntity ontology;
        if (ontologyInfo.getNamespace() != null && !ontologyInfo.getNamespace().isBlank()) {
            ontology = ontologyRepository.findByNameAndNamespace(
                            ontologyInfo.getName(), ontologyInfo.getNamespace())
                    .orElseThrow(() -> new ResourceNotFoundException("Ontology",
                            ontologyInfo.getName() + " (" + ontologyInfo.getNamespace() + ")"));
        } else {
            ontology = ontologyRepository.findByName(ontologyInfo.getName())
                    .orElseThrow(() -> new ResourceNotFoundException("Ontology", ontologyInfo.getName()));
        }
        String ontologyId = ontology.getId();

        QueryBody q = request.getQuery();
        if (q == null || q.getTarget() == null || q.getTarget().isBlank()) {
            throw new BadRequestException("Query target is required");
        }

        Map<String, EntityInfo> entityLookup = buildEntityLookup(ontologyId);
        Map<String, RelationInfo> relationLookup = buildRelationLookup(ontologyId);

        String subjectEntity = q.getTarget();
        EntityInfo subjectInfo = entityLookup.get(subjectEntity);
        if (subjectInfo == null) {
            throw new BadRequestException("Target entity '" + subjectEntity
                    + "' not found in table mappings. Available: " + entityLookup.keySet());
        }

        // Alias map: entity name → SQL table alias
        Map<String, String> entityAliasMap = new LinkedHashMap<>();
        entityAliasMap.put(subjectEntity, "s0");

        // Separate paths: filter paths (for pagination) vs select relation paths
        List<List<String>> filterPaths = new ArrayList<>();
        collectFilterPaths(q.getFilter(), new LinkedHashSet<>(), filterPaths);

        List<List<String>> selectRelationPaths = collectSelectRelationPaths(q.getSelection());

        // All paths
        List<List<String>> allPaths = new ArrayList<>(filterPaths);
        allPaths.addAll(selectRelationPaths);

        // Resolve filter paths only (for pagination query)
        Map<String, PathResult> filterPathResults = resolveAllPaths(filterPaths, subjectEntity, entityLookup, relationLookup);
        // Resolve all paths (for data query)
        Map<String, PathResult> allPathResults = resolveAllPaths(allPaths, subjectEntity, entityLookup, relationLookup);

        for (Map.Entry<String, PathResult> e : allPathResults.entrySet()) {
            entityAliasMap.put(e.getValue().entityName, e.getValue().tableAlias);
        }

        List<SelectItem> relationSelectItems = collectRelationSelects(q.getSelection());
        boolean hasRelationSelect = !relationSelectItems.isEmpty();

        // Build shared components
        String fromJoinSql = buildFromJoinSql(subjectInfo, allPathResults);
        String whereSql = buildWhereSql(q.getFilter(), allPathResults, entityAliasMap, entityLookup);

        if (hasRelationSelect) {
            return executeNestedQuery(q, subjectEntity, subjectInfo, entityLookup,
                    filterPaths, filterPathResults, allPathResults, entityAliasMap,
                    relationSelectItems, fromJoinSql, whereSql);
        } else {
            return executeFlatQuery(q, subjectInfo, allPathResults, entityAliasMap,
                    entityLookup, fromJoinSql, whereSql);
        }
    }

    // ── Flat query execution (unchanged behavior) ──

    private DslQueryResponse executeFlatQuery(QueryBody q,
                                               EntityInfo subjectInfo,
                                               Map<String, PathResult> pathResults,
                                               Map<String, String> entityAliasMap,
                                               Map<String, EntityInfo> entityLookup,
                                               String fromJoinSql, String whereSql) {
        SqlBuilder sb = new SqlBuilder();
        List<String> columns = new ArrayList<>();

        sb.append("SELECT ");
        buildSelect(q.getSelection(), pathResults, entityAliasMap, entityLookup, sb, columns, true);

        sb.append(fromJoinSql);

        if (whereSql != null) {
            sb.append(" WHERE ").append(whereSql);
        }

        // Pagination
        long totalCount = 0;
        Pagination pag = q.getPagination();
        if (pag != null) {
            String countSql = "SELECT COUNT(*) AS cnt FROM (" + sb.toString() + ") _dsl_count";
            totalCount = executeCountQuery(subjectInfo, countSql);

            int limit = 0, offset = 0;
            if (pag.getLimit() != null && pag.getLimit() > 0) {
                limit = pag.getLimit();
                offset = pag.getOffset() != null ? pag.getOffset() : 0;
            } else if (pag.getSize() != null && pag.getSize() > 0) {
                limit = pag.getSize();
                int page = (pag.getPage() != null && pag.getPage() > 0) ? pag.getPage() : 1;
                offset = (page - 1) * limit;
            }
            if (limit > 0) {
                sb.append(" LIMIT ").append(limit);
                if (offset > 0) {
                    sb.append(" OFFSET ").append(offset);
                }
            }
        }

        String sql = sb.toString();
        List<Map<String, Object>> rows = executeQuery(subjectInfo, sql);

        if (pag == null) {
            totalCount = rows.size();
        }

        List<String> outputColumns = columns.isEmpty() ? getResultColumnNames(rows) : columns;

        return DslQueryResponse.builder()
                .columns(outputColumns)
                .rows(rows)
                .total(totalCount)
                .sql(sql)
                .message("Query executed successfully" + (totalCount > 0 ? ", returned " + rows.size() + " rows" : ""))
                .build();
    }

    // ── Nested query execution ──

    private DslQueryResponse executeNestedQuery(QueryBody q,
                                                 String subjectEntity,
                                                 EntityInfo subjectInfo,
                                                 Map<String, EntityInfo> entityLookup,
                                                 List<List<String>> filterPaths,
                                                 Map<String, PathResult> filterPathResults,
                                                 Map<String, PathResult> allPathResults,
                                                 Map<String, String> entityAliasMap,
                                                 List<SelectItem> relationSelectItems,
                                                 String fromJoinSql, String whereSql) {
        String idCol = findIdColumn(subjectInfo);
        String idProp = findIdProperty(subjectInfo);
        Pagination pag = q.getPagination();
        long totalCount = 0;
        List<Object> paginatedIds;

        // Phase 1: Pagination query — get distinct target IDs
        SqlBuilder pagSb = new SqlBuilder();
        pagSb.append("SELECT DISTINCT s0.").append(quoteId(idCol));
        pagSb.append(" FROM ").append(quoteId(subjectInfo.tableName)).append(" s0");

        // Build filter-only JOINs for pagination query
        String filterFromJoinSql = buildFilterFromJoinSql(subjectInfo, filterPathResults);
        pagSb.append(filterFromJoinSql.substring((" FROM " + quoteId(subjectInfo.tableName) + " s0").length()));
        // Hmm, this is fragile. Let me build it differently.
        // Actually, let me just use the filter path results to build JOINs.

        // Wait, I need to restructure. Let me build the pagination query from scratch.

        // Re-build pagination SQL with filter-only JOINs
        SqlBuilder pagSqlBuilder = new SqlBuilder();
        pagSqlBuilder.append("SELECT DISTINCT s0.").append(quoteId(idCol));
        pagSqlBuilder.append(" FROM ").append(quoteId(subjectInfo.tableName)).append(" s0");
        for (PathResult pr : filterPathResults.values()) {
            if (pr.joinSql != null && !pr.joinSql.isEmpty()) {
                pagSqlBuilder.append(pr.joinSql);
            }
        }
        if (whereSql != null) {
            pagSqlBuilder.append(" WHERE ").append(whereSql);
        }
        // Note: whereSql references table aliases which are consistent across both pathResults sets
        // because the alias indexing uses the same starting point.

        // Count
        String countSql = "SELECT COUNT(*) AS cnt FROM (" + pagSqlBuilder.toString() + ") _dsl_page";
        totalCount = executeCountQuery(subjectInfo, countSql);

        // Pagination
        int limit = 0, offset = 0;
        if (pag != null) {
            if (pag.getLimit() != null && pag.getLimit() > 0) {
                limit = pag.getLimit();
                offset = pag.getOffset() != null ? pag.getOffset() : 0;
            } else if (pag.getSize() != null && pag.getSize() > 0) {
                limit = pag.getSize();
                int page = (pag.getPage() != null && pag.getPage() > 0) ? pag.getPage() : 1;
                offset = (page - 1) * limit;
            }
        }
        if (limit > 0) {
            pagSqlBuilder.append(" LIMIT ").append(limit);
            if (offset > 0) {
                pagSqlBuilder.append(" OFFSET ").append(offset);
            }
        }

        // Execute pagination query to get IDs
        String pagSql = pagSqlBuilder.toString();
        List<Map<String, Object>> idRows = executeQuery(subjectInfo, pagSql);
        paginatedIds = idRows.stream()
                .map(r -> r.get(idCol))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (paginatedIds.isEmpty()) {
            return DslQueryResponse.builder()
                    .data(Collections.emptyList())
                    .total(totalCount)
                    .message("Query executed successfully, returned 0 rows")
                    .build();
        }

        // Phase 2: Build data query with all SELECTs + target ID
        SqlBuilder dataSb = new SqlBuilder();
        List<String> columns = new ArrayList<>();

        // Always add target ID for grouping
        dataSb.append("SELECT s0.").append(quoteId(idCol)).append(" AS _dsl_tid");
        if (q.getSelection() != null && !q.getSelection().isEmpty()) {
            dataSb.append(", ");
            buildSelect(q.getSelection(), allPathResults, entityAliasMap, entityLookup, dataSb, columns, true);
        } else {
            dataSb.append(", s0.*");
            columns.add("*");
        }

        dataSb.append(fromJoinSql);

        if (whereSql != null) {
            dataSb.append(" WHERE ").append(whereSql);
            dataSb.append(" AND s0.").append(quoteId(idCol)).append(" IN (");
            for (int i = 0; i < paginatedIds.size(); i++) {
                if (i > 0) dataSb.append(", ");
                Object idVal = paginatedIds.get(i);
                if (idVal instanceof Number) {
                    dataSb.append(idVal.toString());
                } else {
                    dataSb.append(quoteString(idVal.toString()));
                }
            }
            dataSb.append(")");
        } else {
            dataSb.append(" WHERE s0.").append(quoteId(idCol)).append(" IN (");
            for (int i = 0; i < paginatedIds.size(); i++) {
                if (i > 0) dataSb.append(", ");
                Object idVal = paginatedIds.get(i);
                if (idVal instanceof Number) {
                    dataSb.append(idVal.toString());
                } else {
                    dataSb.append(quoteString(idVal.toString()));
                }
            }
            dataSb.append(")");
        }

        // Phase 3: Execute and post-process
        String dataSql = dataSb.toString();
        List<Map<String, Object>> flatRows = executeQuery(subjectInfo, dataSql);

        List<Map<String, Object>> nestedData = buildNestedRows(flatRows, idProp, columns, relationSelectItems);

        // Derive columns from nested structure (top-level keys)
        List<String> outputColumns = nestedData.isEmpty() ? Collections.emptyList()
                : new ArrayList<>(nestedData.get(0).keySet());

        int returnedRows = nestedData.size();
        return DslQueryResponse.builder()
                .data(nestedData)
                .columns(outputColumns)
                .total(totalCount)
                .sql(dataSql)
                .message("Query executed successfully" + (returnedRows > 0 ? ", returned " + returnedRows + " rows" : ""))
                .build();
    }

    // ── Nested post-processing ──

    private List<Map<String, Object>> buildNestedRows(List<Map<String, Object>> flatRows,
                                                       String idProp,
                                                       List<String> columns,
                                                       List<SelectItem> relationSelectItems) {
        // Group flat rows by _dsl_tid
        Map<Object, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : flatRows) {
            Object tid = row.get("_dsl_tid");
            if (tid == null) continue;
            grouped.computeIfAbsent(tid, k -> new ArrayList<>()).add(row);
        }

        Set<String> relationNames = relationSelectItems.stream()
                .map(SelectItem::getRelation).collect(Collectors.toSet());
        List<Map<String, Object>> result = new ArrayList<>();

        for (List<Map<String, Object>> rows : grouped.values()) {
            if (rows.isEmpty()) continue;

            Map<String, Object> obj = new LinkedHashMap<>();
            // Collect target fields (same across all rows for this tid)
            for (Map<String, Object> row : rows) {
                for (Map.Entry<String, Object> cell : row.entrySet()) {
                    String col = cell.getKey();
                    if ("_dsl_tid".equals(col)) continue;
                    if (!startsWithAnyRelation(col, relationNames)) {
                        obj.putIfAbsent(col, cell.getValue());
                    }
                }
            }

            // Collect relation data recursively
            for (SelectItem si : relationSelectItems) {
                buildNestedData(si, "", rows, obj);
            }
            result.add(obj);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void buildNestedData(SelectItem si, String parentPrefix,
                                  List<Map<String, Object>> rows, Map<String, Object> obj) {
        if (si.getNestedFields() == null) return;
        String relName = si.getRelation();
        if (relName == null) return;
        String prefix = parentPrefix.isEmpty() ? relName : parentPrefix + "." + relName;

        // Separate plain field names from sub-relations
        List<String> fieldNames = new ArrayList<>();
        List<SelectItem> subRelations = new ArrayList<>();
        for (Object item : si.getNestedFields()) {
            if (item instanceof String) {
                fieldNames.add((String) item);
            } else if (item instanceof Map) {
                SelectItem subSi = objectMapper.convertValue(item, SelectItem.class);
                if (subSi.getRelation() != null) {
                    subRelations.add(subSi);
                }
            }
        }

        // Group rows by parent relation's field values so that sub-relations
        // only see rows belonging to the same parent entry
        Map<List<Object>, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            List<Object> key = new ArrayList<>();
            for (String field : fieldNames) {
                key.add(row.get(prefix + "." + field));
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        List<Map<String, Object>> relList = new ArrayList<>();
        for (Map.Entry<List<Object>, List<Map<String, Object>>> group : groups.entrySet()) {
            List<Map<String, Object>> groupRows = group.getValue();

            Map<String, Object> relObj = new LinkedHashMap<>();
            boolean hasValue = false;
            for (int i = 0; i < fieldNames.size(); i++) {
                Object val = group.getKey().get(i);
                if (val != null) hasValue = true;
                relObj.put(fieldNames.get(i), val);
            }

            // Process sub-relations using only rows from this parent group
            for (SelectItem subSi : subRelations) {
                buildNestedData(subSi, prefix, groupRows, relObj);
            }

            if (hasValue && !relList.contains(relObj)) {
                relList.add(relObj);
            }
        }

        if (!relList.isEmpty()) {
            obj.put(relName, relList);
        }
    }

    private boolean startsWithAnyRelation(String columnName, Set<String> relationNames) {
        for (String name : relationNames) {
            if (columnName.startsWith(name + ".")) {
                return true;
            }
        }
        return false;
    }

    // ── Path collection & resolution ──

    private List<List<String>> collectSelectRelationPaths(List<Object> selectItems) {
        Set<List<String>> paths = new LinkedHashSet<>();
        if (selectItems != null) {
            for (Object item : selectItems) {
                SelectItem si = toSelectItem(item);
                if (si != null && si.getRelation() != null && !si.getRelation().isBlank()) {
                    paths.add(Collections.singletonList(si.getRelation()));
                    collectNestedPaths(si, paths);
                }
            }
        }
        return new ArrayList<>(paths);
    }

    private void collectNestedPaths(SelectItem si, Set<List<String>> paths) {
        if (si.getNestedFields() == null) return;
        for (Object item : si.getNestedFields()) {
            if (item instanceof Map) {
                SelectItem subSi = objectMapper.convertValue(item, SelectItem.class);
                if (subSi.getRelation() != null && !subSi.getRelation().isBlank()) {
                    String parentPathStr = si.getRelation() + "." + subSi.getRelation();
                    paths.add(Arrays.asList(parentPathStr.split("\\.")));
                    collectNestedPaths(subSi, paths);
                }
            }
        }
    }

    private void collectFilterPaths(FilterGroup group, Set<List<String>> paths) {
        if (group == null || group.getConditions() == null) return;
        for (Object item : group.getConditions()) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                if (map.containsKey("logic")) {
                    FilterGroup sub = objectMapper.convertValue(map, FilterGroup.class);
                    collectFilterPaths(sub, paths);
                } else {
                    FilterCondition c = objectMapper.convertValue(map, FilterCondition.class);
                    if (c.getPath() != null && !c.getPath().isEmpty()) {
                        paths.add(c.getPath());
                    }
                }
            }
        }
    }

    // Collect filter paths into a list (for separation from select relation paths)
    private void collectFilterPaths(FilterGroup group, Set<List<String>> paths, List<List<String>> output) {
        collectFilterPaths(group, paths);
        output.addAll(paths);
    }

    private Map<String, PathResult> resolveAllPaths(List<List<String>> allPaths,
                                                      String subjectEntity,
                                                      Map<String, EntityInfo> entityLookup,
                                                      Map<String, RelationInfo> relationLookup) {
        Map<String, PathResult> results = new LinkedHashMap<>();
        int aliasIdx = 0;

        // Sort by path length so parent paths are resolved before sub-paths
        List<List<String>> sorted = new ArrayList<>(allPaths);
        sorted.sort(Comparator.comparingInt(List::size));

        for (List<String> path : sorted) {
            String key = String.join(".", path);
            if (results.containsKey(key)) continue;

            // Find longest existing prefix path to reuse its target as starting point
            PathResult prefixResult = null;
            int prefixLen = 0;
            for (int i = path.size() - 1; i > 0; i--) {
                String subKey = String.join(".", path.subList(0, i));
                PathResult existing = results.get(subKey);
                if (existing != null) {
                    prefixResult = existing;
                    prefixLen = i;
                    break;
                }
            }

            if (prefixResult != null) {
                // Only resolve the suffix from the prefix's target entity
                List<String> suffix = path.subList(prefixLen, path.size());
                PathResult pr = resolveSuffixPath(suffix, prefixResult, aliasIdx, entityLookup, relationLookup);
                results.put(key, pr);
                aliasIdx += suffix.size();
            } else {
                PathResult pr = resolveSinglePath(path, subjectEntity, aliasIdx, entityLookup, relationLookup);
                results.put(key, pr);
                aliasIdx += path.size();
            }
        }
        return results;
    }

    private PathResult resolveSuffixPath(List<String> suffix, PathResult fromResult,
                                          int startAliasIdx,
                                          Map<String, EntityInfo> entityLookup,
                                          Map<String, RelationInfo> relationLookup) {
        PathResult pr = new PathResult();
        StringBuilder joins = new StringBuilder();
        String currentEntity = fromResult.entityName;

        for (int i = 0; i < suffix.size(); i++) {
            String relName = suffix.get(i);
            RelationInfo rel = relationLookup.get(relName);
            if (rel == null) {
                throw new BadRequestException("Relation '" + relName + "' not found");
            }

            String direction = "OUT";
            String targetEntity = rel.targetClass;
            if (!rel.sourceClass.equals(currentEntity)) {
                if (rel.targetClass != null && rel.targetClass.equals(currentEntity)) {
                    targetEntity = rel.sourceClass;
                    direction = "IN";
                } else {
                    throw new BadRequestException("Cannot traverse relation '" + relName
                            + "' from entity '" + currentEntity + "'");
                }
            }

            String currentAlias = i == 0 ? fromResult.tableAlias : "t" + (startAliasIdx + i - 1);
            String targetAlias = "t" + (startAliasIdx + i);

            EntityInfo currentInfo = entityLookup.get(currentEntity);
            EntityInfo targetInfo = entityLookup.get(targetEntity);
            if (currentInfo == null) {
                throw new BadRequestException("Source entity '" + currentEntity + "' not found in table mappings");
            }
            if (targetInfo == null) {
                throw new BadRequestException("Target entity '" + targetEntity + "' not found in table mappings");
            }

            if (!currentInfo.dbConnectionId.equals(targetInfo.dbConnectionId)) {
                throw new BadRequestException("Cross-connection traversal not supported: '"
                        + currentEntity + "' and '" + targetEntity + "' are in different databases");
            }

            appendJoinSql(currentAlias, targetAlias, currentInfo, targetInfo, rel, direction, joins);
            currentEntity = targetEntity;
        }

        pr.entityName = currentEntity;
        pr.tableAlias = "t" + (startAliasIdx + suffix.size() - 1);
        pr.joinSql = joins.toString();
        return pr;
    }

    private PathResult resolveSinglePath(List<String> path, String subjectEntity,
                                          int startAliasIdx,
                                          Map<String, EntityInfo> entityLookup,
                                          Map<String, RelationInfo> relationLookup) {
        PathResult pr = new PathResult();
        StringBuilder joins = new StringBuilder();
        String currentEntity = subjectEntity;

        for (int i = 0; i < path.size(); i++) {
            String relName = path.get(i);
            RelationInfo rel = relationLookup.get(relName);
            if (rel == null) {
                throw new BadRequestException("Relation '" + relName + "' not found");
            }

            // Determine direction and target entity
            String direction = "OUT";
            String targetEntity = rel.targetClass;
            if (!rel.sourceClass.equals(currentEntity)) {
                if (rel.targetClass != null && rel.targetClass.equals(currentEntity)) {
                    targetEntity = rel.sourceClass;
                    direction = "IN";
                } else {
                    throw new BadRequestException("Cannot traverse relation '" + relName
                            + "' from entity '" + currentEntity + "'");
                }
            }

            String currentAlias = i == 0 ? "s0" : "t" + (startAliasIdx + i - 1);
            String targetAlias = "t" + (startAliasIdx + i);

            EntityInfo currentInfo = entityLookup.get(currentEntity);
            EntityInfo targetInfo = entityLookup.get(targetEntity);
            if (currentInfo == null) {
                throw new BadRequestException("Source entity '" + currentEntity + "' not found in table mappings");
            }
            if (targetInfo == null) {
                throw new BadRequestException("Target entity '" + targetEntity + "' not found in table mappings");
            }

            if (!currentInfo.dbConnectionId.equals(targetInfo.dbConnectionId)) {
                throw new BadRequestException("Cross-connection traversal not supported: '"
                        + currentEntity + "' and '" + targetEntity + "' are in different databases");
            }

            appendJoinSql(currentAlias, targetAlias, currentInfo, targetInfo, rel, direction, joins);
            currentEntity = targetEntity;
        }

        pr.entityName = currentEntity;
        pr.tableAlias = "t" + (startAliasIdx + path.size() - 1);
        pr.joinSql = joins.toString();
        return pr;
    }

    private void appendJoinSql(String fromAlias, String toAlias,
                                EntityInfo fromInfo, EntityInfo toInfo,
                                RelationInfo rel, String direction,
                                StringBuilder sb) {
        boolean isIn = "IN".equalsIgnoreCase(direction);
        String joinType = " INNER JOIN ";

        boolean isManyToMany = rel.relationType == RelationType.MANY_TO_MANY
                && rel.junctionTableName != null && !rel.junctionTableName.isBlank();

        if (isManyToMany) {
            String jAlias = "j" + toAlias.substring(1);
            // First JOIN: fromAlias → junction table
            sb.append(joinType).append(quoteId(rel.junctionTableName)).append(" ").append(jAlias);
            sb.append(" ON ");
            if (isIn) {
                // Traversing IN: fromAlias is the target/range entity
                sb.append(fromAlias).append(".").append(quoteId(resolveJunctionColumn(rel.junctionRangeColumn, fromInfo)))
                        .append(" = ").append(jAlias).append(".").append(quoteId(rel.junctionRangeColumn));
            } else {
                // Traversing OUT: fromAlias is the source/domain entity
                sb.append(fromAlias).append(".").append(quoteId(resolveJunctionColumn(rel.junctionDomainColumn, fromInfo)))
                        .append(" = ").append(jAlias).append(".").append(quoteId(rel.junctionDomainColumn));
            }
            // Second JOIN: junction table → toAlias
            sb.append(joinType).append(quoteId(toInfo.tableName)).append(" ").append(toAlias);
            sb.append(" ON ");
            if (isIn) {
                // toAlias is the source/domain entity, match via junctionDomainColumn
                sb.append(jAlias).append(".").append(quoteId(rel.junctionDomainColumn))
                        .append(" = ").append(toAlias).append(".").append(quoteId(resolveJunctionColumn(rel.junctionDomainColumn, toInfo)));
            } else {
                // toAlias is the target/range entity, match via junctionRangeColumn
                sb.append(jAlias).append(".").append(quoteId(rel.junctionRangeColumn))
                        .append(" = ").append(toAlias).append(".").append(quoteId(resolveJunctionColumn(rel.junctionRangeColumn, toInfo)));
            }
        } else {
            if (rel.mappingRules == null || rel.mappingRules.isEmpty()) {
                throw new BadRequestException("Relation '" + rel.name + "' has no mapping rules for JOIN");
            }
            sb.append(joinType).append(quoteId(toInfo.tableName)).append(" ").append(toAlias);
            sb.append(" ON ");
            for (int i = 0; i < rel.mappingRules.size(); i++) {
                if (i > 0) sb.append(" AND ");
                Map<String, String> rule = rel.mappingRules.get(i);
                String domainProp = rule.get("domain_property");
                String rangeProp = rule.get("range_property");
                if (isIn) {
                    sb.append(fromAlias).append(".").append(quoteId(resolveColumn(rangeProp, fromInfo)))
                            .append(" = ").append(toAlias).append(".").append(quoteId(resolveColumn(domainProp, toInfo)));
                } else {
                    sb.append(fromAlias).append(".").append(quoteId(resolveColumn(domainProp, fromInfo)))
                            .append(" = ").append(toAlias).append(".").append(quoteId(resolveColumn(rangeProp, toInfo)));
                }
            }
        }
    }

    // ── SELECT ──

    private void buildSelect(List<Object> selectItems,
                              Map<String, PathResult> pathResults,
                              Map<String, String> entityAliasMap,
                              Map<String, EntityInfo> entityLookup,
                              SqlBuilder sb, List<String> columns,
                              boolean useAlias) {
        if (selectItems == null || selectItems.isEmpty()) {
            sb.append("s0.*");
            columns.add("*");
            return;
        }

        for (int i = 0; i < selectItems.size(); i++) {
            if (i > 0) sb.append(", ");
            SelectItem si = toSelectItem(selectItems.get(i));
            if (si == null) {
                // String shorthand — treat as field name on subject
                String fieldName = selectItems.get(i).toString();
                EntityInfo info = entityLookup.get(findSubjectKey(entityAliasMap));
                String col = resolveColumn(fieldName, info);
                sb.append("s0.").append(quoteId(col));
                if (useAlias) {
                    sb.append(" AS ").append(quoteId(fieldName));
                }
                columns.add(fieldName);
                continue;
            }

            // Relation select with nested fields (may include sub-relations)
            appendRelationSelect(si.getRelation(), si.getNestedFields(),
                    pathResults, entityAliasMap, entityLookup, sb, columns);
        }
    }

    private void appendRelationSelect(String prefix, List<Object> nestedFields,
                                       Map<String, PathResult> pathResults,
                                       Map<String, String> entityAliasMap,
                                       Map<String, EntityInfo> entityLookup,
                                       SqlBuilder sb, List<String> columns) {
        boolean[] firstRef = new boolean[]{true};
        appendRelationFields(prefix, nestedFields, pathResults, entityAliasMap, entityLookup, sb, columns, firstRef);
    }

    @SuppressWarnings("unchecked")
    private void appendRelationFields(String prefix, List<Object> fields,
                                       Map<String, PathResult> pathResults,
                                       Map<String, String> entityAliasMap,
                                       Map<String, EntityInfo> entityLookup,
                                       SqlBuilder sb, List<String> columns,
                                       boolean[] firstRef) {
        if (fields == null) return;
        for (Object item : fields) {
            if (item instanceof String) {
                String fieldName = (String) item;
                PathResult pr = pathResults.get(prefix);
                String alias = pr != null ? pr.tableAlias : "s0";
                EntityInfo info = pr != null ? entityLookup.get(pr.entityName)
                        : entityLookup.get(findSubjectKey(entityAliasMap));
                String col = resolveColumn(fieldName, info);
                String displayName = prefix + "." + fieldName;
                if (!firstRef[0]) sb.append(", ");
                sb.append(alias).append(".").append(quoteId(col));
                sb.append(" AS ").append(quoteId(displayName));
                columns.add(displayName);
                firstRef[0] = false;
            } else if (item instanceof Map) {
                SelectItem subSi = objectMapper.convertValue(item, SelectItem.class);
                if (subSi.getRelation() != null) {
                    String subPrefix = prefix + "." + subSi.getRelation();
                    appendRelationFields(subPrefix, subSi.getNestedFields(),
                            pathResults, entityAliasMap, entityLookup, sb, columns, firstRef);
                }
            }
        }
    }

    private SelectItem toSelectItem(Object item) {
        if (item instanceof String) return null;
        if (item instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) item;
            return objectMapper.convertValue(map, SelectItem.class);
        }
        return null;
    }

    private List<SelectItem> collectRelationSelects(List<Object> selectItems) {
        if (selectItems == null) return Collections.emptyList();
        List<SelectItem> result = new ArrayList<>();
        for (Object item : selectItems) {
            SelectItem si = toSelectItem(item);
            if (si != null && si.getRelation() != null && !si.getRelation().isBlank()) {
                result.add(si);
            }
        }
        return result;
    }

    // ── Filters ──

    private void buildFilterGroup(FilterGroup group,
                                   Map<String, PathResult> pathResults,
                                   Map<String, String> entityAliasMap,
                                   Map<String, EntityInfo> entityLookup,
                                   SqlBuilder sb) {
        if (group == null || group.getConditions() == null) return;
        List<String> parts = new ArrayList<>();

        for (Object item : group.getConditions()) {
            if (!(item instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) item;

            if (map.containsKey("logic")) {
                // Nested group
                FilterGroup sub = objectMapper.convertValue(map, FilterGroup.class);
                if (!hasFilterConditions(sub)) continue;
                SqlBuilder subSb = new SqlBuilder();
                subSb.append("(");
                buildFilterGroup(sub, pathResults, entityAliasMap, entityLookup, subSb);
                subSb.append(")");
                parts.add(subSb.toString());
            } else {
                // Condition
                FilterCondition c = objectMapper.convertValue(map, FilterCondition.class);
                String alias = resolveConditionAlias(c, pathResults);
                EntityInfo info = resolveConditionEntity(c, findSubjectKey(entityAliasMap), pathResults, entityLookup);
                parts.add(buildCondition(c, alias, info));
            }
        }

        if (parts.isEmpty()) return;
        String logic = "AND".equalsIgnoreCase(group.getLogic()) ? " AND " : " OR ";
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(logic);
            sb.append(parts.get(i));
        }
    }

    private boolean hasFilterConditions(FilterGroup group) {
        if (group == null || group.getConditions() == null) return false;
        for (Object item : group.getConditions()) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                if (map.containsKey("logic")) {
                    FilterGroup sub = objectMapper.convertValue(map, FilterGroup.class);
                    if (hasFilterConditions(sub)) return true;
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    private String buildCondition(FilterCondition c, String alias, EntityInfo info) {
        if (c.getField() == null) throw new BadRequestException("Filter field is required");
        String col = resolveColumn(c.getField(), null, null, info);
        String qCol = alias + "." + quoteId(col);
        String op = c.getOperator();
        if (op == null) throw new BadRequestException("Filter operator is required");

        return switch (op.toUpperCase()) {
            case "EQ" -> qCol + " = " + formatValue(c.getValue());
            case "NEQ" -> qCol + " != " + formatValue(c.getValue());
            case "GT" -> qCol + " > " + formatValue(c.getValue());
            case "GTE" -> qCol + " >= " + formatValue(c.getValue());
            case "LT" -> qCol + " < " + formatValue(c.getValue());
            case "LTE" -> qCol + " <= " + formatValue(c.getValue());
            case "IN" -> qCol + " IN (" + formatInValues(c.getValue()) + ")";
            case "NOT_IN" -> qCol + " NOT IN (" + formatInValues(c.getValue()) + ")";
            case "BETWEEN" -> qCol + " BETWEEN " + formatBetweenValues(c.getValue());
            case "LIKE" -> qCol + " LIKE " + quoteString(c.getValue() != null ? c.getValue().toString() : "");
            case "CONTAINS" -> qCol + " LIKE " + quoteString("%" + c.getValue() + "%");
            case "STARTS_WITH" -> qCol + " LIKE " + quoteString(c.getValue() + "%");
            case "ENDS_WITH" -> qCol + " LIKE " + quoteString("%" + c.getValue());
            case "IS_NULL" -> qCol + " IS NULL";
            case "IS_NOT_NULL" -> qCol + " IS NOT NULL";
            default -> throw new BadRequestException("Unknown operator: " + op);
        };
    }

    // ── SQL component builders ──

    private String buildFromJoinSql(EntityInfo subjectInfo, Map<String, PathResult> pathResults) {
        StringBuilder sb = new StringBuilder();
        sb.append(" FROM ").append(quoteId(subjectInfo.tableName)).append(" s0");
        for (PathResult pr : pathResults.values()) {
            if (pr.joinSql != null && !pr.joinSql.isEmpty()) {
                sb.append(pr.joinSql);
            }
        }
        return sb.toString();
    }

    private String buildFilterFromJoinSql(EntityInfo subjectInfo, Map<String, PathResult> filterPathResults) {
        StringBuilder sb = new StringBuilder();
        sb.append(" FROM ").append(quoteId(subjectInfo.tableName)).append(" s0");
        for (PathResult pr : filterPathResults.values()) {
            if (pr.joinSql != null && !pr.joinSql.isEmpty()) {
                sb.append(pr.joinSql);
            }
        }
        return sb.toString();
    }

    private String buildWhereSql(FilterGroup filter, Map<String, PathResult> pathResults,
                                  Map<String, String> entityAliasMap,
                                  Map<String, EntityInfo> entityLookup) {
        if (filter == null || !hasFilterConditions(filter)) return null;
        SqlBuilder sb = new SqlBuilder();
        buildFilterGroup(filter, pathResults, entityAliasMap, entityLookup, sb);
        return sb.toString();
    }

    // ── ID helpers ──

    private String findIdColumn(EntityInfo info) {
        if (info == null) return "id";
        // Use the primary key column recorded during table import
        if (info.primaryKeyColumn != null) return info.primaryKeyColumn;
        // Fallback: look for property "id"
        for (Map.Entry<String, String> e : info.propToCol.entrySet()) {
            if ("id".equalsIgnoreCase(e.getKey())) {
                return e.getValue();
            }
        }
        // Fallback: look for column "id"
        for (String col : info.colToProp.keySet()) {
            if ("id".equalsIgnoreCase(col)) return col;
        }
        return "id";
    }

    private String findIdProperty(EntityInfo info) {
        if (info == null) return "id";
        // Use the primary key column and map it back to property
        if (info.primaryKeyColumn != null) {
            String prop = info.colToProp.get(info.primaryKeyColumn);
            if (prop != null) return prop;
        }
        // Fallback: look for property "id"
        for (Map.Entry<String, String> e : info.propToCol.entrySet()) {
            if ("id".equalsIgnoreCase(e.getKey())) {
                return e.getKey();
            }
        }
        return "id";
    }

    // ── Alias / entity helpers ──

    private String resolveConditionAlias(FilterCondition c, Map<String, PathResult> pathResults) {
        if (c.getPath() != null && !c.getPath().isEmpty()) {
            String key = String.join(".", c.getPath());
            PathResult pr = pathResults.get(key);
            if (pr != null) return pr.tableAlias;
        }
        return "s0";
    }

    private EntityInfo resolveConditionEntity(FilterCondition c, String subjectEntity,
                                               Map<String, PathResult> pathResults,
                                               Map<String, EntityInfo> entityLookup) {
        if (c.getPath() != null && !c.getPath().isEmpty()) {
            String key = String.join(".", c.getPath());
            PathResult pr = pathResults.get(key);
            if (pr != null) {
                EntityInfo info = entityLookup.get(pr.entityName);
                if (info != null) return info;
            }
        }
        return entityLookup.get(subjectEntity);
    }

    private String resolveColumn(String property, EntityInfo info) {
        return resolveColumn(property, null, null, info);
    }

    /**
     * Resolve a junction table column name to the corresponding entity column.
     * Junction columns (junctionDomainColumn / junctionRangeColumn) may store
     * either a property name (e.g. "id") or a raw column name (e.g. "category_id").
     * This method tries both, falling back to the entity's identity column.
     */
    private String resolveJunctionColumn(String junctionCol, EntityInfo info) {
        if (junctionCol == null) return findIdColumn(info);
        // Try as property name first
        String col = info.propToCol.get(junctionCol);
        if (col != null) return col;
        // Try case-insensitive property match
        for (Map.Entry<String, String> e : info.propToCol.entrySet()) {
            if (e.getKey().equalsIgnoreCase(junctionCol)) return e.getValue();
        }
        // Try as column name (in case it's stored as raw column name in the junction table)
        String prop = info.colToProp.get(junctionCol);
        if (prop != null) return junctionCol; // column exists, use as-is
        // Fall back to entity's identity column (junction references PK)
        return findIdColumn(info);
    }

    private String resolveColumn(String property, List<String> path,
                                  Map<String, PathResult> pathResults, EntityInfo info) {
        if (property == null) throw new BadRequestException("Property name is required");
        if (info == null) throw new BadRequestException("Entity info not found for property: " + property);
        String col = info.propToCol.get(property);
        if (col == null) {
            for (Map.Entry<String, String> entry : info.propToCol.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(property)) return entry.getValue();
            }
            throw new BadRequestException("Property '" + property + "' not found in entity '"
                    + info.className + "'. Available: " + info.propToCol.keySet());
        }
        return col;
    }

    private String findSubjectKey(Map<String, String> entityAliasMap) {
        for (Map.Entry<String, String> e : entityAliasMap.entrySet()) {
            if ("s0".equals(e.getValue())) return e.getKey();
        }
        return entityAliasMap.keySet().iterator().next();
    }

    // ── Value formatting ──

    private String formatValue(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) {
            return (Boolean) value ? "1" : "0";
        }
        return quoteString(value.toString());
    }

    private String formatInValues(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Collection<?> col) {
            return col.stream().map(this::formatValue).collect(Collectors.joining(", "));
        }
        if (value instanceof Object[] arr) {
            return Arrays.stream(arr).map(this::formatValue).collect(Collectors.joining(", "));
        }
        return formatValue(value);
    }

    private String formatBetweenValues(Object value) {
        if (value == null) throw new BadRequestException("BETWEEN requires a value");
        if (value instanceof Collection<?> col) {
            Iterator<?> it = col.iterator();
            return formatValue(it.next()) + " AND " + formatValue(it.next());
        }
        if (value instanceof Object[] arr && arr.length >= 2) {
            return formatValue(arr[0]) + " AND " + formatValue(arr[1]);
        }
        throw new BadRequestException("BETWEEN requires an array of two values");
    }

    // ── SQL execution ──

    private long executeCountQuery(EntityInfo subjectInfo, String countSql) {
        DbConnectionEntity conn = getDbConnection(subjectInfo.dbConnectionId);
        String url = buildMySqlUrl(conn);
        try (Connection sqlConn = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword());
             PreparedStatement ps = sqlConn.prepareStatement(countSql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new BadRequestException("Count query failed: " + e.getMessage() + "\nSQL: " + countSql);
        }
        return 0;
    }

    private List<Map<String, Object>> executeQuery(EntityInfo subjectInfo, String sql) {
        DbConnectionEntity conn = getDbConnection(subjectInfo.dbConnectionId);
        String url = buildMySqlUrl(conn);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection sqlConn = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword());
             PreparedStatement ps = sqlConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            throw new BadRequestException("Query execution failed: " + e.getMessage() + "\nSQL: " + sql);
        }
        return rows;
    }

    private List<String> getResultColumnNames(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(rows.get(0).keySet());
    }

    // ── Lookup builders ──

    private Map<String, EntityInfo> buildEntityLookup(String ontologyId) {
        List<TableImportEntity> imports = tableImportRepository.findByOntologyIdAndStatus(ontologyId, "applied");
        if (imports.isEmpty()) {
            throw new BadRequestException("No applied table imports found for ontology: " + ontologyId);
        }
        Map<String, EntityInfo> lookup = new LinkedHashMap<>();
        for (TableImportEntity ti : imports) {
            MappingConfig config;
            try {
                config = objectMapper.readValue(ti.getMappingJson(), MappingConfig.class);
            } catch (Exception e) {
                throw new BadRequestException("Failed to parse mapping JSON for import " + ti.getId());
            }
            if (config.getTables() == null) continue;
            for (TableMapping tbl : config.getTables()) {
                EntityInfo info = new EntityInfo();
                info.className = tbl.getClassName();
                info.tableName = tbl.getTableName();
                info.dbConnectionId = ti.getDbConnectionId();
                info.propToCol = new LinkedHashMap<>();
                info.colToProp = new LinkedHashMap<>();
                if (tbl.getColumns() != null) {
                    for (ColumnMapping col : tbl.getColumns()) {
                        info.propToCol.put(col.getPropertyName(), col.getColumnName());
                        info.colToProp.put(col.getColumnName(), col.getPropertyName());
                        if (col.isPrimaryKey()) {
                            info.primaryKeyColumn = col.getColumnName();
                        }
                    }
                }
                lookup.put(tbl.getClassName(), info);
            }
        }
        return lookup;
    }

    private Map<String, RelationInfo> buildRelationLookup(String ontologyId) {
        List<PropertyEntity> properties = propertyRepository.findByOntologyId(ontologyId);
        Map<String, RelationInfo> lookup = new LinkedHashMap<>();
        for (PropertyEntity prop : properties) {
            if (prop.getPropertyType() != PropertyType.OBJECT) continue;
            RelationInfo info = new RelationInfo();
            info.name = prop.getName();
            info.sourceClass = classRepository.findById(prop.getDomainClassId())
                    .map(ClassEntity::getName).orElse(null);
            info.targetClass = prop.getRange();
            info.relationType = prop.getRelationType() != null ? prop.getRelationType() : RelationType.ONE_TO_MANY;
            if (prop.getMappingRules() != null && !prop.getMappingRules().isBlank()) {
                try {
                    info.mappingRules = objectMapper.readValue(prop.getMappingRules(),
                            new TypeReference<List<Map<String, String>>>() {});
                } catch (Exception e) {
                    info.mappingRules = Collections.emptyList();
                }
            } else {
                info.mappingRules = Collections.emptyList();
            }
            info.junctionTableName = prop.getJunctionTableName();
            info.junctionDomainColumn = prop.getJunctionDomainColumn();
            info.junctionRangeColumn = prop.getJunctionRangeColumn();
            lookup.put(prop.getName(), info);
        }
        return lookup;
    }

    // ── DB connection helpers ──

    private DbConnectionEntity getDbConnection(String dbConnectionId) {
        return dbConnectionRepository.findById(dbConnectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Database connection", dbConnectionId));
    }

    private String buildMySqlUrl(DbConnectionEntity conn) {
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                conn.getHost(), conn.getPort(), conn.getDatabaseName());
    }

    // ── SQL helpers ──

    private static String quoteId(String identifier) {
        return "`" + identifier + "`";
    }

    private static String quoteString(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    private static class SqlBuilder {
        private final StringBuilder sb = new StringBuilder();
        SqlBuilder append(String s) { sb.append(s); return this; }
        SqlBuilder append(int i) { sb.append(i); return this; }
        @Override public String toString() { return sb.toString(); }
    }
}
