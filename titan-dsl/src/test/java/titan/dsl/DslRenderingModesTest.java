package titan.dsl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3.2 (audit D-1/D-2/D-4): both rendering modes — parameterized placeholders with ordered
 * bind values, and per-SQLType dialect-aware literal text.
 */
class DslRenderingModesTest {

    private static final EventsTable EVENTS = new EventsTable();

    // ---------------------------------------------------------------- parameterized mode

    @Test
    void selectRendersPlaceholdersWithOrderedParameters() {
        ParameterizedSql rendered = DSL.select(EVENTS.ID)
                .from(EVENTS)
                .where(EVENTS.NAME.eq("ada").and(EVENTS.ID.between(1, 9)))
                .render(SqlDialect.POSTGRESQL);

        assertEquals(
                "SELECT id FROM public.events WHERE (name = ?) AND (id BETWEEN ? AND ?)",
                rendered.sql());
        assertEquals(
                List.of(
                        BindValue.of("ada", SQLType.VARCHAR),
                        BindValue.of(1, SQLType.INTEGER),
                        BindValue.of(9, SQLType.INTEGER)),
                rendered.parameters());
    }

    @Test
    void inListAndSubqueryParametersKeepDocumentOrder() {
        SelectBuilder subquery = DSL.select(EVENTS.ID).from(EVENTS).where(EVENTS.NAME.eq("sub"));
        ParameterizedSql rendered = DSL.select(EVENTS.ID)
                .from(EVENTS)
                .where(EVENTS.ID.in(4, 5).and(EVENTS.ID.in(subquery)))
                .render(SqlDialect.POSTGRESQL);

        assertEquals(
                "SELECT id FROM public.events WHERE (id IN (?, ?)) AND "
                        + "(id IN (SELECT id FROM public.events WHERE name = ?))",
                rendered.sql());
        assertEquals(List.of(4, 5, "sub"),
                rendered.parameters().stream().map(BindValue::value).toList());
    }

    @Test
    void insertUpdateDeleteRenderPlaceholders() {
        ParameterizedSql insert = DSL.insertInto(EVENTS)
                .set(EVENTS.NAME, "ada")
                .set(EVENTS.OCCURRED_ON, LocalDate.of(2024, 6, 9))
                .onConflict(EVENTS.ID)
                .doUpdate()
                .set(EVENTS.NAME, "updated")
                .render(SqlDialect.POSTGRESQL);
        assertEquals(
                "INSERT INTO public.events (name, occurred_on) VALUES (?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET name = ?",
                insert.sql());
        assertEquals(List.of("ada", LocalDate.of(2024, 6, 9), "updated"),
                insert.parameters().stream().map(BindValue::value).toList());

        ParameterizedSql update = DSL.update(EVENTS)
                .set(EVENTS.NAME, "x")
                .where(EVENTS.ID.eq(7))
                .render(SqlDialect.MYSQL);
        assertEquals("UPDATE public.events SET name = ? WHERE id = ?", update.sql());
        assertEquals(List.of("x", 7), update.parameters().stream().map(BindValue::value).toList());

        ParameterizedSql delete = DSL.deleteFrom(EVENTS)
                .where(EVENTS.NAME.eq("x"))
                .render(SqlDialect.MYSQL);
        assertEquals("DELETE FROM public.events WHERE name = ?", delete.sql());
        assertEquals(List.of("x"), delete.parameters().stream().map(BindValue::value).toList());
    }

    @Test
    void batchValuesRenderOnePlaceholderPerValue() {
        ParameterizedSql rendered = DSL.insertInto(EVENTS)
                .columns(EVENTS.NAME, EVENTS.OCCURRED_ON)
                .values("a", LocalDate.of(2024, 1, 1))
                .values("b", LocalDate.of(2024, 2, 2))
                .render(SqlDialect.POSTGRESQL);

        assertEquals("INSERT INTO public.events (name, occurred_on) VALUES (?, ?), (?, ?)", rendered.sql());
        assertEquals(List.of("a", LocalDate.of(2024, 1, 1), "b", LocalDate.of(2024, 2, 2)),
                rendered.parameters().stream().map(BindValue::value).toList());
        assertEquals(SQLType.DATE, rendered.parameters().get(1).sqlType());
    }

    @Test
    void parameterizedBindValuesCarryDeclaredSqlTypes() {
        ParameterizedSql rendered = DSL.select(EVENTS.ID)
                .from(EVENTS)
                .where(EVENTS.OCCURRED_ON.eq(LocalDate.of(2024, 6, 9)))
                .render(SqlDialect.POSTGRESQL);

        assertEquals(1, rendered.parameters().size());
        assertEquals(SQLType.DATE, rendered.parameters().get(0).sqlType());
    }

    // ---------------------------------------------------------------- literal mode, per SQLType

    @Test
    void localDateRendersAsQuotedDateLiteralNotBareArithmetic() {
        // Audit D-2: previously rendered unquoted as 2024-06-09, which MySQL evaluates as
        // integer subtraction (2009) and silently matches wrong rows.
        String sql = DSL.select(EVENTS.ID)
                .from(EVENTS)
                .where(EVENTS.OCCURRED_ON.eq(LocalDate.of(2024, 6, 9)))
                .toSql(SqlDialect.MYSQL);

        assertEquals("SELECT id FROM public.events WHERE occurred_on = DATE '2024-06-09'", sql);
    }

    @Test
    void temporalLiteralsRenderWithTypePrefixes() {
        assertEquals("occurred_on = DATE '2024-06-09'",
                EVENTS.OCCURRED_ON.eq(LocalDate.of(2024, 6, 9)).sql(SqlDialect.POSTGRESQL));
        assertEquals("created_at = TIMESTAMP '2024-06-09 10:15:30'",
                EVENTS.CREATED_AT.eq(LocalDateTime.of(2024, 6, 9, 10, 15, 30)).sql(SqlDialect.POSTGRESQL));
        assertEquals("start_time = TIME '10:15:30'",
                EVENTS.START_TIME.eq(LocalTime.of(10, 15, 30)).sql(SqlDialect.POSTGRESQL));
        assertEquals("created_at = TIMESTAMP '2024-06-09 10:15:30.123456'",
                EVENTS.CREATED_AT.eq(LocalDateTime.of(2024, 6, 9, 10, 15, 30, 123_456_000))
                        .sql(SqlDialect.POSTGRESQL));
    }

    @Test
    void instantRendersAsUtcTimestampPerDialect() {
        Instant instant = Instant.parse("2024-06-09T10:15:30Z");
        assertEquals("recorded_at = TIMESTAMP WITH TIME ZONE '2024-06-09 10:15:30+00'",
                EVENTS.RECORDED_AT.eq(instant).sql(SqlDialect.POSTGRESQL));
        assertEquals("recorded_at = TIMESTAMP '2024-06-09 10:15:30'",
                EVENTS.RECORDED_AT.eq(instant).sql(SqlDialect.MYSQL));
    }

    @Test
    void uuidRendersQuoted() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertEquals("external_id = '123e4567-e89b-12d3-a456-426614174000'",
                EVENTS.EXTERNAL_ID.eq(uuid).sql(SqlDialect.POSTGRESQL));
        assertEquals("external_id = '123e4567-e89b-12d3-a456-426614174000'",
                EVENTS.EXTERNAL_ID.eq(uuid).sql(SqlDialect.MYSQL));
    }

    @Test
    void uuidParameterizedBindCarriesUuidValueAndSqlType() {
        // In parameterized mode the UUID is bound (not spliced): the BindValue carries the UUID value
        // and SQLType.UUID, which the JDBC runtime then binds per-dialect (native on PG, string on MySQL).
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        ParameterizedSql rendered = DSL.select(EVENTS.ID)
                .from(EVENTS)
                .where(EVENTS.EXTERNAL_ID.eq(uuid))
                .render(SqlDialect.MYSQL);

        assertEquals(1, rendered.parameters().size());
        assertEquals(SQLType.UUID, rendered.parameters().get(0).sqlType());
        assertEquals(uuid, rendered.parameters().get(0).value());
    }

    @Test
    void numericLiteralsPassThrough() {
        assertEquals("amount = 12.50",
                EVENTS.AMOUNT.eq(new BigDecimal("12.50")).sql(SqlDialect.MYSQL));
        assertEquals("id = 42", EVENTS.ID.eq(42).sql(SqlDialect.MYSQL));
    }

    @Test
    void stringEscapingIsPerDialect() {
        assertEquals("name = 'O''Brien'", EVENTS.NAME.eq("O'Brien").sql(SqlDialect.POSTGRESQL));
        assertEquals("name = 'O''Brien'", EVENTS.NAME.eq("O'Brien").sql(SqlDialect.MYSQL));
        // PostgreSQL: backslash is a literal character (standard_conforming_strings).
        assertEquals("name = 'a\\b'", EVENTS.NAME.eq("a\\b").sql(SqlDialect.POSTGRESQL));
        // MySQL: backslash must be doubled.
        assertEquals("name = 'a\\\\b'", EVENTS.NAME.eq("a\\b").sql(SqlDialect.MYSQL));
    }

    @Test
    void mysqlValueEndingInBackslashCanNoLongerEscapeTheClosingQuote() {
        // Audit D-1 injection regression: with quote-only escaping, a value ending in a
        // backslash rendered as '...\' on MySQL, escaping the closing quote so attacker-
        // controlled text after the literal became SQL.
        String sql = DSL.select(EVENTS.ID)
                .from(EVENTS)
                .where(EVENTS.NAME.eq("payload\\' OR '1'='1"))
                .toSql(SqlDialect.MYSQL);

        assertEquals("SELECT id FROM public.events WHERE name = 'payload\\\\'' OR ''1''=''1'", sql);
        assertTrue(sql.endsWith("'"), "literal must stay closed");
    }

    @Test
    void insertLiteralRenderingIsDialectAware() {
        InsertBuilder insert = DSL.insertInto(EVENTS)
                .set(EVENTS.NAME, "a\\")
                .set(EVENTS.OCCURRED_ON, LocalDate.of(2024, 6, 9));

        assertEquals(
                "INSERT INTO public.events (name, occurred_on) VALUES ('a\\', DATE '2024-06-09')",
                insert.toSql(SqlDialect.POSTGRESQL));
        assertEquals(
                "INSERT INTO public.events (name, occurred_on) VALUES ('a\\\\', DATE '2024-06-09')",
                insert.toSql(SqlDialect.MYSQL));
    }

    @Test
    void parameterlessToSqlKeepsDefaultDialectBehaviour() {
        InsertBuilder insert = DSL.insertInto(EVENTS).set(EVENTS.NAME, "ada");
        assertEquals(insert.toSql(SqlDialect.POSTGRESQL), insert.toSql());

        SelectBuilder select = DSL.select(EVENTS.ID).from(EVENTS).where(EVENTS.ID.eq(1));
        assertEquals(select.toSql(SqlDialect.POSTGRESQL), select.toSql());
    }

    // ---------------------------------------------------------------- null handling

    @Test
    void nullEqualityStillRendersIsNullChecks() {
        assertEquals("name IS NULL", EVENTS.NAME.eq(null).sql());
        assertEquals("name IS NOT NULL", EVENTS.NAME.ne(null).sql());
    }

    @Test
    void nullInequalityOperandsAreRejected() {
        assertThrowsNullOperand(() -> EVENTS.ID.lt(null), "lt");
        assertThrowsNullOperand(() -> EVENTS.ID.gt(null), "gt");
        assertThrowsNullOperand(() -> EVENTS.ID.le(null), "le");
        assertThrowsNullOperand(() -> EVENTS.ID.ge(null), "ge");
        assertThrowsNullOperand(() -> EVENTS.ID.between(null, 9), "between");
        assertThrowsNullOperand(() -> EVENTS.ID.between(1, null), "between");
        assertThrowsNullOperand(() -> EVENTS.NAME.like(null), "like");
        assertThrowsNullOperand(() -> EVENTS.ID.in(1, null, 3), "in");
    }

    @Test
    void nullAssignmentsRenderNullLiteralAndBindNull() {
        InsertBuilder insert = DSL.insertInto(EVENTS).set(EVENTS.NAME, null);
        assertEquals("INSERT INTO public.events (name) VALUES (NULL)", insert.toSql(SqlDialect.POSTGRESQL));

        ParameterizedSql rendered = insert.render(SqlDialect.POSTGRESQL);
        assertEquals("INSERT INTO public.events (name) VALUES (?)", rendered.sql());
        assertEquals(1, rendered.parameters().size());
        assertEquals(null, rendered.parameters().get(0).value());
    }

    private static void assertThrowsNullOperand(org.junit.jupiter.api.function.Executable executable, String operator) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, executable);
        assertTrue(ex.getMessage().contains(operator + "(...)"),
                "expected message to mention " + operator + " but was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("isNull()"),
                "expected message to suggest isNull(): " + ex.getMessage());
    }

    private static final class EventsTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> NAME = column("name", SQLType.VARCHAR, Nullability.NULLABLE);
        private final Column<LocalDate> OCCURRED_ON = column("occurred_on", SQLType.DATE, Nullability.NULLABLE);
        private final Column<LocalDateTime> CREATED_AT = column("created_at", SQLType.TIMESTAMP, Nullability.NULLABLE);
        private final Column<LocalTime> START_TIME = column("start_time", SQLType.TIME, Nullability.NULLABLE);
        private final Column<Instant> RECORDED_AT = column("recorded_at", SQLType.TIMESTAMP_TZ, Nullability.NULLABLE);
        private final Column<UUID> EXTERNAL_ID = column("external_id", SQLType.UUID, Nullability.NULLABLE);
        private final Column<BigDecimal> AMOUNT = column("amount", SQLType.NUMERIC, Nullability.NULLABLE);

        private EventsTable() {
            super("events", "public");
        }
    }
}
