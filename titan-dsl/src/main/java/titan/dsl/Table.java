package titan.dsl;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Table<R> implements TableLike<R> {

    private final String name;
    private final String schema;

    protected Table(String name, String schema) {
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

    /**
     * References this table under an explicit alias (audit D-7), enabling self-joins.
     * Columns are re-qualified through {@link AliasedTable#col(Column)}.
     */
    public final AliasedTable<R> as(String alias) {
        return new AliasedTable<>(this, alias);
    }

    public <T> Column<T> column(String name, SQLType sqlType, Nullability nullability) {
        return new Column<>(name, sqlType, nullability);
    }

    @SafeVarargs
    protected final UniqueKey<R> primaryKey(Column<?>... columns) {
        return new UniqueKey<>(true, Arrays.asList(columns));
    }

    @SafeVarargs
    protected final UniqueKey<R> uniqueKey(Column<?>... columns) {
        return new UniqueKey<>(false, Arrays.asList(columns));
    }

    protected final <T> ForeignKey<R, T> foreignKey(Column<?> local, Table<T> referencedTable, Column<?> referencedColumn) {
        return new ForeignKey<>(List.of(local), referencedTable, List.of(referencedColumn));
    }

    protected final <T> ForeignKey<R, T> foreignKey(Column<?>[] local,
                                                    Table<T> referencedTable,
                                                    Column<?>[] referenced) {
        return new ForeignKey<>(
                List.copyOf(Arrays.asList(Objects.requireNonNull(local, "local"))),
                referencedTable,
                List.copyOf(Arrays.asList(Objects.requireNonNull(referenced, "referenced"))));
    }

    protected final <T> ForeignKey<R, T> foreignKey(Column<?> local1,
                                                    Column<?> local2,
                                                    Table<T> referencedTable,
                                                    Column<?> referenced1,
                                                    Column<?> referenced2) {
        return new ForeignKey<>(List.of(local1, local2), referencedTable, List.of(referenced1, referenced2));
    }
}
