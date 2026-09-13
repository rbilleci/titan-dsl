package titan.dsl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Minimal non-recursive common table expression descriptor.
 */
public class CommonTableExpression<R> implements TableLike<R> {

    private final String name;
    private final String querySql;
    private final SelectBuilder query;
    private final String[] declaredFields;

    CommonTableExpression(String name, String querySql) {
        this(name, querySql, new String[0]);
    }

    CommonTableExpression(String name, String querySql, String[] declaredFields) {
        this.name = Objects.requireNonNull(name, "name");
        this.querySql = Objects.requireNonNull(querySql, "querySql");
        this.declaredFields = Objects.requireNonNull(declaredFields, "declaredFields");
        this.query = null;
    }

    CommonTableExpression(String name, SelectBuilder query, String[] declaredFields) {
        this.name = Objects.requireNonNull(name, "name");
        this.query = Objects.requireNonNull(query, "query").snapshot();
        // Canonical text is used only for identity/collision checks, not actual rendering.
        this.querySql = this.query.toSql(SqlDialect.POSTGRESQL);
        this.declaredFields = Objects.requireNonNull(declaredFields, "declaredFields");
    }

    void appendQueryTo(SqlWriter writer) {
        if (query == null) {
            writer.append(querySql);
        } else {
            query.appendTo(writer, true);
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String schema() {
        return null;
    }

    String querySql() {
        return querySql;
    }

    boolean sameDefinition(CommonTableExpression<?> other) {
        if (!querySql.equals(other.querySql)) {
            return false;
        }
        if (query == null || other.query == null) {
            // A literal body must not silently replace a structured body's parameters.
            return query == null && other.query == null;
        }
        for (SqlDialect dialect : SqlDialect.values()) {
            ParameterizedSql left = query.render(dialect);
            ParameterizedSql right = other.query.render(dialect);
            if (!left.sql().equals(right.sql()) || !left.parameters().equals(right.parameters())) {
                return false;
            }
        }
        return true;
    }

    String renderedAlias() {
        if (declaredFields.length == 0) {
            return name;
        }
        StringJoiner fields = new StringJoiner(", ");
        for (String field : declaredFields) {
            fields.add(field);
        }
        return name + " (" + fields + ")";
    }

    public <T> Column<T> field(String fieldName, SQLType sqlType, Nullability nullability) {
        Objects.requireNonNull(fieldName, "fieldName");
        if (fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must not be blank");
        }
        validateDeclaredField(fieldName);
        return new Column<>(name + "." + fieldName, sqlType, nullability);
    }

    public <T> Column<T> field(String fieldName, Class<T> javaType) {
        Objects.requireNonNull(javaType, "javaType");
        return field(fieldName, sqlTypeFor(javaType), Nullability.NULLABLE);
    }

    private void validateDeclaredField(String fieldName) {
        if (declaredFields.length == 0) {
            return;
        }
        for (String declaredField : declaredFields) {
            if (declaredField.equalsIgnoreCase(fieldName)) {
                return;
            }
        }
        throw new IllegalArgumentException("CTE field '" + fieldName + "' is not declared in " + name
                + " (declared: " + String.join(", ", declaredFields) + ")");
    }

    private static SQLType sqlTypeFor(Class<?> javaType) {
        Class<?> boxed = box(javaType);
        if (boxed == Integer.class) {
            return SQLType.INTEGER;
        }
        if (boxed == Long.class) {
            return SQLType.BIGINT;
        }
        if (boxed == Short.class) {
            return SQLType.SMALLINT;
        }
        if (boxed == Byte.class) {
            return SQLType.TINYINT;
        }
        if (boxed == Boolean.class) {
            return SQLType.BOOLEAN;
        }
        if (boxed == Float.class) {
            return SQLType.REAL;
        }
        if (boxed == Double.class) {
            return SQLType.DOUBLE;
        }
        if (boxed == BigDecimal.class) {
            return SQLType.NUMERIC;
        }
        if (boxed == String.class) {
            return SQLType.VARCHAR;
        }
        if (boxed == LocalDate.class) {
            return SQLType.DATE;
        }
        if (boxed == LocalTime.class) {
            return SQLType.TIME;
        }
        if (boxed == LocalDateTime.class) {
            return SQLType.TIMESTAMP;
        }
        if (boxed == Instant.class || boxed == ZonedDateTime.class || boxed == OffsetDateTime.class) {
            return SQLType.TIMESTAMP_TZ;
        }
        if (boxed == UUID.class) {
            return SQLType.UUID;
        }
        return SQLType.UNKNOWN;
    }

    private static Class<?> box(Class<?> type) {
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
        return type;
    }
}
