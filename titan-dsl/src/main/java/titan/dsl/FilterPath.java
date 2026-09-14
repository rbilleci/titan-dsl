package titan.dsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * An explicit foreign-key path from a filtered table to the column that carries the scope value.
 *
 * <p>Each hop names the local single-column foreign key to follow, in order:
 * {@code via(INVOICES.ORDER_ID, ORDERS.CUSTOMER_ID).to(CUSTOMERS.USER_ID)}. Without
 * {@link #to(Column)}, the path ends at the last referenced table's own binding for the same
 * filter. Composite foreign keys are not followed by paths; bind those tables with a lambda.</p>
 */
public final class FilterPath<T> {

    private final List<Column<?>> hops;
    private final Column<T> target;

    private FilterPath(List<Column<?>> hops, Column<T> target) {
        this.hops = hops;
        this.target = target;
    }

    public static <T> FilterPath<T> via(Column<?>... foreignKeyColumns) {
        if (foreignKeyColumns == null || foreignKeyColumns.length == 0) {
            throw new IllegalArgumentException("via(...) requires at least one foreign-key column");
        }
        List<Column<?>> hops = new ArrayList<>(foreignKeyColumns.length);
        for (Column<?> column : foreignKeyColumns) {
            hops.add(Objects.requireNonNull(column, "foreign-key column"));
        }
        return new FilterPath<>(List.copyOf(hops), null);
    }

    public <U> FilterPath<U> to(Column<U> column) {
        return new FilterPath<>(hops, Objects.requireNonNull(column, "column"));
    }

    List<Column<?>> hops() {
        return hops;
    }

    Column<T> target() {
        return target;
    }

    @Override
    public String toString() {
        return "via" + Arrays.toString(hops.stream().map(Column::name).toArray())
                + (target == null ? "" : " to " + target.name());
    }
}
