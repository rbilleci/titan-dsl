package titan.dsl.generative;

import java.util.List;
import titan.dsl.Column;
import titan.dsl.DSL;
import titan.dsl.Nullability;
import titan.dsl.SQLType;
import titan.dsl.Table;

final class DslProjectionArityGenerator {

    GeneratedDslCase generate(long seed) {
        ProjectionFamily family = ProjectionFamily.values()[(int) Math.floorMod(seed, ProjectionFamily.values().length)];
        WideTable wide = new WideTable();

        return switch (family) {
            case WIDTH_1 -> caseFor(seed, family, () -> DSL.select(wide.C1)
                    .from(wide)
                    .orderBy(wide.C1.asc())
                    .fetch());
            case WIDTH_2 -> caseFor(seed, family, () -> DSL.select(wide.C1, wide.C2)
                    .from(wide)
                    .where(wide.C1.eq(1))
                    .fetch());
            case WIDTH_4 -> caseFor(seed, family, () -> DSL.select(wide.C1, wide.C2, wide.C3, wide.C4)
                    .from(wide)
                    .where(wide.C1.eq(1).and(wide.C4.eq(true)))
                    .fetch());
            case WIDTH_6 -> caseFor(seed, family, () -> DSL.select(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5, wide.C6)
                    .from(wide)
                    .where(wide.C6.eq(6))
                    .orderBy(wide.C2.asc())
                    .fetch());
            case WIDTH_8 -> caseFor(seed, family, () -> DSL.select(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5, wide.C6, wide.C7, wide.C8)
                    .from(wide)
                    .where(wide.C7.isNotNull())
                    .fetch());
            case WIDTH_10 -> caseFor(seed, family, () -> DSL.select(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5, wide.C6, wide.C7, wide.C8, wide.C9, wide.C10)
                    .from(wide)
                    .orderBy(wide.C10.asc())
                    .fetch());
        };
    }

    private static GeneratedDslCase caseFor(long seed, ProjectionFamily family, DslCaseExecutable executable) {
        return new GeneratedDslCase(
                seed,
                DslGenerativeProfile.PROJECTION_ARITY.id(),
                family.id,
                "projection width " + family.width,
                false,
                family.expectedFragments,
                executable
        );
    }

    private enum ProjectionFamily {
        WIDTH_1("width-1", 1, List.of("select c1", "from public.wide_table")),
        WIDTH_2("width-2", 2, List.of("select c1, c2", "where c1 = 1")),
        WIDTH_4("width-4", 4, List.of("select c1, c2, c3, c4", "where (c1 = 1) and (c4 = true)")),
        WIDTH_6("width-6", 6, List.of("select c1, c2, c3, c4, c5, c6", "where c6 = 6")),
        WIDTH_8("width-8", 8, List.of("select c1, c2, c3, c4, c5, c6, c7, c8", "c7 is not null")),
        WIDTH_10("width-10", 10, List.of("select c1, c2, c3, c4, c5, c6, c7, c8, c9, c10", "order by c10 asc"));

        private final String id;
        private final int width;
        private final List<String> expectedFragments;

        ProjectionFamily(String id, int width, List<String> expectedFragments) {
            this.id = id;
            this.width = width;
            this.expectedFragments = expectedFragments;
        }
    }

    private static final class WideTable extends Table<Object> {
        private final Column<Integer> C1 = column("c1", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> C2 = column("c2", SQLType.VARCHAR, Nullability.NOT_NULL);
        private final Column<Long> C3 = column("c3", SQLType.BIGINT, Nullability.NOT_NULL);
        private final Column<Boolean> C4 = column("c4", SQLType.BOOLEAN, Nullability.NOT_NULL);
        private final Column<String> C5 = column("c5", SQLType.VARCHAR, Nullability.NULLABLE);
        private final Column<Integer> C6 = column("c6", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> C7 = column("c7", SQLType.VARCHAR, Nullability.NULLABLE);
        private final Column<Long> C8 = column("c8", SQLType.BIGINT, Nullability.NOT_NULL);
        private final Column<Integer> C9 = column("c9", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> C10 = column("c10", SQLType.VARCHAR, Nullability.NOT_NULL);

        private WideTable() {
            super("wide_table", "public");
        }
    }
}
