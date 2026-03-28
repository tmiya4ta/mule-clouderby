package io.gitlab.myst3m.clouderby.jdbc;

import io.gitlab.myst3m.clouderby.jdbc.http.Protocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ClouderbyResultSet.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClouderbyResultSet Tests")
class ClouderbyResultSetTest {

    @Mock
    private ClouderbyStatement statement;

    private ClouderbyResultSet resultSet;

    private Protocol.QueryResponse createResponse(String[] columnNames, String[] columnTypes, Object[][] rows) {
        Protocol.QueryResponse response = new Protocol.QueryResponse();
        response.columns = new java.util.ArrayList<>();
        for (int i = 0; i < columnNames.length; i++) {
            Protocol.ColumnInfo col = new Protocol.ColumnInfo();
            col.name = columnNames[i];
            col.type = columnTypes[i];
            response.columns.add(col);
        }
        response.rows = new java.util.ArrayList<>();
        for (Object[] row : rows) {
            response.rows.add(Arrays.asList(row));
        }
        response.done = true;
        return response;
    }

    @Nested
    @DisplayName("Navigation Tests")
    class NavigationTests {

        @Test
        @DisplayName("next should return true when rows available")
        void testNext_WithRows() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id", "name"},
                    new String[]{"INTEGER", "TEXT"},
                    new Object[][]{{1, "first"}, {2, "second"}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);

            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(2);

            assertThat(resultSet.next()).isFalse();
        }

        @Test
        @DisplayName("next should return false for empty ResultSet")
        void testNext_EmptyResultSet() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThat(resultSet.next()).isFalse();
        }

        @Test
        @DisplayName("previous should throw SQLFeatureNotSupportedException")
        void testPreviousThrowsUnsupported() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThatThrownBy(() -> resultSet.previous())
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
        }

        @Test
        @DisplayName("getRow should return current row number (1-based)")
        void testGetRow() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}, {2}, {3}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThat(resultSet.getRow()).isEqualTo(0); // Before first
            resultSet.next();
            assertThat(resultSet.getRow()).isEqualTo(1);
            resultSet.next();
            assertThat(resultSet.getRow()).isEqualTo(2);
        }

        @Test
        @DisplayName("isBeforeFirst should return true before first row")
        void testIsBeforeFirst() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThat(resultSet.isBeforeFirst()).isTrue();
            resultSet.next();
            assertThat(resultSet.isBeforeFirst()).isFalse();
        }

        @Test
        @DisplayName("isAfterLast should return true after last row")
        void testIsAfterLast() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThat(resultSet.isAfterLast()).isFalse();
            resultSet.next(); // On first row
            assertThat(resultSet.isAfterLast()).isFalse();
            resultSet.next(); // After last
            assertThat(resultSet.isAfterLast()).isTrue();
        }

        @Test
        @DisplayName("isFirst should return true on first row")
        void testIsFirst() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}, {2}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThat(resultSet.isFirst()).isFalse();
            resultSet.next();
            assertThat(resultSet.isFirst()).isTrue();
            resultSet.next();
            assertThat(resultSet.isFirst()).isFalse();
        }

        @Test
        @DisplayName("isLast should return true on last row")
        void testIsLast() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}, {2}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            resultSet.next();
            assertThat(resultSet.isLast()).isFalse();
            resultSet.next();
            assertThat(resultSet.isLast()).isTrue();
        }

        @Test
        @DisplayName("absolute should throw SQLFeatureNotSupportedException")
        void testAbsoluteThrowsUnsupported() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThatThrownBy(() -> resultSet.absolute(1))
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
        }

        @Test
        @DisplayName("relative should throw SQLFeatureNotSupportedException")
        void testRelativeThrowsUnsupported() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThatThrownBy(() -> resultSet.relative(1))
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
        }
    }

    @Nested
    @DisplayName("Type Retrieval Tests (by Index)")
    class TypeRetrievalByIndexTests {

        @Test
        @DisplayName("getString should return string value")
        void testGetString_FromString() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"name"},
                    new String[]{"TEXT"},
                    new Object[][]{{"test value"}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getString(1)).isEqualTo("test value");
        }

        @Test
        @DisplayName("getString should convert number to string")
        void testGetString_FromNumber() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"num"},
                    new String[]{"INTEGER"},
                    new Object[][]{{42}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getString(1)).isEqualTo("42");
        }

        @Test
        @DisplayName("getInt should return integer value")
        void testGetInt_FromInteger() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{42}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getInt(1)).isEqualTo(42);
        }

        @Test
        @DisplayName("getInt should return 0 for NULL")
        void testGetInt_FromNull() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{null}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getInt(1)).isEqualTo(0);
            assertThat(resultSet.wasNull()).isTrue();
        }

        @Test
        @DisplayName("getInt should parse string to int")
        void testGetInt_FromString() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"TEXT"},
                    new Object[][]{{"123"}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getInt(1)).isEqualTo(123);
        }

        @Test
        @DisplayName("getLong should return long value")
        void testGetLong() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"big_id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{Long.MAX_VALUE}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getLong(1)).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("getDouble should return double value")
        void testGetDouble() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"value"},
                    new String[]{"REAL"},
                    new Object[][]{{3.14159}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getDouble(1)).isEqualTo(3.14159);
        }

        @Test
        @DisplayName("getFloat should return float value")
        void testGetFloat() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"value"},
                    new String[]{"REAL"},
                    new Object[][]{{2.5}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getFloat(1)).isEqualTo(2.5f);
        }

        @Test
        @DisplayName("getBoolean should return true for non-zero integer")
        void testGetBoolean_FromInteger() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"flag1", "flag2", "flag3"},
                    new String[]{"INTEGER", "INTEGER", "INTEGER"},
                    new Object[][]{{1, 0, -1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getBoolean(1)).isTrue();  // 1 = true
            assertThat(resultSet.getBoolean(2)).isFalse(); // 0 = false
            assertThat(resultSet.getBoolean(3)).isTrue();  // -1 = true
        }

        @Test
        @DisplayName("getBoolean should parse string")
        void testGetBoolean_FromString() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"flag"},
                    new String[]{"TEXT"},
                    new Object[][]{{"true"}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getBoolean(1)).isTrue();
        }

        @Test
        @DisplayName("getBytes should decode Base64 BLOB")
        void testGetBytes_Base64Decode() throws SQLException {
            byte[] data = {0x01, 0x02, 0x03, (byte) 0xFF};
            String base64 = Base64.getEncoder().encodeToString(data);

            Protocol.QueryResponse response = createResponse(
                    new String[]{"data"},
                    new String[]{"BLOB"},
                    new Object[][]{{base64}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getBytes(1)).isEqualTo(data);
        }

        @Test
        @DisplayName("getBytes should return null for NULL")
        void testGetBytes_FromNull() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"data"},
                    new String[]{"BLOB"},
                    new Object[][]{{null}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getBytes(1)).isNull();
            assertThat(resultSet.wasNull()).isTrue();
        }

        @Test
        @DisplayName("getDate should parse date string")
        void testGetDate_FromString() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"created"},
                    new String[]{"TEXT"},
                    new Object[][]{{"2024-01-15"}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            Date date = resultSet.getDate(1);
            assertThat(date.toString()).isEqualTo("2024-01-15");
        }

        @Test
        @DisplayName("getTimestamp should parse timestamp string")
        void testGetTimestamp_FromString() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"created"},
                    new String[]{"TEXT"},
                    new Object[][]{{"2024-01-15 10:30:45"}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            Timestamp ts = resultSet.getTimestamp(1);
            assertThat(ts.toString()).contains("2024-01-15");
        }

        @Test
        @DisplayName("getBigDecimal should convert number")
        void testGetBigDecimal() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"amount"},
                    new String[]{"REAL"},
                    new Object[][]{{123.456}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            BigDecimal bd = resultSet.getBigDecimal(1);
            assertThat(bd.doubleValue()).isEqualTo(123.456);
        }

        @Test
        @DisplayName("getBigDecimal should parse string")
        void testGetBigDecimal_FromString() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"amount"},
                    new String[]{"TEXT"},
                    new Object[][]{{"9999999999999.99"}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            BigDecimal bd = resultSet.getBigDecimal(1);
            assertThat(bd.toString()).isEqualTo("9999999999999.99");
        }

        @Test
        @DisplayName("getObject should return raw value")
        void testGetObject() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"value"},
                    new String[]{"INTEGER"},
                    new Object[][]{{42}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            Object obj = resultSet.getObject(1);
            assertThat(obj).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("Type Retrieval Tests (by Name)")
    class TypeRetrievalByNameTests {

        @Test
        @DisplayName("getString by column name should work")
        void testGetStringByName() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"name"},
                    new String[]{"TEXT"},
                    new Object[][]{{"test"}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getString("name")).isEqualTo("test");
        }

        @Test
        @DisplayName("findColumn should return correct index")
        void testFindColumn() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id", "name", "value"},
                    new String[]{"INTEGER", "TEXT", "REAL"},
                    new Object[][]{{1, "test", 3.14}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThat(resultSet.findColumn("id")).isEqualTo(1);
            assertThat(resultSet.findColumn("name")).isEqualTo(2);
            assertThat(resultSet.findColumn("value")).isEqualTo(3);
        }

        @Test
        @DisplayName("findColumn should be case insensitive")
        void testFindColumnCaseInsensitive() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"MyColumn"},
                    new String[]{"TEXT"},
                    new Object[][]{{"test"}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThat(resultSet.findColumn("MyColumn")).isEqualTo(1);
            assertThat(resultSet.findColumn("mycolumn")).isEqualTo(1);
            assertThat(resultSet.findColumn("MYCOLUMN")).isEqualTo(1);
        }

        @Test
        @DisplayName("findColumn should throw SQLException for unknown column")
        void testFindColumn_NotFound() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThatThrownBy(() -> resultSet.findColumn("unknown"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Column not found");
        }

        @Test
        @DisplayName("getInt by column name should work")
        void testGetIntByName() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{42}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getInt("id")).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("NULL Handling Tests")
    class NullHandlingTests {

        @Test
        @DisplayName("wasNull should return true after reading NULL")
        void testWasNull_AfterNull() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"value"},
                    new String[]{"TEXT"},
                    new Object[][]{{null}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            resultSet.getString(1);
            assertThat(resultSet.wasNull()).isTrue();
        }

        @Test
        @DisplayName("wasNull should return false after reading non-NULL")
        void testWasNull_AfterNonNull() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"value"},
                    new String[]{"TEXT"},
                    new Object[][]{{"test"}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            resultSet.getString(1);
            assertThat(resultSet.wasNull()).isFalse();
        }

        @Test
        @DisplayName("getString should return null for NULL value")
        void testGetString_Null() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"name"},
                    new String[]{"TEXT"},
                    new Object[][]{{null}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getString(1)).isNull();
            assertThat(resultSet.wasNull()).isTrue();
        }

        @Test
        @DisplayName("Primitive getters should return default for NULL")
        void testPrimitiveGettersReturnDefault() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"int_col", "long_col", "double_col", "bool_col"},
                    new String[]{"INTEGER", "INTEGER", "REAL", "INTEGER"},
                    new Object[][]{{null, null, null, null}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getInt(1)).isEqualTo(0);
            assertThat(resultSet.wasNull()).isTrue();

            assertThat(resultSet.getLong(2)).isEqualTo(0L);
            assertThat(resultSet.wasNull()).isTrue();

            assertThat(resultSet.getDouble(3)).isEqualTo(0.0);
            assertThat(resultSet.wasNull()).isTrue();

            assertThat(resultSet.getBoolean(4)).isFalse();
            assertThat(resultSet.wasNull()).isTrue();
        }
    }

    @Nested
    @DisplayName("Close and State Tests")
    class CloseAndStateTests {

        @Test
        @DisplayName("close should mark ResultSet as closed")
        void testClose() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThat(resultSet.isClosed()).isFalse();
            resultSet.close();
            assertThat(resultSet.isClosed()).isTrue();
        }

        @Test
        @DisplayName("Operations on closed ResultSet should throw SQLException")
        void testOperationsOnClosedResultSet() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.close();

            assertThatThrownBy(() -> resultSet.next())
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("closed");

            assertThatThrownBy(() -> resultSet.getString(1))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("closed");
        }

        @Test
        @DisplayName("getType should return TYPE_FORWARD_ONLY")
        void testGetType() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThat(resultSet.getType()).isEqualTo(ResultSet.TYPE_FORWARD_ONLY);
        }

        @Test
        @DisplayName("getConcurrency should return CONCUR_READ_ONLY")
        void testGetConcurrency() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThat(resultSet.getConcurrency()).isEqualTo(ResultSet.CONCUR_READ_ONLY);
        }

        @Test
        @DisplayName("getFetchDirection should return FETCH_FORWARD")
        void testGetFetchDirection() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThat(resultSet.getFetchDirection()).isEqualTo(ResultSet.FETCH_FORWARD);
        }

        @Test
        @DisplayName("getStatement should return owning statement")
        void testGetStatement() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThat(resultSet.getStatement()).isSameAs(statement);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Getting value without calling next should throw SQLException")
        void testGetValueWithoutNext() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            assertThatThrownBy(() -> resultSet.getString(1))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("No current row");
        }

        @Test
        @DisplayName("Invalid column index should throw SQLException")
        void testInvalidColumnIndex() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThatThrownBy(() -> resultSet.getString(0))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("out of range");

            assertThatThrownBy(() -> resultSet.getString(2))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("out of range");
        }

        @Test
        @DisplayName("Update methods should throw SQLFeatureNotSupportedException")
        void testUpdateMethodsThrowUnsupported() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id"},
                    new String[]{"INTEGER"},
                    new Object[][]{{1}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThatThrownBy(() -> resultSet.updateString(1, "test"))
                    .isInstanceOf(SQLFeatureNotSupportedException.class);

            assertThatThrownBy(() -> resultSet.updateInt(1, 1))
                    .isInstanceOf(SQLFeatureNotSupportedException.class);

            assertThatThrownBy(() -> resultSet.insertRow())
                    .isInstanceOf(SQLFeatureNotSupportedException.class);

            assertThatThrownBy(() -> resultSet.deleteRow())
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
        }
    }

    @Nested
    @DisplayName("getObject with Type Tests")
    class GetObjectWithTypeTests {

        @Test
        @DisplayName("getObject(int, Class) should convert to requested type")
        void testGetObjectWithClass() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"value"},
                    new String[]{"INTEGER"},
                    new Object[][]{{42}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getObject(1, String.class)).isEqualTo("42");
            assertThat(resultSet.getObject(1, Integer.class)).isEqualTo(42);
            assertThat(resultSet.getObject(1, Long.class)).isEqualTo(42L);
        }

        @Test
        @DisplayName("getObject(int, Class) should return null for NULL")
        void testGetObjectWithClass_Null() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"value"},
                    new String[]{"TEXT"},
                    new Object[][]{{null}}
            );
            resultSet = new ClouderbyResultSet(statement, response);
            resultSet.next();

            assertThat(resultSet.getObject(1, String.class)).isNull();
        }
    }

    @Nested
    @DisplayName("MetaData Tests")
    class MetaDataTests {

        @Test
        @DisplayName("getMetaData should return ResultSetMetaData")
        void testGetMetaData() throws SQLException {
            Protocol.QueryResponse response = createResponse(
                    new String[]{"id", "name"},
                    new String[]{"INTEGER", "TEXT"},
                    new Object[][]{{1, "test"}}
            );
            resultSet = new ClouderbyResultSet(statement, response);

            ResultSetMetaData metaData = resultSet.getMetaData();
            assertThat(metaData.getColumnCount()).isEqualTo(2);
            assertThat(metaData.getColumnName(1)).isEqualTo("id");
            assertThat(metaData.getColumnName(2)).isEqualTo("name");
        }
    }
}
