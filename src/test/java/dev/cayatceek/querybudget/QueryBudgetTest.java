package dev.cayatceek.querybudget;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class QueryBudgetTest {
    private MonitoringDataSource dataSource;

    @BeforeEach
    void setup() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:querybudget_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource = new MonitoringDataSource(source);
    }

    @Test
    void countsStatementsAndPreparedStatements() throws Exception {
        try (QueryBudget.Scope scope = QueryBudget.begin();
             Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE items(id INT)");
                statement.executeUpdate("INSERT INTO items VALUES (1)");
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM items WHERE id = ?")) {
                statement.setInt(1, 1);
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                }
            }
            assertEquals(3, scope.count());
            assertEquals(1L, scope.countsBySql().get("SELECT * FROM items WHERE id = ?").longValue());
            scope.assertMax(3);
            AssertionError error = assertThrows(AssertionError.class, () -> scope.assertMax(2));
            assertTrue(error.getMessage().contains("actual 3"));
        }
    }

    @Test
    void countsFailedStatements() throws Exception {
        try (QueryBudget.Scope scope = QueryBudget.begin();
             Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            assertThrows(java.sql.SQLException.class, () -> statement.execute("SELECT * FROM missing"));
            assertEquals(1, scope.count());
        }
    }

    @Test
    void countsBatchAsOneJdbcExecution() throws Exception {
        try (QueryBudget.Scope scope = QueryBudget.begin();
             Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE batch_items(id INT)");
            statement.addBatch("INSERT INTO batch_items VALUES (1)");
            statement.addBatch("INSERT INTO batch_items VALUES (2)");
            statement.executeBatch();
            assertEquals(2, scope.count());
        }
    }

    @Test
    void jdbcWrapperAndStatementConnectionDoNotBypassCounting() throws Exception {
        try (QueryBudget.Scope scope = QueryBudget.begin();
             Connection connection = dataSource.getConnection()) {
            assertTrue(connection.isWrapperFor(Connection.class));
            assertSame(connection, connection.unwrap(Connection.class));
            try (Statement statement = connection.createStatement()) {
                assertSame(connection, statement.getConnection());
                assertTrue(statement.isWrapperFor(Statement.class));
                assertSame(statement, statement.unwrap(Statement.class));
                try (Statement fromConnection = statement.getConnection().createStatement()) {
                    fromConnection.execute("SELECT 1");
                }
            }
            try (PreparedStatement prepared = connection.prepareStatement("SELECT ?")) {
                assertSame(prepared, prepared.unwrap(PreparedStatement.class));
                assertSame(connection, prepared.getConnection());
                prepared.setInt(1, 1);
                try (var row = prepared.executeQuery()) {
                    assertTrue(row.next());
                }
            }
            assertEquals(2, scope.count());
        }
    }

    @Test
    void assertionNeverLeaksSqlLiteralsByDefault() throws Exception {
        try (QueryBudget.Scope scope = QueryBudget.begin();
             Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 'private-user-token'");
            AssertionError error = assertThrows(AssertionError.class, () -> scope.assertMax(0));
            assertFalse(error.getMessage().contains("private-user-token"));
            assertEquals(1, scope.count());
        }
    }

    @Test
    void sessionClosesAndDoesNotLeakIntoSubsequentTests() throws Exception {
        try (QueryBudget.Scope scope = QueryBudget.begin()) {
            assertThrows(IllegalStateException.class, QueryBudget::begin);
        }
        try (QueryBudget.Scope second = QueryBudget.begin()) {
            assertEquals(0, second.count());
            assertThrows(IllegalArgumentException.class, () -> second.assertMax(-1));
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
        }
    }
}
