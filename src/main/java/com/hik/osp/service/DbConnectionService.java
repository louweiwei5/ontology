package com.hik.osp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hik.osp.dto.*;
import com.hik.osp.dto.request.DbConnectionCreateRequest;
import com.hik.osp.dto.request.DbConnectionUpdateRequest;
import com.hik.osp.dto.request.TableImportCreateRequest;
import com.hik.osp.dto.response.*;
import com.hik.osp.entity.*;
import com.hik.osp.exception.BadRequestException;
import com.hik.osp.exception.ResourceNotFoundException;
import com.hik.osp.repository.*;
import com.hik.osp.util.IriUtils;
import com.hik.osp.util.NamingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DbConnectionService {

    private final DbConnectionRepository dbConnectionRepository;
    private final OntologyRepository ontologyRepository;
    private final ClassRepository classRepository;
    private final PropertyRepository propertyRepository;
    private final TableImportRepository tableImportRepository;
    private final ObjectMapper objectMapper;

    // MySQL type → DataType mapping
    private static final Map<String, String> MYSQL_TYPE_MAP = new LinkedHashMap<>();

    static {
        MYSQL_TYPE_MAP.put("int", "integer");
        MYSQL_TYPE_MAP.put("tinyint", "integer");
        MYSQL_TYPE_MAP.put("smallint", "integer");
        MYSQL_TYPE_MAP.put("mediumint", "integer");
        MYSQL_TYPE_MAP.put("bigint", "integer");
        MYSQL_TYPE_MAP.put("float", "float");
        MYSQL_TYPE_MAP.put("double", "float");
        MYSQL_TYPE_MAP.put("decimal", "float");
        MYSQL_TYPE_MAP.put("numeric", "float");
        MYSQL_TYPE_MAP.put("varchar", "string");
        MYSQL_TYPE_MAP.put("char", "string");
        MYSQL_TYPE_MAP.put("text", "text");
        MYSQL_TYPE_MAP.put("mediumtext", "text");
        MYSQL_TYPE_MAP.put("longtext", "text");
        MYSQL_TYPE_MAP.put("tinytext", "string");
        MYSQL_TYPE_MAP.put("date", "date");
        MYSQL_TYPE_MAP.put("datetime", "datetime");
        MYSQL_TYPE_MAP.put("timestamp", "datetime");
        MYSQL_TYPE_MAP.put("year", "integer");
        MYSQL_TYPE_MAP.put("time", "datetime");
        MYSQL_TYPE_MAP.put("boolean", "boolean");
        MYSQL_TYPE_MAP.put("bit", "boolean");
        MYSQL_TYPE_MAP.put("blob", "string");
        MYSQL_TYPE_MAP.put("mediumblob", "string");
        MYSQL_TYPE_MAP.put("longblob", "string");
        MYSQL_TYPE_MAP.put("tinyblob", "string");
        MYSQL_TYPE_MAP.put("json", "text");
    }

    private static String mysqlTypeToDataType(String mysqlType) {
        String base = mysqlType.toLowerCase().split("\\(")[0].split(" ")[0];
        return MYSQL_TYPE_MAP.getOrDefault(base, "string");
    }

    private String buildMySqlUrl(DbConnectionEntity conn) {
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                conn.getHost(), conn.getPort(), conn.getDatabaseName());
    }

    private DbConnectionEntity getConnOrThrow(String id) {
        return dbConnectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Database connection", id));
    }

    // ── CRUD ──

    public DbConnectionEntity create(DbConnectionCreateRequest request) {
        DbConnectionEntity conn = new DbConnectionEntity();
        conn.setName(request.getName());
        conn.setDescription(request.getDescription());
        conn.setDbType(request.getDbType() != null ? request.getDbType() : "mysql");
        conn.setHost(request.getHost());
        conn.setPort(request.getPort() != null ? request.getPort() : 3306);
        conn.setDatabaseName(request.getDatabaseName());
        conn.setUsername(request.getUsername());
        conn.setPassword(request.getPassword());
        return dbConnectionRepository.save(conn);
    }

    public DbConnectionEntity getById(String id) {
        return getConnOrThrow(id);
    }

    public List<DbConnectionEntity> listAll() {
        return dbConnectionRepository.findAllByOrderByCreatedAtDesc();
    }

    public DbConnectionEntity update(String id, DbConnectionUpdateRequest request) {
        DbConnectionEntity conn = getConnOrThrow(id);
        if (request.getName() != null) conn.setName(request.getName());
        if (request.getDescription() != null) conn.setDescription(request.getDescription());
        if (request.getHost() != null) conn.setHost(request.getHost());
        if (request.getPort() != null) conn.setPort(request.getPort());
        if (request.getDatabaseName() != null) conn.setDatabaseName(request.getDatabaseName());
        if (request.getUsername() != null) conn.setUsername(request.getUsername());
        if (request.getPassword() != null) conn.setPassword(request.getPassword());
        return dbConnectionRepository.save(conn);
    }

    public void delete(String id) {
        DbConnectionEntity conn = getConnOrThrow(id);
        tableImportRepository.deleteByDbConnectionId(id);
        dbConnectionRepository.delete(conn);
    }

    // ── Connection test ──

    public Map<String, Object> testConnection(String id) {
        DbConnectionEntity conn = getConnOrThrow(id);
        String url = buildMySqlUrl(conn);
        try (Connection sqlConn = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword())) {
            try (Statement stmt = sqlConn.createStatement()) {
                stmt.execute("SELECT 1");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Connection successful");
            return result;
        } catch (SQLException e) {
            throw new BadRequestException("Connection failed: " + e.getMessage());
        }
    }

    // ── Table introspection ──

    public List<TableInfo> listTables(String id) {
        DbConnectionEntity conn = getConnOrThrow(id);
        String url = buildMySqlUrl(conn);
        List<TableInfo> tables = new ArrayList<>();

        try (Connection sqlConn = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword())) {
            DatabaseMetaData meta = sqlConn.getMetaData();
            try (ResultSet rs = meta.getTables(conn.getDatabaseName(), null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String comment = rs.getString("REMARKS");
                    tables.add(TableInfo.builder()
                            .tableName(tableName)
                            .tableComment(comment != null ? comment : "")
                            .build());
                }
            }
        } catch (SQLException e) {
            throw new BadRequestException("Failed to list tables: " + e.getMessage());
        }

        tables.sort(Comparator.comparing(TableInfo::getTableName));
        return tables;
    }

    public TableDetail getTableDetail(String id, String tableName) {
        DbConnectionEntity conn = getConnOrThrow(id);
        String url = buildMySqlUrl(conn);

        try (Connection sqlConn = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword())) {
            DatabaseMetaData meta = sqlConn.getMetaData();

            // Primary keys
            Set<String> pkCols = new HashSet<>();
            try (ResultSet pkRs = meta.getPrimaryKeys(conn.getDatabaseName(), null, tableName)) {
                while (pkRs.next()) {
                    pkCols.add(pkRs.getString("COLUMN_NAME"));
                }
            }

            // Columns
            List<ColumnInfo> columns = new ArrayList<>();
            try (ResultSet colRs = meta.getColumns(conn.getDatabaseName(), null, tableName, "%")) {
                while (colRs.next()) {
                    String colName = colRs.getString("COLUMN_NAME");
                    String colType = colRs.getString("TYPE_NAME");
                    int nullable = colRs.getInt("NULLABLE");
                    String comment = colRs.getString("REMARKS");
                    columns.add(ColumnInfo.builder()
                            .columnName(colName)
                            .columnType(colType)
                            .nullable(nullable == DatabaseMetaData.columnNullable)
                            .columnComment(comment != null ? comment : "")
                            .primaryKey(pkCols.contains(colName))
                            .build());
                }
            }

            // Table comment
            String tableComment = "";
            String commentSql = "SELECT TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
            try (PreparedStatement ps = sqlConn.prepareStatement(commentSql)) {
                ps.setString(1, conn.getDatabaseName());
                ps.setString(2, tableName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tableComment = rs.getString("TABLE_COMMENT");
                        if (tableComment == null) tableComment = "";
                    }
                }
            }

            return TableDetail.builder()
                    .tableName(tableName)
                    .tableComment(tableComment)
                    .columns(columns)
                    .build();
        } catch (SQLException e) {
            throw new BadRequestException("Failed to get table detail: " + e.getMessage());
        }
    }

    // ── Import & mapping ──

    @Transactional
    public TableImportEntity createImport(String connId, TableImportCreateRequest request) {
        DbConnectionEntity conn = getConnOrThrow(connId);
        OntologyEntity ontology = ontologyRepository.findById(request.getOntologyId())
                .orElseThrow(() -> new ResourceNotFoundException("Ontology", request.getOntologyId()));

        String url = buildMySqlUrl(conn);
        List<TableMapping> tableMappings = new ArrayList<>();

        try (Connection sqlConn = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword())) {
            DatabaseMetaData meta = sqlConn.getMetaData();

            for (String tblName : request.getTables()) {
                // Primary keys
                Set<String> pkCols = new HashSet<>();
                try (ResultSet pkRs = meta.getPrimaryKeys(conn.getDatabaseName(), null, tblName)) {
                    while (pkRs.next()) {
                        pkCols.add(pkRs.getString("COLUMN_NAME"));
                    }
                }

                // Columns
                List<ColumnMapping> colMappings = new ArrayList<>();
                try (ResultSet colRs = meta.getColumns(conn.getDatabaseName(), null, tblName, "%")) {
                    while (colRs.next()) {
                        String colName = colRs.getString("COLUMN_NAME");
                        String colType = colRs.getString("TYPE_NAME");
                        String comment = colRs.getString("REMARKS");
                        String dt = mysqlTypeToDataType(colType);
                        colMappings.add(ColumnMapping.builder()
                                .columnName(colName)
                                .propertyName(NamingUtils.toCamelCase(colName))
                                .dataType(dt)
                                .columnComment(comment != null ? comment : "")
                                .primaryKey(pkCols.contains(colName))
                                .build());
                    }
                }

                // Table comment
                String tableComment = "";
                String commentSql = "SELECT TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
                try (PreparedStatement ps = sqlConn.prepareStatement(commentSql)) {
                    ps.setString(1, conn.getDatabaseName());
                    ps.setString(2, tblName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            tableComment = rs.getString("TABLE_COMMENT");
                            if (tableComment == null) tableComment = "";
                        }
                    }
                }

                String className = NamingUtils.toPascalCase(tblName);
                tableMappings.add(TableMapping.builder()
                        .tableName(tblName)
                        .className(className)
                        .classId(null)
                        .columns(colMappings)
                        .tableComment(tableComment)
                        .build());
            }
        } catch (SQLException e) {
            throw new BadRequestException("Failed to introspect tables: " + e.getMessage());
        }

        MappingConfig config = MappingConfig.builder().tables(tableMappings).build();
        String mappingJson;
        try {
            mappingJson = objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize mapping config");
        }

        TableImportEntity ti = new TableImportEntity();
        ti.setDbConnectionId(connId);
        ti.setOntologyId(request.getOntologyId());
        ti.setStatus("draft");
        ti.setMappingJson(mappingJson);
        return tableImportRepository.save(ti);
    }

    public TableImportEntity getImport(String importId) {
        return tableImportRepository.findById(importId)
                .orElseThrow(() -> new ResourceNotFoundException("Table import", importId));
    }

    public List<Map<String, Object>> listImports() {
        List<TableImportEntity> items = tableImportRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (TableImportEntity ti : items) {
            String connName = dbConnectionRepository.findById(ti.getDbConnectionId())
                    .map(DbConnectionEntity::getName).orElse("");
            String ontoName = ontologyRepository.findById(ti.getOntologyId())
                    .map(OntologyEntity::getName).orElse("");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ti.getId());
            item.put("db_connection_id", ti.getDbConnectionId());
            item.put("ontology_id", ti.getOntologyId());
            item.put("status", ti.getStatus());
            item.put("connection_name", connName);
            item.put("ontology_name", ontoName);
            item.put("created_at", ti.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    @Transactional
    public TableImportEntity updateMapping(String importId, MappingConfig config) {
        TableImportEntity ti = getImport(importId);
        try {
            ti.setMappingJson(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize mapping config");
        }
        return tableImportRepository.save(ti);
    }

    @Transactional
    public TableImportEntity applyMapping(String importId) {
        TableImportEntity ti = getImport(importId);
        MappingConfig config;
        try {
            config = objectMapper.readValue(ti.getMappingJson(), MappingConfig.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid mapping JSON");
        }

        OntologyEntity ontology = ontologyRepository.findById(ti.getOntologyId())
                .orElseThrow(() -> new ResourceNotFoundException("Ontology", ti.getOntologyId()));

        Map<String, ClassEntity> createdClasses = new HashMap<>();

        for (TableMapping tbl : config.getTables()) {
            // Check if class already exists
            ClassEntity cls = classRepository.findByOntologyIdAndName(ti.getOntologyId(), tbl.getClassName())
                    .orElseGet(() -> {
                        ClassEntity newCls = new ClassEntity();
                        newCls.setOntologyId(ti.getOntologyId());
                        newCls.setName(tbl.getClassName());
                        newCls.setFullIri(IriUtils.buildFullIri(ontology.getNamespace(), tbl.getClassName()));
                        newCls.setDescription(tbl.getTableComment() != null && !tbl.getTableComment().isEmpty()
                                ? tbl.getTableComment() : null);
                        return classRepository.save(newCls);
                    });

            createdClasses.put(tbl.getTableName(), cls);

            // Create properties for each column mapping
            for (ColumnMapping colMap : tbl.getColumns()) {
                boolean exists = propertyRepository
                        .findByOntologyIdAndNameAndDomainClassId(ti.getOntologyId(), colMap.getPropertyName(), cls.getId())
                        .isPresent();
                if (exists) continue;

                PropertyEntity prop = new PropertyEntity();
                prop.setOntologyId(ti.getOntologyId());
                prop.setName(colMap.getPropertyName());
                prop.setFullIri(IriUtils.buildFullIri(ontology.getNamespace(), colMap.getPropertyName()));
                prop.setPropertyType(com.hik.osp.enums.PropertyType.DATA);
                if (colMap.getDataType() != null) {
                    prop.setDataType(com.hik.osp.enums.DataType.fromValue(colMap.getDataType()));
                }
                prop.setDomainClassId(cls.getId());
                prop.setDescription(colMap.getColumnComment() != null && !colMap.getColumnComment().isEmpty()
                        ? colMap.getColumnComment() : null);
                prop.setPrimaryKey(colMap.isPrimaryKey());
                propertyRepository.save(prop);
            }
        }

        ti.setStatus("applied");
        return tableImportRepository.save(ti);
    }
}
