package titan.dsl;

import java.util.Objects;

/**
 * One relation as it appears in a query (a table, view, or alias) with a qualifier for its
 * columns. Automatic-filter bindings receive a {@code TableRef} so their conditions are always
 * qualified correctly, whether the relation was aliased or not.
 */
public final class TableRef {

    private final TableLike<?> relation;
    private final String qualifier;

    private TableRef(TableLike<?> relation, String qualifier) {
        this.relation = relation;
        this.qualifier = qualifier;
    }

    /** Returns {@code null} for relations that have no physical identity (CTEs, inline views). */
    static TableRef of(TableLike<?> relation) {
        Objects.requireNonNull(relation, "relation");
        if (relation instanceof AliasedTable<?> aliased) {
            return new TableRef(aliased.table(), aliased.alias());
        }
        if (relation instanceof Table<?> || relation instanceof View<?>) {
            return new TableRef(relation, relation.schema() + '.' + relation.name());
        }
        return null;
    }

    /** The underlying table or view descriptor, never an alias wrapper. */
    public TableLike<?> relation() {
        return relation;
    }

    /** Qualifies a column of this relation: {@code alias.col} or {@code schema.table.col}. */
    public <T> Column<T> col(Column<T> column) {
        Objects.requireNonNull(column, "column");
        return new Column<>(qualifier + "." + terminalIdentifier(column.name()), column.sqlType(), column.nullability());
    }

    static String terminalIdentifier(String identifier) {
        int lastDot = identifier.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < identifier.length()) {
            return identifier.substring(lastDot + 1);
        }
        return identifier;
    }

    @Override
    public String toString() {
        return qualifier;
    }
}
