package titan.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Phase 3.3 (audit D-7) DSL surface: table aliases (self-joins), DISTINCT, searched CASE,
 * NULLS FIRST/LAST sort ordering, and NOT IN — rendered in both modes (literal per dialect,
 * parameterized with ordered bind values).
 */
class DslSurfacePhase33RenderingTest {

    private static final EmployeesTable EMPLOYEES = new EmployeesTable();

    // ---------------------------------------------------------------- table aliases / self-join

    @Test
    void aliasedSelfJoinRendersAliasQualifiedSql() {
        AliasedTable<Object> e = EMPLOYEES.as("e");
        AliasedTable<Object> m = EMPLOYEES.as("m");

        String sql = DSL.select(e.col(EMPLOYEES.NAME), m.col(EMPLOYEES.NAME))
                .from(e)
                .join(m).on(e.col(EMPLOYEES.MANAGER_ID), m.col(EMPLOYEES.ID))
                .fetch();

        assertEquals("SELECT e.name, m.name FROM hr.employees AS e "
                + "JOIN hr.employees AS m ON e.manager_id = m.id", sql);
    }

    @Test
    void aliasedSelfJoinRendersParameterizedWithAliasQualifiers() {
        AliasedTable<Object> e = EMPLOYEES.as("e");
        AliasedTable<Object> m = EMPLOYEES.as("m");

        ParameterizedSql rendered = DSL.select(e.col(EMPLOYEES.NAME))
                .from(e)
                .leftJoin(m).on(e.col(EMPLOYEES.MANAGER_ID), m.col(EMPLOYEES.ID))
                .where(e.col(EMPLOYEES.SCORE).gt(50))
                .render(SqlDialect.MYSQL);

        assertEquals("SELECT e.name FROM hr.employees AS e "
                + "LEFT JOIN hr.employees AS m ON e.manager_id = m.id WHERE e.score > ?", rendered.sql());
        assertEquals(List.of(50), rendered.parameters().stream().map(BindValue::value).toList());
    }

    @Test
    void blankOrMalformedAliasIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> EMPLOYEES.as(" "));
        assertThrows(IllegalArgumentException.class, () -> EMPLOYEES.as("e; DROP TABLE x"));
    }

    // ---------------------------------------------------------------- DISTINCT

    @Test
    void distinctRendersSelectDistinctInBothModes() {
        assertEquals("SELECT DISTINCT name FROM hr.employees",
                DSL.select(EMPLOYEES.NAME).from(EMPLOYEES).distinct().fetch());

        ParameterizedSql rendered = DSL.select(EMPLOYEES.NAME)
                .from(EMPLOYEES)
                .distinct()
                .where(EMPLOYEES.SCORE.ge(10))
                .render(SqlDialect.POSTGRESQL);
        assertEquals("SELECT DISTINCT name FROM hr.employees WHERE score >= ?", rendered.sql());
        assertEquals(List.of(10), rendered.parameters().stream().map(BindValue::value).toList());
    }

    @Test
    void typedSelectBuilderPreservesTypeThroughDistinct() {
        // Fluent preservation on the generated arity ladder (D-5 template includes distinct()).
        String sql = DSL.select(EMPLOYEES.ID, EMPLOYEES.NAME)
                .from(EMPLOYEES)
                .distinct()
                .forEach((id, name) -> {
                    // compile-time callback placeholder; the typed overload resolving proves
                    // distinct() returned SelectBuilder2<Integer, String>.
                });
        assertTrue(sql.contains("SELECT DISTINCT id, name FROM hr.employees"), sql);
    }

    // ---------------------------------------------------------------- searched CASE

    @Test
    void searchedCaseRendersLiteralPerDialect() {
        Column<String> band = DSL.when(EMPLOYEES.SCORE.ge(90), "A")
                .when(EMPLOYEES.SCORE.ge(50), "B")
                .otherwise("C");

        String postgres = DSL.select(band).from(EMPLOYEES).toSql(SqlDialect.POSTGRESQL);
        assertEquals("SELECT CASE WHEN score >= 90 THEN 'A' WHEN score >= 50 THEN 'B' ELSE 'C' END "
                + "FROM hr.employees", postgres);

        Column<String> escaped = DSL.when(EMPLOYEES.NAME.eq("O'Brien\\"), "match").otherwise("none");
        String mysql = DSL.select(escaped).from(EMPLOYEES).toSql(SqlDialect.MYSQL);
        assertEquals("SELECT CASE WHEN name = 'O''Brien\\\\' THEN 'match' ELSE 'none' END FROM hr.employees",
                mysql);
    }

    @Test
    void searchedCaseRendersParameterizedWithOrderedBinds() {
        Column<String> band = DSL.when(EMPLOYEES.SCORE.ge(90), "A").otherwise("B");

        ParameterizedSql rendered = DSL.select(band)
                .from(EMPLOYEES)
                .where(EMPLOYEES.ACTIVE.eq(true))
                .render(SqlDialect.POSTGRESQL);

        assertEquals("SELECT CASE WHEN score >= ? THEN ? ELSE ? END FROM hr.employees WHERE active = ?",
                rendered.sql());
        assertEquals(List.of(90, "A", "B", true),
                rendered.parameters().stream().map(BindValue::value).toList());
    }

    // ---------------------------------------------------------------- NULLS FIRST / LAST

    @Test
    void nullsOrderingRendersNativePostgresAndEmulatedMySql() {
        SelectBuilder query = DSL.select(EMPLOYEES.NAME)
                .from(EMPLOYEES)
                .orderBy(EMPLOYEES.SCORE.desc().nullsLast(), EMPLOYEES.NAME.asc());

        assertEquals("SELECT name FROM hr.employees ORDER BY score DESC NULLS LAST, name ASC",
                query.toSql(SqlDialect.POSTGRESQL));
        // MySQL has no NULLS LAST: emulated with a leading (expr IS NULL) sort key.
        assertEquals("SELECT name FROM hr.employees ORDER BY (score IS NULL) ASC, score DESC, name ASC",
                query.toSql(SqlDialect.MYSQL));
    }

    @Test
    void nullsFirstRendersNativePostgresAndEmulatedMySql() {
        SelectBuilder query = DSL.select(EMPLOYEES.NAME)
                .from(EMPLOYEES)
                .orderBy(EMPLOYEES.SCORE.asc().nullsFirst());

        assertEquals("SELECT name FROM hr.employees ORDER BY score ASC NULLS FIRST",
                query.toSql(SqlDialect.POSTGRESQL));
        assertEquals("SELECT name FROM hr.employees ORDER BY (score IS NULL) DESC, score ASC",
                query.toSql(SqlDialect.MYSQL));
    }

    // ---------------------------------------------------------------- NOT IN

    @Test
    void notInValuesRendersInBothModes() {
        SelectBuilder query = DSL.select(EMPLOYEES.NAME)
                .from(EMPLOYEES)
                .where(EMPLOYEES.ID.notIn(3, 5, 8));

        assertEquals("SELECT name FROM hr.employees WHERE id NOT IN (3, 5, 8)",
                query.toSql(SqlDialect.POSTGRESQL));

        ParameterizedSql rendered = query.render(SqlDialect.MYSQL);
        assertEquals("SELECT name FROM hr.employees WHERE id NOT IN (?, ?, ?)", rendered.sql());
        assertEquals(List.of(3, 5, 8), rendered.parameters().stream().map(BindValue::value).toList());
    }

    @Test
    void notInSubqueryRendersNestedSelect() {
        SelectBuilder subquery = DSL.select(EMPLOYEES.MANAGER_ID)
                .from(EMPLOYEES)
                .where(EMPLOYEES.ACTIVE.eq(false));

        String sql = DSL.select(EMPLOYEES.NAME)
                .from(EMPLOYEES)
                .where(EMPLOYEES.ID.notIn(subquery))
                .fetch();

        assertEquals("SELECT name FROM hr.employees WHERE id NOT IN "
                + "(SELECT manager_id FROM hr.employees WHERE active = FALSE)", sql);
    }

    @Test
    void emptyNotInMatchesEverythingAndEmptyInMatchesNothing() {
        assertEquals("1 = 1", EMPLOYEES.ID.notIn().sql());
        assertEquals("1 = 0", EMPLOYEES.ID.in().sql());
    }

    @Test
    void notInRejectsNullElementsAndMismatchedSubqueries() {
        assertThrows(IllegalArgumentException.class, () -> EMPLOYEES.ID.notIn(1, null, 3));
        assertThrows(IllegalArgumentException.class,
                () -> EMPLOYEES.ID.notIn(DSL.select(EMPLOYEES.NAME).from(EMPLOYEES)));
        assertThrows(IllegalArgumentException.class,
                () -> EMPLOYEES.ID.notIn(DSL.select(EMPLOYEES.ID, EMPLOYEES.NAME).from(EMPLOYEES)));
    }

    private static final class EmployeesTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> NAME = column("name", SQLType.VARCHAR, Nullability.NOT_NULL);
        private final Column<Integer> MANAGER_ID = column("manager_id", SQLType.INTEGER, Nullability.NULLABLE);
        private final Column<Integer> SCORE = column("score", SQLType.INTEGER, Nullability.NULLABLE);
        private final Column<Boolean> ACTIVE = column("active", SQLType.BOOLEAN, Nullability.NOT_NULL);

        private EmployeesTable() {
            super("employees", "hr");
        }
    }
}
