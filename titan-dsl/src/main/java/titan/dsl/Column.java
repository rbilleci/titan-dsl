package titan.dsl;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * Typed column descriptor supporting condition builders.
 *
 * <p>Condition factories capture values as {@link BindValue}s (value + {@link SQLType}) so
 * rendering can be parameterized or literal per dialect, instead of pre-rendering literals
 * with quote-only escaping (audit findings D-1/D-2).</p>
 */
public final class Column<T> {

    private final String name;
    private final SQLType sqlType;
    private final Nullability nullability;
    /**
     * Structured expression backing this column (searched CASE, audit D-7), or {@code null} for
     * plain named columns. When present, projection rendering goes through the fragment so the
     * captured values stay typed (bind parameters / per-dialect literals); {@link #name()} falls
     * back to the deterministic default-dialect literal rendering for legacy name-based paths.
     */
    private final SqlFragment expression;

    public Column(String name, SQLType sqlType, Nullability nullability) {
        this.name = Objects.requireNonNull(name, "name");
        this.sqlType = Objects.requireNonNull(sqlType, "sqlType");
        this.nullability = Objects.requireNonNull(nullability, "nullability");
        this.expression = null;
    }

    Column(SqlFragment expression, SQLType sqlType, Nullability nullability) {
        this.expression = Objects.requireNonNull(expression, "expression");
        this.sqlType = Objects.requireNonNull(sqlType, "sqlType");
        this.nullability = Objects.requireNonNull(nullability, "nullability");
        SqlWriter writer = new SqlWriter(SqlWriter.Mode.LITERAL, SqlDialect.POSTGRESQL);
        expression.appendTo(writer);
        this.name = writer.sql();
    }

    public String name() {
        return name;
    }

    public SQLType sqlType() {
        return sqlType;
    }

    public Nullability nullability() {
        return nullability;
    }

    public Condition eq(T value) {
        if (value == null) {
            return isNull();
        }
        return Condition.fromFragment(SqlFragment.of(name + " = ", bind(value)));
    }

    public Condition eqColumn(Column<T> other) {
        Objects.requireNonNull(other, "other");
        if (sqlType != SQLType.UNKNOWN
                && other.sqlType() != SQLType.UNKNOWN
                && sqlType != other.sqlType()) {
            throw new IllegalArgumentException("Column comparison requires matching SQL types");
        }
        return Condition.of(name + " = " + other.name());
    }

    public Condition ne(T value) {
        if (value == null) {
            return isNotNull();
        }
        return Condition.fromFragment(SqlFragment.of(name + " <> ", bind(value)));
    }

    public Condition lt(T value) {
        return comparison(" < ", value, "lt");
    }

    public Condition gt(T value) {
        return comparison(" > ", value, "gt");
    }

    public Condition le(T value) {
        return comparison(" <= ", value, "le");
    }

    public Condition ge(T value) {
        return comparison(" >= ", value, "ge");
    }

    private Condition comparison(String operator, T value, String operatorName) {
        rejectNullOperand(value, operatorName);
        return Condition.fromFragment(SqlFragment.of(name + operator, bind(value)));
    }

    @SafeVarargs
    public final Condition in(T... values) {
        if (values == null || values.length == 0) {
            return Condition.of("1 = 0");
        }
        return in(Arrays.asList(values));
    }

    public Condition in(Collection<T> values) {
        if (values == null || values.isEmpty()) {
            return Condition.of("1 = 0");
        }
        SqlFragment.Builder fragment = SqlFragment.builder().add(name + " IN (");
        boolean first = true;
        for (T value : values) {
            rejectNullOperand(value, "in");
            if (!first) {
                fragment.add(", ");
            }
            fragment.add(bind(value));
            first = false;
        }
        fragment.add(")");
        return Condition.fromFragment(fragment.build());
    }

    public Condition in(SelectBuilder subquery) {
        Objects.requireNonNull(subquery, "subquery");
        if (subquery.projectionSize() != 1) {
            throw new IllegalArgumentException("IN subquery must project exactly one column");
        }
        SQLType projectedType = subquery.projectionSqlType(0);
        if (projectedType != sqlType) {
            throw new IllegalArgumentException("IN subquery projection type must match column type");
        }
        return Condition.fromFragment(SqlFragment.of(name + " IN (", subquery, ")"));
    }

    @SafeVarargs
    public final Condition notIn(T... values) {
        if (values == null || values.length == 0) {
            return Condition.of("1 = 1");
        }
        return notIn(Arrays.asList(values));
    }

    public Condition notIn(Collection<T> values) {
        if (values == null || values.isEmpty()) {
            return Condition.of("1 = 1");
        }
        SqlFragment.Builder fragment = SqlFragment.builder().add(name + " NOT IN (");
        boolean first = true;
        for (T value : values) {
            rejectNullOperand(value, "notIn");
            if (!first) {
                fragment.add(", ");
            }
            fragment.add(bind(value));
            first = false;
        }
        fragment.add(")");
        return Condition.fromFragment(fragment.build());
    }

    public Condition notIn(SelectBuilder subquery) {
        Objects.requireNonNull(subquery, "subquery");
        if (subquery.projectionSize() != 1) {
            throw new IllegalArgumentException("NOT IN subquery must project exactly one column");
        }
        SQLType projectedType = subquery.projectionSqlType(0);
        if (projectedType != sqlType) {
            throw new IllegalArgumentException("NOT IN subquery projection type must match column type");
        }
        return Condition.fromFragment(SqlFragment.of(name + " NOT IN (", subquery, ")"));
    }

    public Condition between(T lower, T upper) {
        rejectNullOperand(lower, "between");
        rejectNullOperand(upper, "between");
        return Condition.fromFragment(SqlFragment.of(name + " BETWEEN ", bind(lower), " AND ", bind(upper)));
    }

    public Column<T> add(Number value) {
        Objects.requireNonNull(value, "value");
        return new Column<>("(" + name + " + " + numericLiteral(value) + ")", sqlType, nullability);
    }

    public Column<T> subtract(Number value) {
        Objects.requireNonNull(value, "value");
        return new Column<>("(" + name + " - " + numericLiteral(value) + ")", sqlType, nullability);
    }

    public Column<T> multiply(Number value) {
        Objects.requireNonNull(value, "value");
        return new Column<>("(" + name + " * " + numericLiteral(value) + ")", sqlType, nullability);
    }

    public Condition like(String pattern) {
        if (!supportsLike(sqlType)) {
            throw new IllegalStateException("LIKE is only supported for textual column types");
        }
        rejectNullOperand(pattern, "like");
        return Condition.fromFragment(SqlFragment.of(name + " LIKE ", BindValue.of(pattern, sqlType)));
    }

    private static boolean supportsLike(SQLType sqlType) {
        return switch (sqlType) {
            case VARCHAR, TEXT, CHAR, ENUM -> true;
            default -> false;
        };
    }

    public Condition isNull() {
        return Condition.of(name + " IS NULL");
    }

    public Condition isNotNull() {
        return Condition.of(name + " IS NOT NULL");
    }

    public SortField asc() {
        return new SortField(name, true);
    }

    public SortField desc() {
        return new SortField(name, false);
    }

    public Column<T> over(WindowSpecificationBuilder specification) {
        Objects.requireNonNull(specification, "specification");
        return new Column<>(name + " OVER (" + specification.toSql() + ")", sqlType, nullability);
    }

    private BindValue bind(Object value) {
        return BindValue.of(value, sqlType);
    }

    private void rejectNullOperand(Object value, String operatorName) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "null is not a valid operand for " + operatorName + "(...) on column '" + name
                            + "'; use isNull()/isNotNull() for null checks");
        }
    }

    /**
     * Numbers embedded into derived column expressions; numeric rendering is dialect-independent
     * passthrough, so the default dialect is safe here.
     */
    private static String numericLiteral(Number value) {
        return SqlLiterals.render(value, SQLType.NUMERIC, SqlDialect.POSTGRESQL);
    }

    /**
     * Renders this column as a projection element: structured expressions (searched CASE) render
     * through their fragment so values stay typed per mode/dialect; plain columns render by name.
     */
    void appendProjectionTo(SqlWriter writer) {
        if (expression != null) {
            expression.appendTo(writer);
            return;
        }
        writer.append(name);
    }

    /**
     * Value equality on (name, sqlType, nullability) — audit D-9: identity equality broke
     * {@code LinkedHashMap}-keyed builder assignments for equal-but-distinct instances
     * ({@code table.column("id", ...)} called twice produced two map keys for one column).
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Column<?> column)) {
            return false;
        }
        return name.equals(column.name) && sqlType == column.sqlType && nullability == column.nullability;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, sqlType, nullability);
    }

    @Override
    public String toString() {
        return "Column[" + name + ", " + sqlType + ", " + nullability + "]";
    }
}
