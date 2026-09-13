package titan.dsl;

import java.util.Objects;

/**
 * A table reference under an explicit alias (audit D-7): enables self-joins, which are
 * inexpressible with bare {@link Table} references because both sides share one name.
 *
 * <p>Created via {@link Table#as(String)}. Columns referenced through the alias are obtained
 * with {@link #col(Column)}, which re-qualifies the underlying column with the alias:</p>
 *
 * <pre>{@code
 * AliasedTable<Object> e = EMPLOYEES.as("e");
 * AliasedTable<Object> m = EMPLOYEES.as("m");
 * select(e.col(EMPLOYEES.NAME), m.col(EMPLOYEES.NAME))
 *         .from(e)
 *         .join(m).on(e.col(EMPLOYEES.MANAGER_ID), m.col(EMPLOYEES.ID))
 *         .fetch();
 * }</pre>
 */
public final class AliasedTable<R> implements TableLike<R> {

    private final Table<R> table;
    private final String alias;

    AliasedTable(Table<R> table, String alias) {
        this.table = Objects.requireNonNull(table, "table");
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("table alias must not be blank");
        }
        if (!isIdentifierShaped(alias)) {
            throw new IllegalArgumentException("table alias must be identifier-shaped: '" + alias + "'");
        }
        this.alias = alias;
    }

    /** The underlying (unaliased) table. */
    public Table<R> table() {
        return table;
    }

    /** The alias this table is referenced under. */
    public String alias() {
        return alias;
    }

    @Override
    public String name() {
        return table.name();
    }

    @Override
    public String schema() {
        return table.schema();
    }

    /**
     * Re-qualifies a column of the underlying table with this alias
     * ({@code e.col(EMPLOYEES.ID)} renders as {@code e.id}).
     */
    public <T> Column<T> col(Column<T> column) {
        Objects.requireNonNull(column, "column");
        return new Column<>(alias + "." + terminalIdentifier(column.name()), column.sqlType(), column.nullability());
    }

    private static String terminalIdentifier(String identifier) {
        int lastDot = identifier.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < identifier.length()) {
            return identifier.substring(lastDot + 1);
        }
        return identifier;
    }

    private static boolean isIdentifierShaped(String identifier) {
        if (!Character.isLetter(identifier.charAt(0)) && identifier.charAt(0) != '_') {
            return false;
        }
        for (int i = 1; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }
}
