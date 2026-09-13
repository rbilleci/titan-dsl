package titan.dsl;

import java.util.Objects;

public final class WindowFunction<T> {

    private final String functionSql;
    private final SQLType sqlType;
    private final Nullability nullability;

    WindowFunction(String functionSql, SQLType sqlType, Nullability nullability) {
        this.functionSql = Objects.requireNonNull(functionSql, "functionSql");
        this.sqlType = Objects.requireNonNull(sqlType, "sqlType");
        this.nullability = Objects.requireNonNull(nullability, "nullability");
    }

    public Column<T> over(WindowSpecificationBuilder specification) {
        Objects.requireNonNull(specification, "specification");
        return new Column<>(functionSql + " OVER (" + specification.toSql() + ")", sqlType, nullability);
    }
}
