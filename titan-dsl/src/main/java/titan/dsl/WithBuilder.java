package titan.dsl;

import java.util.Arrays;
import java.util.Objects;

/**
 * Entry-point for WITH ... SELECT query construction.
 */
public final class WithBuilder {

    private final boolean recursive;
    private final SqlDialect dialect;
    private final QueryFilters filters;
    private final CommonTableExpression<?>[] expressions;

    WithBuilder(boolean recursive, CommonTableExpression<?>... expressions) {
        this(null, null, recursive, expressions);
    }

    WithBuilder(SqlDialect dialect, QueryFilters filters, boolean recursive, CommonTableExpression<?>... expressions) {
        this.dialect = dialect;
        this.filters = filters;
        this.recursive = recursive;
        this.expressions = Objects.requireNonNull(expressions, "expressions");
        if (expressions.length == 0) {
            throw new IllegalArgumentException("with requires at least one CTE");
        }
        if (Arrays.stream(expressions).anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("with CTE expressions must not contain null");
        }
    }

    public SelectBuilder select(Column<?>... columns) {
        return new SelectBuilder(dialect, filters, columns).with(recursive, expressions);
    }
}
