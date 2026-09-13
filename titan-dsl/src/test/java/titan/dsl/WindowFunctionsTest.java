package titan.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WindowFunctionsTest {

    private static final MetricsTable METRICS = new MetricsTable();

    @Test
    void rowNumberOverPartitionAndOrderRendersExpectedSql() {
        String sql = DSL.select(
                        METRICS.DAY,
                        DSL.rowNumber().over(DSL.partitionBy(METRICS.ACCOUNT_ID).orderBy(METRICS.DAY.asc())))
                .from(METRICS)
                .fetch();

        assertEquals(
                "SELECT day, ROW_NUMBER() OVER (PARTITION BY account_id ORDER BY day ASC) FROM public.metrics",
                sql);
    }

    @Test
    void windowFrameRowsRangeAndGroupsRenderExpectedSql() {
        String rowsSql = DSL.select(
                        METRICS.DAY,
                        DSL.sum(METRICS.VALUE)
                                .over(DSL.partitionBy(METRICS.ACCOUNT_ID)
                                        .orderBy(METRICS.DAY.asc())
                                        .rowsBetween(DSL.unboundedPreceding(), DSL.currentRow())))
                .from(METRICS)
                .fetch();

        String rangeSql = DSL.select(
                        METRICS.DAY,
                        DSL.avg(METRICS.VALUE)
                                .over(DSL.orderBy(METRICS.DAY.asc())
                                        .rangeBetween(DSL.preceding(6), DSL.currentRow())))
                .from(METRICS)
                .fetch();

        String groupsSql = DSL.select(
                        METRICS.DAY,
                        DSL.avg(METRICS.VALUE)
                                .over(DSL.orderBy(METRICS.DAY.asc())
                                        .groupsBetween(DSL.preceding(2), DSL.following(1))))
                .from(METRICS)
                .fetch();

        assertEquals(
                "SELECT day, SUM(value) OVER (PARTITION BY account_id ORDER BY day ASC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) FROM public.metrics",
                rowsSql);
        assertEquals(
                "SELECT day, AVG(value) OVER (ORDER BY day ASC RANGE BETWEEN 6 PRECEDING AND CURRENT ROW) FROM public.metrics",
                rangeSql);
        assertEquals(
                "SELECT day, AVG(value) OVER (ORDER BY day ASC GROUPS BETWEEN 2 PRECEDING AND 1 FOLLOWING) FROM public.metrics",
                groupsSql);
    }

    @Test
    void lagLeadNthValueAndNtileGuardrails() {
        assertThrows(IllegalArgumentException.class, () -> DSL.ntile(0));
        assertThrows(IllegalArgumentException.class, () -> DSL.lag(METRICS.VALUE, 0));
        assertThrows(IllegalArgumentException.class, () -> DSL.lead(METRICS.VALUE, -1));
        assertThrows(IllegalArgumentException.class, () -> DSL.nthValue(METRICS.VALUE, 0));
    }

    @Test
    void nthValueRendersExpectedSql() {
        String sql = DSL.select(
                        METRICS.DAY,
                        DSL.nthValue(METRICS.VALUE, 3)
                                .over(DSL.partitionBy(METRICS.ACCOUNT_ID).orderBy(METRICS.DAY.asc())))
                .from(METRICS)
                .fetch();

        assertEquals(
                "SELECT day, NTH_VALUE(value, 3) OVER (PARTITION BY account_id ORDER BY day ASC) FROM public.metrics",
                sql);
    }

    private static final class MetricsTable extends Table<Object> {
        private final Column<Integer> ACCOUNT_ID = column("account_id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> DAY = column("day", SQLType.DATE, Nullability.NOT_NULL);
        private final Column<Double> VALUE = column("value", SQLType.DOUBLE, Nullability.NOT_NULL);

        private MetricsTable() {
            super("metrics", "public");
        }
    }
}
