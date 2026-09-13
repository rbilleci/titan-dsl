package titan.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AggregationFunctionsTest {

    @Test
    void sumMinMaxRenderAsAggregateColumns() {
        MetricsTable metrics = new MetricsTable();

        assertEquals("SUM(score)", DSL.sum(metrics.SCORE).name());
        assertEquals("MIN(score)", DSL.min(metrics.SCORE).name());
        assertEquals("MAX(score)", DSL.max(metrics.SCORE).name());
    }

    private static final class MetricsTable extends Table<Object> {
        private final Column<Integer> SCORE = column("score", SQLType.INTEGER, Nullability.NOT_NULL);

        private MetricsTable() {
            super("metrics", "public");
        }
    }
}
