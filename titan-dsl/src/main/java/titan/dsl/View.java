package titan.dsl;

import java.util.Objects;

public class View<R> implements TableLike<R> {

    private final String name;
    private final String schema;

    protected View(String name, String schema) {
        this.name = Objects.requireNonNull(name, "name");
        this.schema = Objects.requireNonNull(schema, "schema");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String schema() {
        return schema;
    }

    public <T> Column<T> column(String name, SQLType sqlType, Nullability nullability) {
        return new Column<>(name, sqlType, nullability);
    }
}
