package titan.dsl;

public interface TriggerRowAccessor<R> extends ReadOnlyTriggerRowAccessor<R> {
    <T> void set(Column<T> column, T value);
}
