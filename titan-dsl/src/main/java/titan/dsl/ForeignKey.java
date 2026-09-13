package titan.dsl;

import java.util.List;
import java.util.Objects;

/**
 * Foreign-key metadata between a local table and a referenced table.
 *
 * <p>Columns are immutable {@link List}s (audit D-9): the previous array components leaked
 * mutable state through the record accessors and broke record value-equality (arrays compare
 * by identity in the generated {@code equals}/{@code hashCode}).</p>
 */
public record ForeignKey<S, T>(List<Column<?>> localColumns, Table<T> referencedTable, List<Column<?>> referencedColumns) {

    public ForeignKey {
        localColumns = List.copyOf(Objects.requireNonNull(localColumns, "localColumns"));
        referencedColumns = List.copyOf(Objects.requireNonNull(referencedColumns, "referencedColumns"));
        Objects.requireNonNull(referencedTable, "referencedTable");
    }
}
