package titan.dsl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.UUID;

/**
 * Dialect-aware SQL literal rendering (audit finding D-2).
 *
 * <p>Replaces the previous {@code toString()} passthrough: temporals render as quoted ISO strings
 * with {@code DATE}/{@code TIME}/{@code TIMESTAMP} prefixes, UUIDs are quoted, numbers pass
 * through, and string escaping follows the per-dialect rules used by the SQL emitters
 * (quote-doubling on PostgreSQL; quote-doubling plus backslash escaping on MySQL).</p>
 */
final class SqlLiterals {

    private static final DateTimeFormatter LOCAL_DATE_TIME = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral(' ')
            .appendPattern("HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true)
            .toFormatter();

    private static final DateTimeFormatter LOCAL_TIME = new DateTimeFormatterBuilder()
            .appendPattern("HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true)
            .toFormatter();

    private SqlLiterals() {
    }

    static String render(Object value, SQLType sqlType, SqlDialect dialect) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String s) {
            return quote(s, dialect);
        }
        if (value instanceof Character c) {
            return quote(c.toString(), dialect);
        }
        if (value instanceof Boolean b) {
            return b ? "TRUE" : "FALSE";
        }
        if (value instanceof Number n) {
            return renderNumber(n);
        }
        if (value instanceof LocalDate localDate) {
            return "DATE " + quote(DateTimeFormatter.ISO_LOCAL_DATE.format(localDate), dialect);
        }
        if (value instanceof LocalTime localTime) {
            return "TIME " + quote(LOCAL_TIME.format(localTime), dialect);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return "TIMESTAMP " + quote(LOCAL_DATE_TIME.format(localDateTime), dialect);
        }
        if (value instanceof Instant instant) {
            return renderInstant(instant, dialect);
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return renderInstant(offsetDateTime.toInstant(), dialect);
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return renderInstant(zonedDateTime.toInstant(), dialect);
        }
        if (value instanceof UUID uuid) {
            return quote(uuid.toString(), dialect);
        }
        if (value instanceof Enum<?> enumValue) {
            return quote(enumValue.name(), dialect);
        }
        throw new IllegalArgumentException(
                "No SQL literal rendering for value of type " + value.getClass().getName()
                        + " (declared SQL type " + sqlType + ")");
    }

    private static String renderNumber(Number number) {
        if (number instanceof Double d && (d.isNaN() || d.isInfinite())) {
            throw new IllegalArgumentException("Cannot render non-finite double as a SQL literal: " + d);
        }
        if (number instanceof Float f && (f.isNaN() || f.isInfinite())) {
            throw new IllegalArgumentException("Cannot render non-finite float as a SQL literal: " + f);
        }
        if (number instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (number instanceof Integer || number instanceof Long || number instanceof Short
                || number instanceof Byte || number instanceof Double || number instanceof Float
                || number instanceof BigInteger) {
            return number.toString();
        }
        throw new IllegalArgumentException(
                "No SQL literal rendering for numeric type " + number.getClass().getName());
    }

    private static String renderInstant(Instant instant, SqlDialect dialect) {
        LocalDateTime utc = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        if (dialect == SqlDialect.MYSQL) {
            // MySQL has no TIMESTAMP WITH TIME ZONE literal; render the UTC instant.
            return "TIMESTAMP " + quote(LOCAL_DATE_TIME.format(utc), dialect);
        }
        return "TIMESTAMP WITH TIME ZONE " + quote(LOCAL_DATE_TIME.format(utc) + "+00", dialect);
    }

    private static String quote(String text, SqlDialect dialect) {
        return "'" + escape(text, dialect) + "'";
    }

    /**
     * Per-dialect string escaping, consistent with the emitters' escape rules:
     * PostgreSQL doubles single quotes; MySQL additionally escapes backslashes so a value
     * ending in a backslash can no longer escape the closing quote (audit finding D-1).
     */
    static String escape(String text, SqlDialect dialect) {
        if (dialect == SqlDialect.MYSQL) {
            return text.replace("\\", "\\\\").replace("'", "''");
        }
        return text.replace("'", "''");
    }
}
