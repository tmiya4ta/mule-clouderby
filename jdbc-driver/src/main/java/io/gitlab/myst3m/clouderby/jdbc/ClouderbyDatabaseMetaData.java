package io.gitlab.myst3m.clouderby.jdbc;

import io.gitlab.myst3m.clouderby.jdbc.http.HttpClient;
import io.gitlab.myst3m.clouderby.jdbc.http.Protocol;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC DatabaseMetaData implementation for clouderbyd.
 * Provides metadata about the database via the clouderbyd protocol.
 */
public class ClouderbyDatabaseMetaData implements DatabaseMetaData {
    private final ClouderbyConnection connection;
    private final HttpClient httpClient;
    private Protocol.MetadataInfoResponse cachedInfo;

    public ClouderbyDatabaseMetaData(ClouderbyConnection connection) {
        this.connection = connection;
        this.httpClient = connection.getHttpClient();
    }

    private Protocol.MetadataInfoResponse getInfo() throws SQLException {
        if (cachedInfo == null) {
            cachedInfo = httpClient.getMetadataInfo();
        }
        return cachedInfo;
    }

    @Override
    public boolean allProceduresAreCallable() throws SQLException {
        return false;
    }

    @Override
    public boolean allTablesAreSelectable() throws SQLException {
        return true;
    }

    @Override
    public String getURL() throws SQLException {
        return "jdbc:clouderby://";
    }

    @Override
    public String getUserName() throws SQLException {
        return "";
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return false;
    }

    @Override
    public boolean nullsAreSortedHigh() throws SQLException {
        return false;
    }

    @Override
    public boolean nullsAreSortedLow() throws SQLException {
        return true;
    }

    @Override
    public boolean nullsAreSortedAtStart() throws SQLException {
        return false;
    }

    @Override
    public boolean nullsAreSortedAtEnd() throws SQLException {
        return false;
    }

    @Override
    public String getDatabaseProductName() throws SQLException {
        return getInfo().productName;
    }

    @Override
    public String getDatabaseProductVersion() throws SQLException {
        return getInfo().productVersion;
    }

    @Override
    public String getDriverName() throws SQLException {
        return "clouderby JDBC Driver";
    }

    @Override
    public String getDriverVersion() throws SQLException {
        return "1.0.0";
    }

    @Override
    public int getDriverMajorVersion() {
        return 1;
    }

    @Override
    public int getDriverMinorVersion() {
        return 0;
    }

    @Override
    public boolean usesLocalFiles() throws SQLException {
        return true;
    }

    @Override
    public boolean usesLocalFilePerTable() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsMixedCaseIdentifiers() throws SQLException {
        return true;
    }

    @Override
    public boolean storesUpperCaseIdentifiers() throws SQLException {
        return false;
    }

    @Override
    public boolean storesLowerCaseIdentifiers() throws SQLException {
        return false;
    }

    @Override
    public boolean storesMixedCaseIdentifiers() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
        return true;
    }

    @Override
    public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
        return false;
    }

    @Override
    public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
        return false;
    }

    @Override
    public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
        return true;
    }

    @Override
    public String getIdentifierQuoteString() throws SQLException {
        String quote = getInfo().identifierQuoteString;
        return quote != null ? quote : "\"";
    }

    @Override
    public String getSQLKeywords() throws SQLException {
        return "";
    }

    @Override
    public String getNumericFunctions() throws SQLException {
        return "abs,max,min,round";
    }

    @Override
    public String getStringFunctions() throws SQLException {
        return "length,lower,upper,substr,trim";
    }

    @Override
    public String getSystemFunctions() throws SQLException {
        return "";
    }

    @Override
    public String getTimeDateFunctions() throws SQLException {
        return "date,time,datetime,julianday,strftime";
    }

    @Override
    public String getSearchStringEscape() throws SQLException {
        return "\\";
    }

    @Override
    public String getExtraNameCharacters() throws SQLException {
        return "";
    }

    @Override
    public boolean supportsAlterTableWithAddColumn() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsAlterTableWithDropColumn() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsColumnAliasing() throws SQLException {
        return true;
    }

    @Override
    public boolean nullPlusNonNullIsNull() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsConvert() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsConvert(int fromType, int toType) throws SQLException {
        return false;
    }

    @Override
    public boolean supportsTableCorrelationNames() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsDifferentTableCorrelationNames() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsExpressionsInOrderBy() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsOrderByUnrelated() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsGroupBy() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsGroupByUnrelated() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsGroupByBeyondSelect() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsLikeEscapeClause() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsMultipleResultSets() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsMultipleTransactions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsNonNullableColumns() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsMinimumSQLGrammar() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsCoreSQLGrammar() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsExtendedSQLGrammar() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsANSI92EntryLevelSQL() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsANSI92IntermediateSQL() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsANSI92FullSQL() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsIntegrityEnhancementFacility() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsOuterJoins() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsFullOuterJoins() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsLimitedOuterJoins() throws SQLException {
        return true;
    }

    @Override
    public String getSchemaTerm() throws SQLException {
        String term = getInfo().schemaTerm;
        return term != null ? term : "schema";
    }

    @Override
    public String getProcedureTerm() throws SQLException {
        return "procedure";
    }

    @Override
    public String getCatalogTerm() throws SQLException {
        String term = getInfo().catalogTerm;
        return term != null ? term : "database";
    }

    @Override
    public boolean isCatalogAtStart() throws SQLException {
        return true;
    }

    @Override
    public String getCatalogSeparator() throws SQLException {
        String sep = getInfo().catalogSeparator;
        return sep != null ? sep : ".";
    }

    @Override
    public boolean supportsSchemasInDataManipulation() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsSchemasInProcedureCalls() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsSchemasInTableDefinitions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsSchemasInIndexDefinitions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInDataManipulation() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInProcedureCalls() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInTableDefinitions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsPositionedDelete() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsPositionedUpdate() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsSelectForUpdate() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsStoredProcedures() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsSubqueriesInComparisons() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSubqueriesInExists() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSubqueriesInIns() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSubqueriesInQuantifieds() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsCorrelatedSubqueries() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsUnion() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsUnionAll() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
        return false;
    }

    @Override
    public int getMaxBinaryLiteralLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxCharLiteralLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnNameLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInGroupBy() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInIndex() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInOrderBy() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInSelect() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInTable() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxConnections() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxCursorNameLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxIndexLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxSchemaNameLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxProcedureNameLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxCatalogNameLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxRowSize() throws SQLException {
        return 0;
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
        return true;
    }

    @Override
    public int getMaxStatementLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxStatements() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxTableNameLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxTablesInSelect() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxUserNameLength() throws SQLException {
        return 0;
    }

    @Override
    public int getDefaultTransactionIsolation() throws SQLException {
        return Connection.TRANSACTION_SERIALIZABLE;
    }

    @Override
    public boolean supportsTransactions() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsTransactionIsolationLevel(int level) throws SQLException {
        return level == Connection.TRANSACTION_SERIALIZABLE;
    }

    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsDataManipulationTransactionsOnly() throws SQLException {
        return false;
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
        return false;
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
        return false;
    }

    @Override
    public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern)
            throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern,
                                          String columnNamePattern) throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types)
            throws SQLException {
        Protocol.MetadataTablesResponse response = httpClient.getMetadataTables(catalog, schemaPattern, tableNamePattern, types);

        // Build JDBC standard tables ResultSet
        List<Protocol.ColumnInfo> columns = new ArrayList<>();
        String[] colNames = {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE", "REMARKS",
                "TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "SELF_REFERENCING_COL_NAME", "REF_GENERATION"};
        for (String name : colNames) {
            Protocol.ColumnInfo col = new Protocol.ColumnInfo();
            col.name = name;
            col.type = "TEXT";
            columns.add(col);
        }

        List<List<Object>> rows = new ArrayList<>();
        if (response.tables != null) {
            for (Protocol.TableInfo table : response.tables) {
                List<Object> row = new ArrayList<>();
                row.add(table.catalog);         // TABLE_CAT
                row.add(table.schema);          // TABLE_SCHEM
                row.add(table.name);            // TABLE_NAME
                row.add(table.type);            // TABLE_TYPE
                row.add(table.remarks);         // REMARKS
                row.add(null);                  // TYPE_CAT
                row.add(null);                  // TYPE_SCHEM
                row.add(null);                  // TYPE_NAME
                row.add(null);                  // SELF_REFERENCING_COL_NAME
                row.add(null);                  // REF_GENERATION
                rows.add(row);
            }
        }

        Protocol.QueryResponse queryResponse = new Protocol.QueryResponse();
        queryResponse.columns = columns;
        queryResponse.rows = rows;
        queryResponse.done = true;
        return new ClouderbyResultSet(null, queryResponse);
    }

    @Override
    public ResultSet getSchemas() throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getCatalogs() throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getTableTypes() throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern,
                                 String columnNamePattern) throws SQLException {
        if (tableNamePattern == null) {
            return createEmptyResultSet();
        }

        Protocol.MetadataColumnsResponse response = httpClient.getMetadataColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern);

        // Build JDBC standard columns ResultSet
        List<Protocol.ColumnInfo> columns = new ArrayList<>();
        String[] colNames = {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "DATA_TYPE",
                "TYPE_NAME", "COLUMN_SIZE", "BUFFER_LENGTH", "DECIMAL_DIGITS", "NUM_PREC_RADIX",
                "NULLABLE", "REMARKS", "COLUMN_DEF", "SQL_DATA_TYPE", "SQL_DATETIME_SUB",
                "CHAR_OCTET_LENGTH", "ORDINAL_POSITION", "IS_NULLABLE", "SCOPE_CATALOG",
                "SCOPE_SCHEMA", "SCOPE_TABLE", "SOURCE_DATA_TYPE", "IS_AUTOINCREMENT", "IS_GENERATEDCOLUMN"};
        for (String name : colNames) {
            Protocol.ColumnInfo col = new Protocol.ColumnInfo();
            col.name = name;
            col.type = "TEXT";
            columns.add(col);
        }

        List<List<Object>> rows = new ArrayList<>();
        if (response.columns != null) {
            for (Protocol.ColumnMetadata colMeta : response.columns) {
                List<Object> row = new ArrayList<>();
                row.add(colMeta.catalog);                   // TABLE_CAT
                row.add(colMeta.schema);                    // TABLE_SCHEM
                row.add(colMeta.tableName);                 // TABLE_NAME
                row.add(colMeta.columnName);                // COLUMN_NAME
                row.add(colMeta.dataType);                  // DATA_TYPE
                row.add(colMeta.typeName);                  // TYPE_NAME
                row.add(colMeta.columnSize);                // COLUMN_SIZE
                row.add(null);                              // BUFFER_LENGTH
                row.add(null);                              // DECIMAL_DIGITS
                row.add(10);                                // NUM_PREC_RADIX
                row.add(colMeta.nullable == 0 ? columnNoNulls : columnNullable); // NULLABLE
                row.add(colMeta.remarks);                   // REMARKS
                row.add(colMeta.defaultValue);              // COLUMN_DEF
                row.add(null);                              // SQL_DATA_TYPE
                row.add(null);                              // SQL_DATETIME_SUB
                row.add(null);                              // CHAR_OCTET_LENGTH
                row.add(colMeta.ordinalPosition);           // ORDINAL_POSITION (1-based)
                row.add(colMeta.nullable == 0 ? "NO" : "YES"); // IS_NULLABLE
                row.add(null);                              // SCOPE_CATALOG
                row.add(null);                              // SCOPE_SCHEMA
                row.add(null);                              // SCOPE_TABLE
                row.add(null);                              // SOURCE_DATA_TYPE
                row.add(colMeta.isAutoIncrement ? "YES" : "NO"); // IS_AUTOINCREMENT
                row.add("NO");                              // IS_GENERATEDCOLUMN
                rows.add(row);
            }
        }

        Protocol.QueryResponse queryResponse = new Protocol.QueryResponse();
        queryResponse.columns = columns;
        queryResponse.rows = rows;
        queryResponse.done = true;
        return new ClouderbyResultSet(null, queryResponse);
    }

    private ResultSet createEmptyResultSet() {
        return new ClouderbyResultSet(null, new Protocol.QueryResponse());
    }

    @Override
    public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern)
            throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern)
            throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable)
            throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
        if (table == null) {
            return createEmptyResultSet();
        }

        Protocol.MetadataPrimaryKeysResponse response = httpClient.getMetadataPrimaryKeys(catalog, schema, table);

        // Build JDBC standard primary keys ResultSet
        List<Protocol.ColumnInfo> columns = new ArrayList<>();
        String[] colNames = {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "KEY_SEQ", "PK_NAME"};
        for (String name : colNames) {
            Protocol.ColumnInfo col = new Protocol.ColumnInfo();
            col.name = name;
            col.type = "TEXT";
            columns.add(col);
        }

        List<List<Object>> rows = new ArrayList<>();
        if (response.primaryKeys != null) {
            for (Protocol.PrimaryKeyInfo pkInfo : response.primaryKeys) {
                List<Object> row = new ArrayList<>();
                row.add(pkInfo.catalog);     // TABLE_CAT
                row.add(pkInfo.schema);      // TABLE_SCHEM
                row.add(pkInfo.tableName);   // TABLE_NAME
                row.add(pkInfo.columnName);  // COLUMN_NAME
                row.add(pkInfo.keySeq);      // KEY_SEQ
                row.add(pkInfo.pkName);      // PK_NAME
                rows.add(row);
            }
        }

        Protocol.QueryResponse queryResponse = new Protocol.QueryResponse();
        queryResponse.columns = columns;
        queryResponse.rows = rows;
        queryResponse.done = true;
        return new ClouderbyResultSet(null, queryResponse);
    }

    @Override
    public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable,
                                         String foreignCatalog, String foreignSchema, String foreignTable)
            throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getTypeInfo() throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate)
            throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public boolean supportsResultSetType(int type) throws SQLException {
        return type == ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public boolean supportsResultSetConcurrency(int type, int concurrency) throws SQLException {
        return type == ResultSet.TYPE_FORWARD_ONLY && concurrency == ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public boolean ownUpdatesAreVisible(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean ownDeletesAreVisible(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean ownInsertsAreVisible(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean othersUpdatesAreVisible(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean othersDeletesAreVisible(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean othersInsertsAreVisible(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean updatesAreDetected(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean deletesAreDetected(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean insertsAreDetected(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean supportsBatchUpdates() throws SQLException {
        return false;
    }

    @Override
    public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types)
            throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connection;
    }

    @Override
    public boolean supportsSavepoints() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsNamedParameters() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsMultipleOpenResults() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsGetGeneratedKeys() throws SQLException {
        return true;
    }

    @Override
    public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern)
            throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern)
            throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern,
                                    String attributeNamePattern) throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public boolean supportsResultSetHoldability(int holdability) throws SQLException {
        return holdability == ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public int getDatabaseMajorVersion() throws SQLException {
        return 3;
    }

    @Override
    public int getDatabaseMinorVersion() throws SQLException {
        return 46;
    }

    @Override
    public int getJDBCMajorVersion() throws SQLException {
        return 4;
    }

    @Override
    public int getJDBCMinorVersion() throws SQLException {
        return 2;
    }

    @Override
    public int getSQLStateType() throws SQLException {
        return sqlStateSQL;
    }

    @Override
    public boolean locatorsUpdateCopy() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsStatementPooling() throws SQLException {
        return false;
    }

    @Override
    public RowIdLifetime getRowIdLifetime() throws SQLException {
        return RowIdLifetime.ROWID_UNSUPPORTED;
    }

    @Override
    public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
        return false;
    }

    @Override
    public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
        return false;
    }

    @Override
    public ResultSet getClientInfoProperties() throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern)
            throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern,
                                          String columnNamePattern) throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern,
                                        String columnNamePattern) throws SQLException {
        return new ClouderbyResultSet(null, new io.gitlab.myst3m.clouderby.jdbc.http.Protocol.QueryResponse());
    }

    @Override
    public boolean generatedKeyAlwaysReturned() throws SQLException {
        return true;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isAssignableFrom(getClass());
    }
}
