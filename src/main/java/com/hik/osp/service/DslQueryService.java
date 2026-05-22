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

    // ── Entity info lookup structures ──

    private static class EntityInfo {
        String className;
        String tableName;
        String dbConnectionId;
        Map<String, String> propToCol;  // property-name → column-name
        Map<String, String> colToProp;  // column-name → property-name
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

    // ── Public entry point ──

    public DslQueryResponse executeQuery(DslQueryRequest request) {
        String ontologyId = request.getOntologyId();
        QueryBody q = request.getQuery();
        if (q == null || q.getSubject() == null) {
            throw new BadRequestException("Query body and subject are required");
        }
        if (q.getSubject().getEntity() == null || q.getSubject().getEntity().isBlank()) {
            throw new BadRequestException("Subject entity is required");
        }

        // Verify ontology exists
        ontologyRepository.findById(ontologyId)
                .orElseThrow(() -> new ResourceNotFoundException("Ontology", ontologyId));

        // Build lookup maps
        Map<String, EntityInfo> entityLookup = buildEntityLookup(ontologyId);
        Map<String, RelationInfo> relationLookup = buildRelationLookup(ontologyId);

        String subjectEntity = q.getSubject().getEntity();
        EntityInfo subjectInfo = entityLookup.get(subjectEntity);
        if (subjectInfo == null) {
            throw new BadRequestException("Subject entity '" + subjectEntity
                    + "' not found in table mappings. Available: " + entityLookup.keySet());
        }

        // Resolve alias mapping: entity name → SQL table alias
        // s0 = subject, t0/t1/... = traversal targets
        Map<String, String> entityAliasMap = new LinkedHashMap<>();
        entityAliasMap.put(subjectEntity, "s0");
        if (q.getSubject().getAlias() != null && !q.getSubject().getAlias().isBlank()) {
            entityAliasMap.put(q.getSubject().getAlias(), "s0");
        }

        // Build SQL
        SqlBuilder sb = new SqlBuilder();
        List<String> columns = new ArrayList<>();

        // Pre-register traversal entity aliases so projection can reference them
        boolean hasAggregation = hasAggregation(q.getProjection());
        if (q.getTraversal() != null) {
            for (int i = 0; i < q.getTraversal().size(); i++) {
                Traversal t = q.getTraversal().get(i);
                entityAliasMap.put(t.getTo(), "t" + i);
            }
        }

        // SELECT clause
        sb.append("SELECT ");
        if (q.getDistinct() != null && q.getDistinct()) {
            sb.append("DISTINCT ");
        }
        buildProjection(q.getProjection(), entityAliasMap, entityLookup, sb, columns);

        // FROM clause
        sb.append(" FROM ").append(quoteId(subjectInfo.tableName)).append(" s0");

        // JOINs from traversals
        if (q.getTraversal() != null && !q.getTraversal().isEmpty()) {
            for (int i = 0; i < q.getTraversal().size(); i++) {
                String targetAlias = "t" + i;
                Traversal t = q.getTraversal().get(i);
                appendJoin(t, targetAlias, entityAliasMap, entityLookup, relationLookup, sb);
            }
        }

        // WHERE clause
        if (q.getFilters() != null && hasConditions(q.getFilters())) {
            sb.append(" WHERE ");
            buildFilterGroup(q.getFilters(), entityAliasMap, entityLookup, sb);
        }

        // GROUP BY (only when aggregation is present)
        if (hasAggregation && q.getProjection() != null) {
            sb.append(" GROUP BY ");
            boolean first = true;
            for (Projection p : q.getProjection()) {
                if (p.getAggregation() == null || p.getAggregation().isBlank()) {
                    if (!first) sb.append(", ");
                    String alias = resolveEntityAlias(p.getEntity(), entityAliasMap);
                    EntityInfo info = resolveEntityInfo(p.getEntity(), entityAliasMap, entityLookup);
                    String col = resolveColumn(p.getProperty(), info);
                    sb.append(alias).append(".").append(quoteId(col));
                    first = false;
                }
            }
        }

        // ORDER BY clause
        if (q.getOrderBy() != null && !q.getOrderBy().isEmpty()) {
            sb.append(" ORDER BY ");
            for (int i = 0; i < q.getOrderBy().size(); i++) {
                if (i > 0) sb.append(", ");
                OrderBy ob = q.getOrderBy().get(i);
                String col;
                String alias = "s0";
                EntityInfo info = subjectInfo;
                // If property contains a dot, parse entity.alias/property
                if (ob.getProperty() != null && ob.getProperty().contains(".")) {
                    String[] parts = ob.getProperty().split("\\.", 2);
                    alias = resolveEntityAlias(parts[0], entityAliasMap);
                    info = resolveEntityInfo(parts[0], entityAliasMap, entityLookup);
                    col = resolveColumn(parts[1], info);
                } else {
                    col = resolveColumn(ob.getProperty(), info);
                }
                sb.append(alias).append(".").append(quoteId(col));
                sb.append(" ").append("ASC".equalsIgnoreCase(ob.getDirection()) ? "ASC" : "DESC");
                if (ob.getNulls() != null) {
                    sb.append(" NULLS ").append("FIRST".equalsIgnoreCase(ob.getNulls()) ? "FIRST" : "LAST");
                }
            }
        }

        // Pagination
        String countSql = null;
        long totalCount = 0;
        Pagination pag = q.getPagination();
        if (pag != null) {
            // First execute count query
            countSql = "SELECT COUNT(*) AS cnt FROM (" + sb.toString() + ") _dsl_count";
            totalCount = executeCountQuery(subjectInfo, countSql);

            int limit = 0, offset = 0;
            if (pag.getLimit() != null && pag.getLimit() > 0) {
                limit = pag.getLimit();
                offset = pag.getOffset() != null ? pag.getOffset() : 0;
            } else if (pag.getPageSize() != null && pag.getPageSize() > 0) {
                limit = pag.getPageSize();
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

        // Execute
        String sql = sb.toString();
        List<Map<String, Object>> rows = executeQuery(subjectInfo, sql);

        // For non-paginated queries, count the rows we got
        if (pag == null) {
            totalCount = rows.size();
        }

        // Map result column names to property names if possible
        List<Map<String, Object>> mappedRows = mapResultColumns(rows, columns.isEmpty() ? null : columns, subjectInfo, entityLookup, entityAliasMap);

        // Determine output column names
        List<String> outputColumns = columns.isEmpty() ? getResultColumnNames(mappedRows) : columns;

        return DslQueryResponse.builder()
                .columns(outputColumns)
                .rows(mappedRows)
                .total(totalCount)
                .message("Query executed successfully" + (totalCount > 0 ? ", returned " + mappedRows.size() + " rows" : ""))
                .build();
    }

    // ── Projection ──

    private void buildProjection(List<Projection> projections,
                                  Map<String, String> entityAliasMap,
                                  Map<String, EntityInfo> entityLookup,
                                  SqlBuilder sb, List<String> columns) {
        if (projections == null || projections.isEmpty()) {
            sb.append("s0.*");
            columns.add("*");
            return;
        }

        boolean hasAgg = false;
        for (Projection p : projections) {
            if (p.getAggregation() != null && !p.getAggregation().isBlank()) {
                hasAgg = true;
                break;
            }
        }

        for (int i = 0; i < projections.size(); i++) {
            if (i > 0) sb.append(", ");
            Projection p = projections.get(i);
            String alias = resolveEntityAlias(p.getEntity(), entityAliasMap);
            EntityInfo info = resolveEntityInfo(p.getEntity(), entityAliasMap, entityLookup);

            if (p.getExpression() != null && !p.getExpression().isBlank()) {
                // Raw expression
                sb.append(p.getExpression());
                boolean exprHasAlias = p.getAlias() != null && !p.getAlias().isBlank();
                String colName = exprHasAlias ? p.getAlias() : p.getExpression();
                columns.add(colName);
                if (exprHasAlias) {
                    sb.append(" AS ").append(quoteId(p.getAlias()));
                }
                continue;
            }

            String col = resolveColumn(p.getProperty(), info);
            boolean hasAlias = p.getAlias() != null && !p.getAlias().isBlank();
            String displayName = hasAlias ? p.getAlias() : p.getProperty();

            if (p.getAggregation() != null && !p.getAggregation().isBlank()) {
                String aggSql = buildAggregation(p.getAggregation(), alias, col);
                sb.append(aggSql);
                columns.add(displayName);
                if (hasAlias) {
                    sb.append(" AS ").append(quoteId(p.getAlias()));
                }
            } else {
                sb.append(alias).append(".").append(quoteId(col));
                columns.add(displayName);
                if (hasAlias) {
                    sb.append(" AS ").append(quoteId(p.getAlias()));
                }
            }
        }
    }

    private String buildAggregation(String aggregation, String alias, String column) {
        String qCol = alias + "." + quoteId(column);
        return switch (aggregation.toUpperCase()) {
            case "COUNT" -> "COUNT(" + qCol + ")";
            case "SUM" -> "SUM(" + qCol + ")";
            case "AVG" -> "AVG(" + qCol + ")";
            case "MIN" -> "MIN(" + qCol + ")";
            case "MAX" -> "MAX(" + qCol + ")";
            case "COUNT_DISTINCT" -> "COUNT(DISTINCT " + qCol + ")";
            default -> throw new BadRequestException("Unknown aggregation: " + aggregation);
        };
    }

    private boolean hasAggregation(List<Projection> projections) {
        if (projections == null) return false;
        return projections.stream().anyMatch(p -> p.getAggregation() != null && !p.getAggregation().isBlank());
    }

    // ── Filters ──

    private void buildFilterGroup(FilterGroup group,
                                   Map<String, String> entityAliasMap,
                                   Map<String, EntityInfo> entityLookup,
                                   SqlBuilder sb) {
        if (group == null) return;

        List<String> parts = new ArrayList<>();

        // Process conditions
        if (group.getConditions() != null) {
            for (FilterCondition c : group.getConditions()) {
                String alias = resolveEntityAlias(c.getEntity(), entityAliasMap);
                EntityInfo info = resolveEntityInfo(c.getEntity(), entityAliasMap, entityLookup);
                parts.add(buildCondition(c, alias, info));
            }
        }

        // Process nested groups
        if (group.getGroups() != null) {
            for (FilterGroup g : group.getGroups()) {
                if (!hasConditions(g)) continue;
                SqlBuilder sub = new SqlBuilder();
                sub.append("(");
                buildFilterGroup(g, entityAliasMap, entityLookup, sub);
                sub.append(")");
                parts.add(sub.toString());
            }
        }

        if (parts.isEmpty()) return;

        String logic = "AND".equalsIgnoreCase(group.getLogic()) ? " AND " : " OR ";
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(logic);
            sb.append(parts.get(i));
        }
    }

    private boolean hasConditions(FilterGroup group) {
        if (group == null) return false;
        if (group.getConditions() != null && !group.getConditions().isEmpty()) return true;
        if (group.getGroups() != null) {
            for (FilterGroup g : group.getGroups()) {
                if (hasConditions(g)) return true;
            }
        }
        return false;
    }

    private String buildCondition(FilterCondition c, String alias, EntityInfo info) {
        String col = resolveColumn(c.getProperty(), info);
        String qCol = alias + "." + quoteId(col);
        String op = c.getOperator();

        if (op == null) throw new BadRequestException("Filter operator is required");

        return switch (op.toUpperCase()) {
            case "EQ" -> qCol + " = " + formatValue(c.getValue(), c.getValueType());
            case "NEQ" -> qCol + " != " + formatValue(c.getValue(), c.getValueType());
            case "GT" -> qCol + " > " + formatValue(c.getValue(), c.getValueType());
            case "GTE" -> qCol + " >= " + formatValue(c.getValue(), c.getValueType());
            case "LT" -> qCol + " < " + formatValue(c.getValue(), c.getValueType());
            case "LTE" -> qCol + " <= " + formatValue(c.getValue(), c.getValueType());
            case "IN" -> qCol + " IN (" + formatInValues(c.getValue()) + ")";
            case "NOT_IN" -> qCol + " NOT IN (" + formatInValues(c.getValue()) + ")";
            case "BETWEEN" -> qCol + " BETWEEN " + formatBetweenValues(c.getValue(), c.getValueType());
            case "CONTAINS" -> qCol + " LIKE " + quoteString("%" + c.getValue() + "%");
            case "STARTS_WITH" -> qCol + " LIKE " + quoteString(c.getValue() + "%");
            case "ENDS_WITH" -> qCol + " LIKE " + quoteString("%" + c.getValue());
            case "IS_NULL" -> qCol + " IS NULL";
            case "IS_NOT_NULL" -> qCol + " IS NOT NULL";
            default -> throw new BadRequestException("Unknown operator: " + op);
        };
    }

    private String formatValue(Object value, String valueType) {
        if (value == null) return "NULL";
        if ("NUMBER".equalsIgnoreCase(valueType)) {
            return value.toString();
        }
        if ("BOOLEAN".equalsIgnoreCase(valueType)) {
            return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(value.toString()) ? "1" : "0";
        }
        if ("DATE".equalsIgnoreCase(valueType)) {
            return "'" + value + "'";
        }
        // STRING or default
        return quoteString(value.toString());
    }

    private String formatInValues(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Collection<?> col) {
            return col.stream().map(v -> formatValue(v, "STRING")).collect(Collectors.joining(", "));
        }
        if (value instanceof Object[] arr) {
            return Arrays.stream(arr).map(v -> formatValue(v, "STRING")).collect(Collectors.joining(", "));
        }
        return formatValue(value, "STRING");
    }

    private String formatBetweenValues(Object value, String valueType) {
        if (value == null) throw new BadRequestException("BETWEEN requires a value");
        if (value instanceof Collection<?> col) {
            Iterator<?> it = col.iterator();
            return formatValue(it.next(), valueType) + " AND " + formatValue(it.next(), valueType);
        }
        if (value instanceof Object[] arr && arr.length >= 2) {
            return formatValue(arr[0], valueType) + " AND " + formatValue(arr[1], valueType);
        }
        throw new BadRequestException("BETWEEN requires an array of two values");
    }

    // ── Traversal / JOIN ──

    private void appendJoin(Traversal t, String targetAlias,
                             Map<String, String> entityAliasMap,
                             Map<String, EntityInfo> entityLookup,
                             Map<String, RelationInfo> relationLookup,
                             SqlBuilder sb) {
        if (t.getRelation() == null) {
            throw new BadRequestException("Traversal relation is required");
        }

        RelationInfo rel = relationLookup.get(t.getRelation());
        if (rel == null) {
            throw new BadRequestException("Relation '" + t.getRelation() + "' not found");
        }

        String fromClass = t.getFrom() != null ? t.getFrom() : rel.sourceClass;
        String fromAlias = entityAliasMap.get(fromClass);
        if (fromAlias == null) {
            throw new BadRequestException("Cannot resolve traversal 'from': '" + fromClass
                    + "'. Available: " + entityAliasMap.keySet());
        }

        EntityInfo fromInfo = entityLookup.get(fromClass);
        EntityInfo targetInfo = entityLookup.get(t.getTo());
        if (fromInfo == null) {
            throw new BadRequestException("From entity '" + fromClass + "' not found in table mappings");
        }
        if (targetInfo == null) {
            throw new BadRequestException("To entity '" + t.getTo() + "' not found in table mappings");
        }

        // Check cross-connection
        if (!fromInfo.dbConnectionId.equals(targetInfo.dbConnectionId)) {
            throw new BadRequestException("Cross-connection traversal not supported: '"
                    + fromClass + "' and '" + t.getTo() + "' are in different databases");
        }

        String joinType = t.isOptional() ? " LEFT JOIN " : " INNER JOIN ";
        boolean isIn = "IN".equalsIgnoreCase(t.getDirection());

        // Check for many-to-many
        boolean isManyToMany = rel.relationType == RelationType.MANY_TO_MANY
                && rel.junctionTableName != null && !rel.junctionTableName.isBlank();

        if (isManyToMany) {
            // Two JOINs: source → junction → target
            String junctionAlias = "j" + targetAlias.substring(1); // j0, j1, etc.
            String jAlias = "j" + targetAlias.charAt(targetAlias.length() - 1);

            sb.append(joinType).append(quoteId(rel.junctionTableName)).append(" ").append(jAlias);
            sb.append(" ON ");

            if (isIn) {
                sb.append(fromAlias).append(".").append(quoteId(resolveColumn(rel.junctionRangeColumn, targetInfo)))
                        .append(" = ").append(jAlias).append(".").append(quoteId(rel.junctionRangeColumn));
            } else {
                sb.append(fromAlias).append(".").append(quoteId(resolveColumn(rel.junctionDomainColumn, fromInfo)))
                        .append(" = ").append(jAlias).append(".").append(quoteId(rel.junctionDomainColumn));
            }

            // Append traversal filters to ON clause
            if (t.getFilters() != null && hasConditions(t.getFilters())) {
                sb.append(" AND ");
                buildFilterGroup(t.getFilters(), entityAliasMap, entityLookup, sb);
            }

            // Second JOIN: junction → target
            sb.append(joinType).append(quoteId(targetInfo.tableName)).append(" ").append(targetAlias);
            sb.append(" ON ").append(jAlias).append(".").append(quoteId(rel.junctionRangeColumn))
                    .append(" = ").append(targetAlias).append(".").append(quoteId(resolveColumn(rel.junctionRangeColumn, targetInfo)));
        } else {
            // Simple direct JOIN
            if (rel.mappingRules == null || rel.mappingRules.isEmpty()) {
                throw new BadRequestException("Relation '" + t.getRelation() + "' has no mapping rules for JOIN");
            }

            sb.append(joinType).append(quoteId(targetInfo.tableName)).append(" ").append(targetAlias);
            sb.append(" ON ");

            for (int i = 0; i < rel.mappingRules.size(); i++) {
                if (i > 0) sb.append(" AND ");
                Map<String, String> rule = rel.mappingRules.get(i);
                String domainProp = rule.get("domain_property");
                String rangeProp = rule.get("range_property");

                if (isIn) {
                    // Reverse: from's range = target's domain
                    sb.append(fromAlias).append(".").append(quoteId(resolveColumn(rangeProp, fromInfo)))
                            .append(" = ").append(targetAlias).append(".").append(quoteId(resolveColumn(domainProp, targetInfo)));
                } else {
                    // Forward: from's domain = target's range
                    sb.append(fromAlias).append(".").append(quoteId(resolveColumn(domainProp, fromInfo)))
                            .append(" = ").append(targetAlias).append(".").append(quoteId(resolveColumn(rangeProp, targetInfo)));
                }
            }

            // Append traversal filters
            if (t.getFilters() != null && hasConditions(t.getFilters())) {
                sb.append(" AND ");
                buildFilterGroup(t.getFilters(), entityAliasMap, entityLookup, sb);
            }
        }
    }

    // ── SQL execution ──

    private long executeCountQuery(EntityInfo subjectInfo, String countSql) {
        DbConnectionEntity conn = getDbConnection(subjectInfo.dbConnectionId);
        String url = buildMySqlUrl(conn);

        try (Connection sqlConn = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword())) {
            try (PreparedStatement ps = sqlConn.prepareStatement(countSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new BadRequestException("Count query failed: " + e.getMessage() + "\nSQL: " + countSql);
        }
        return 0;
    }

    private List<Map<String, Object>> executeQuery(EntityInfo subjectInfo, String sql) {
        DbConnectionEntity conn = getDbConnection(subjectInfo.dbConnectionId);
        String url = buildMySqlUrl(conn);
        List<Map<String, Object>> rows = new ArrayList<>();

        try (Connection sqlConn = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword())) {
            try (PreparedStatement ps = sqlConn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        String colName = meta.getColumnLabel(i);
                        Object val = rs.getObject(i);
                        row.put(colName, val);
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            throw new BadRequestException("Query execution failed: " + e.getMessage() + "\nSQL: " + sql);
        }

        return rows;
    }

    // ── Result mapping ──

    private List<Map<String, Object>> mapResultColumns(List<Map<String, Object>> rows,
                                                         List<String> explicitColumns,
                                                         EntityInfo subjectInfo,
                                                         Map<String, EntityInfo> entityLookup,
                                                         Map<String, String> entityAliasMap) {
        if (rows.isEmpty() || explicitColumns != null) return rows;
        // If no explicit projection, return raw column names from DB
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

            // Parse mapping_rules
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

            // Many-to-many junction info
            info.junctionTableName = prop.getJunctionTableName();
            info.junctionDomainColumn = prop.getJunctionDomainColumn();
            info.junctionRangeColumn = prop.getJunctionRangeColumn();

            lookup.put(prop.getName(), info);
        }

        return lookup;
    }

    // ── Column resolution helpers ──

    private String resolveColumn(String property, EntityInfo info) {
        if (property == null) throw new BadRequestException("Property name is required");
        String col = info.propToCol.get(property);
        if (col == null) {
            // Try case-insensitive match
            for (Map.Entry<String, String> entry : info.propToCol.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(property)) {
                    return entry.getValue();
                }
            }
            throw new BadRequestException("Property '" + property + "' not found in entity '"
                    + info.className + "'. Available: " + info.propToCol.keySet());
        }
        return col;
    }

    private String resolveEntityAlias(String entity, Map<String, String> entityAliasMap) {
        if (entity == null || entity.isBlank() || "subject".equalsIgnoreCase(entity)) {
            return "s0";
        }
        String alias = entityAliasMap.get(entity);
        if (alias == null) {
            // Try case-insensitive
            for (Map.Entry<String, String> e : entityAliasMap.entrySet()) {
                if (e.getKey().equalsIgnoreCase(entity)) return e.getValue();
            }
            return "s0"; // default to subject
        }
        return alias;
    }

    private EntityInfo resolveEntityInfo(String entity, Map<String, String> entityAliasMap,
                                          Map<String, EntityInfo> entityLookup) {
        if (entity == null || entity.isBlank() || "subject".equalsIgnoreCase(entity)) {
            // Find the subject entity
            for (Map.Entry<String, String> e : entityAliasMap.entrySet()) {
                if ("s0".equals(e.getValue())) {
                    EntityInfo info = entityLookup.get(e.getKey());
                    if (info != null) return info;
                }
            }
        }
        EntityInfo info = entityLookup.get(entity);
        if (info != null) return info;
        // Try resolving by entity name in alias map
        String alias = entityAliasMap.get(entity);
        if (alias != null && !"s0".equals(alias)) {
            // Find entity with this alias - reverse lookup
            for (Map.Entry<String, String> e : entityAliasMap.entrySet()) {
                if (alias.equals(e.getValue())) {
                    info = entityLookup.get(e.getKey());
                    if (info != null) return info;
                }
            }
        }
        // Fallback to first entity in alias map (subject)
        for (Map.Entry<String, String> e : entityAliasMap.entrySet()) {
            info = entityLookup.get(e.getKey());
            if (info != null) return info;
        }
        throw new BadRequestException("Cannot resolve entity info for: " + entity);
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

    /**
     * StringBuilder wrapper for fluent SQL building.
     */
    private static class SqlBuilder {
        private final StringBuilder sb = new StringBuilder();

        SqlBuilder append(String s) {
            sb.append(s);
            return this;
        }

        SqlBuilder append(int i) {
            sb.append(i);
            return this;
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
