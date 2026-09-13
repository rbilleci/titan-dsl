package titan.dsl;

import java.util.List;

public record UniqueKey<R>(boolean primary, List<Column<?>> columns) {
}
