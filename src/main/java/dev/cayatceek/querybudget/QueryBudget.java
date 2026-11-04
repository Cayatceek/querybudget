package dev.cayatceek.querybudget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Explicit, thread-bound JDBC query measurement. Wrap your application's DataSource
 * with MonitoringDataSource, then open a scope around the code under test.
 */
public final class QueryBudget {
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();

    private QueryBudget() { }

    public static Scope begin() {
        if (ACTIVE.get() != null) throw new IllegalStateException("Nested query budgets are not supported");
        Scope scope = new Scope(Thread.currentThread());
        ACTIVE.set(scope);
        return scope;
    }

    static void record(String sql) {
        Scope scope = ACTIVE.get();
        if (scope != null) scope.record(sql);
    }

    public static final class Scope implements AutoCloseable {
        private final Thread owner;
        private final Map<String, LongAdder> queries = new ConcurrentHashMap<>();
        private final LongAdder total = new LongAdder();
        private boolean closed;

        private Scope(Thread owner) { this.owner = owner; }

        private void record(String sql) {
            if (closed) throw new IllegalStateException("Scope already closed");
            queries.computeIfAbsent(Objects.requireNonNull(sql), ignored -> new LongAdder()).increment();
            total.increment();
        }

        public long count() { return total.sum(); }

        public Map<String, Long> countsBySql() {
            return queries.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> entry.getValue().sum()));
        }

        public void assertMax(long allowed) {
            if (allowed < 0) throw new IllegalArgumentException("Budget must be nonnegative");
            if (count() > allowed) throw new AssertionError(
                "SQL query budget exceeded: expected <= " + allowed + ", actual " + count()
                + ". Call countsBySql() explicitly for diagnostics; SQL can contain sensitive literals.");
        }

        @Override public void close() {
            if (Thread.currentThread() != owner || ACTIVE.get() != this) {
                throw new IllegalStateException("Close the scope on the thread where it was opened");
            }
            closed = true;
            ACTIVE.remove();
        }
    }
}
