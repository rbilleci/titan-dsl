package titan.dsl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

/** Java-to-SQL type compatibility shared by projection mapping and automatic-filter checks. */
final class SqlTypes {

    private SqlTypes() {
    }

    static boolean isCompatible(Class<?> javaType, SQLType sqlType) {
        Class<?> boxed = box(javaType);
        return switch (sqlType) {
            case INTEGER -> boxed == Integer.class;
            case BIGINT -> boxed == Long.class;
            case SMALLINT -> boxed == Short.class;
            case TINYINT -> boxed == Byte.class || boxed == Boolean.class;
            case BOOLEAN -> boxed == Boolean.class;
            case REAL -> boxed == Float.class;
            case DOUBLE -> boxed == Double.class;
            case DECIMAL, NUMERIC -> boxed == BigDecimal.class;
            case VARCHAR, TEXT, CHAR, ENUM -> boxed == String.class;
            case DATE -> boxed == LocalDate.class;
            case TIME -> boxed == LocalTime.class;
            case TIMESTAMP -> boxed == LocalDateTime.class;
            case TIMESTAMP_TZ -> boxed == Instant.class
                    || boxed == ZonedDateTime.class
                    || boxed == OffsetDateTime.class;
            case UUID -> boxed == UUID.class;
            case JSON, UNKNOWN -> true;
        };
    }

    /** Whether a runtime value may be bound to a column of the given type; enums bind by name. */
    static boolean acceptsValue(Object value, SQLType sqlType) {
        return isCompatible(value.getClass(), sqlType) || (sqlType == SQLType.ENUM && value instanceof Enum<?>);
    }

    /**
     * Value equality for scope checks. Numbers compare by magnitude so a caller's {@code 42}
     * matches a scope's {@code 42L}; enums compare by name.
     */
    static boolean sameValue(Object left, Object right, SQLType sqlType) {
        if (left instanceof Number a && right instanceof Number b) {
            return switch (sqlType) {
                case TINYINT, SMALLINT, INTEGER, BIGINT -> a.longValue() == b.longValue();
                default -> new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
            };
        }
        return Objects.equals(enumName(left), enumName(right));
    }

    private static Object enumName(Object value) {
        return value instanceof Enum<?> constant ? constant.name() : value;
    }

    static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }
}
