package dev.cayatceek.querybudget;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** DataSource decorator that counts JDBC execute invocations in active QueryBudget scopes. */
public final class MonitoringDataSource implements DataSource {
    private final DataSource delegate;

    public MonitoringDataSource(DataSource delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override public Connection getConnection() throws SQLException {
        return monitor(delegate.getConnection());
    }
    @Override public Connection getConnection(String user, String password) throws SQLException {
        return monitor(delegate.getConnection(user, password));
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException ex) { throw ex.getCause(); }
    }

    private static Connection monitor(Connection connection) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                String name = method.getName();
                if (method.getDeclaringClass() == Object.class) {
                    return objectMethod(proxy, method, args);
                }
                if (name.equals("prepareStatement") || name.equals("prepareCall") || name.equals("createStatement")) {
                    Object statement = invoke(connection, method, args);
                    Class<?> api = name.equals("prepareCall") ? CallableStatement.class
                        : name.equals("prepareStatement") ? PreparedStatement.class : Statement.class;
                    String preparedSql = name.equals("createStatement") ? null : (String) args[0];
                    return Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api},
                        (statementProxy, statementMethod, statementArgs) -> {
                            if (statementMethod.getDeclaringClass() == Object.class) {
                                return objectMethod(statementProxy, statementMethod, statementArgs);
                            }
                            String operation = statementMethod.getName();
                            boolean execute = operation.equals("execute") || operation.equals("executeQuery")
                                || operation.equals("executeUpdate") || operation.equals("executeLargeUpdate")
                                || operation.equals("executeBatch") || operation.equals("executeLargeBatch");
                            if (execute) {
                                String sql = statementArgs != null && statementArgs.length > 0
                                    && statementArgs[0] instanceof String s ? s
                                    : preparedSql == null ? "<batch>" : preparedSql;
                                QueryBudget.record(sql);
                            }
                            return invoke(statement, statementMethod, statementArgs);
                        });
                }
                return invoke(connection, method, args);
            });
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "QueryBudgetProxy@" + Integer.toHexString(System.identityHashCode(proxy));
            default -> throw new IllegalStateException("Unexpected Object method");
        };
    }

    @Override public PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
    @Override public void setLogWriter(PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
    @Override public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
    @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
    @Override public Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException { return delegate.getParentLogger(); }
    @Override public <T> T unwrap(Class<T> type) throws SQLException {
        if (type.isInstance(this)) return type.cast(this);
        return delegate.unwrap(type);
    }
    @Override public boolean isWrapperFor(Class<?> type) throws SQLException {
        return type.isInstance(this) || delegate.isWrapperFor(type);
    }
}
