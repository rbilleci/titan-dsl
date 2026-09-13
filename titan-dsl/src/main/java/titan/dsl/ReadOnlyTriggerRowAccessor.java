package titan.dsl;

public interface ReadOnlyTriggerRowAccessor<R> {
    <T> T get(Column<T> column);
}
