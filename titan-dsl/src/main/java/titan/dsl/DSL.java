package titan.dsl;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Function;

public final class DSL {

    static final String RECURSIVE_SELF_REFERENCE_SQL = "__titan_recursive_self_reference__";

    private DSL() {
    }

    /** Creates a reusable, connection-free query context with an explicit SQL dialect. */
    public static DSLContext using(SqlDialect dialect) {
        return new DSLContext(dialect);
    }

    public static CteNameStep name(String name) {
        return new CteNameStep(name);
    }

    public static WithBuilder with(CommonTableExpression<?>... expressions) {
        return new WithBuilder(false, expressions);
    }

    public static WithBuilder withRecursive(CommonTableExpression<?>... expressions) {
        return new WithBuilder(true, expressions);
    }

    /**
     * Defines an inline view (emitted as a CTE). The view name is derived deterministically from
     * the view's query content (audit D-6): the previous process-global counter made names depend
     * on JVM-wide call ordering — nondeterministic output for a transpiler. Identical query
     * bodies produce identical names (interchangeable by construction); different bodies cannot
     * collide silently because {@code SelectBuilder} rejects same-name/different-body CTE
     * registrations.
     */
    public static <R> InlineView<R> defineInlineView(SelectBuilder selectBuilder) {
        if (selectBuilder == null) {
            throw new IllegalArgumentException("selectBuilder must not be null");
        }
        String querySql = selectBuilder.toSql();
        return new InlineView<>("inline_view_" + contentHash(querySql), querySql);
    }

    /**
     * Defines a shared view descriptor. The name is derived deterministically from the view's
     * query content (audit D-6), replacing the process-global counter.
     */
    public static <R> ViewDef<R> defineView(SelectBuilder selectBuilder) {
        if (selectBuilder == null) {
            throw new IllegalArgumentException("selectBuilder must not be null");
        }
        String querySql = selectBuilder.toSql();
        return new ViewDef<>("view_def_" + contentHash(querySql), "public", querySql);
    }

    /** First 12 hex characters of the SHA-256 of the query text; stable across JVM runs. */
    private static String contentHash(String querySql) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(querySql.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(12);
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static SelectBuilder select(Column<?>... columns) {
        return new SelectBuilder(columns);
    }

    public static SelectBuilder selectFrom(TableLike<?> table) {
        return selectFrom(null, table);
    }

    static SelectBuilder selectFrom(SqlDialect dialect, TableLike<?> table) {
        return selectFrom(dialect, null, table);
    }

    static SelectBuilder selectFrom(SqlDialect dialect, QueryFilters filters, TableLike<?> table) {
        if (table == null) {
            throw new IllegalArgumentException("table must not be null");
        }
        List<Column<?>> columns = extractPublicColumns(table);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("selectFrom requires at least one public Column field on table "
                    + table.getClass().getName());
        }
        return new SelectBuilder(dialect, filters, columns.toArray(Column[]::new)).from(table);
    }

    public static <T1> SelectBuilder1<T1> select(Column<T1> column1) {
        return new SelectBuilder1<>(column1);
    }

    public static <T1, T2> SelectBuilder2<T1, T2> select(Column<T1> column1, Column<T2> column2) {
        return new SelectBuilder2<>(column1, column2);
    }

    public static <T1, T2, T3> SelectBuilder3<T1, T2, T3> select(
            Column<T1> column1,
            Column<T2> column2,
            Column<T3> column3) {
        return new SelectBuilder3<>(column1, column2, column3);
    }

    public static <T1, T2, T3, T4> SelectBuilder4<T1, T2, T3, T4> select(
            Column<T1> column1,
            Column<T2> column2,
            Column<T3> column3,
            Column<T4> column4) {
        return new SelectBuilder4<>(column1, column2, column3, column4);
    }

    public static <T1, T2, T3, T4, T5> SelectBuilder5<T1, T2, T3, T4, T5> select(
            Column<T1> column1,
            Column<T2> column2,
            Column<T3> column3,
            Column<T4> column4,
            Column<T5> column5) {
        return new SelectBuilder5<>(column1, column2, column3, column4, column5);
    }

    public static <T1, T2, T3, T4, T5, T6> SelectBuilder6<T1, T2, T3, T4, T5, T6> select(
            Column<T1> column1,
            Column<T2> column2,
            Column<T3> column3,
            Column<T4> column4,
            Column<T5> column5,
            Column<T6> column6) {
        return new SelectBuilder6<>(column1, column2, column3, column4, column5, column6);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> SelectBuilder7<T1, T2, T3, T4, T5, T6, T7> select(
            Column<T1> column1,
            Column<T2> column2,
            Column<T3> column3,
            Column<T4> column4,
            Column<T5> column5,
            Column<T6> column6,
            Column<T7> column7) {
        return new SelectBuilder7<>(column1, column2, column3, column4, column5, column6, column7);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> SelectBuilder8<T1, T2, T3, T4, T5, T6, T7, T8> select(
            Column<T1> column1,
            Column<T2> column2,
            Column<T3> column3,
            Column<T4> column4,
            Column<T5> column5,
            Column<T6> column6,
            Column<T7> column7,
            Column<T8> column8) {
        return new SelectBuilder8<>(column1, column2, column3, column4, column5, column6, column7, column8);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> SelectBuilder9<T1, T2, T3, T4, T5, T6, T7, T8, T9> select(
            Column<T1> column1,
            Column<T2> column2,
            Column<T3> column3,
            Column<T4> column4,
            Column<T5> column5,
            Column<T6> column6,
            Column<T7> column7,
            Column<T8> column8,
            Column<T9> column9) {
        return new SelectBuilder9<>(column1, column2, column3, column4, column5, column6, column7, column8, column9);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> SelectBuilder10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> select(
            Column<T1> column1,
            Column<T2> column2,
            Column<T3> column3,
            Column<T4> column4,
            Column<T5> column5,
            Column<T6> column6,
            Column<T7> column7,
            Column<T8> column8,
            Column<T9> column9,
            Column<T10> column10) {
        return new SelectBuilder10<>(
                column1,
                column2,
                column3,
                column4,
                column5,
                column6,
                column7,
                column8,
                column9,
                column10);
    }

    public static Column<Long> count() {
        return new Column<>("COUNT(*)", SQLType.BIGINT, Nullability.NOT_NULL);
    }

    public static <N extends Number> Column<N> sum(Column<N> column) {
        return aggregate("SUM", column, column.sqlType());
    }

    public static <N extends Number> Column<Double> avg(Column<N> column) {
        return aggregate("AVG", column, SQLType.DOUBLE);
    }

    public static <T> Column<T> min(Column<T> column) {
        return aggregate("MIN", column, column.sqlType());
    }

    public static <T> Column<T> max(Column<T> column) {
        return aggregate("MAX", column, column.sqlType());
    }

    public static GroupingSet set(Column<?>... columns) {
        if (columns == null) {
            throw new IllegalArgumentException("columns must not be null");
        }
        if (columns.length == 0) {
            return new GroupingSet("()");
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (Column<?> column : columns) {
            if (column == null) {
                throw new IllegalArgumentException("columns must not contain nulls");
            }
            joiner.add(column.name());
        }
        return new GroupingSet("(" + joiner + ")");
    }

    public static GroupByExpression groupingSets(GroupingSet... sets) {
        if (sets == null || sets.length == 0) {
            throw new IllegalArgumentException("grouping sets must not be empty");
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (GroupingSet set : sets) {
            if (set == null) {
                throw new IllegalArgumentException("grouping sets must not contain nulls");
            }
            joiner.add(set.sql());
        }
        return new GroupByExpression("GROUPING SETS (" + joiner + ")");
    }

    public static GroupByExpression rollup(Column<?>... columns) {
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("rollup columns must not be empty");
        }
        return new GroupByExpression("ROLLUP (" + joinColumnNames(columns) + ")");
    }

    public static GroupByExpression cube(Column<?>... columns) {
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("cube columns must not be empty");
        }
        return new GroupByExpression("CUBE (" + joinColumnNames(columns) + ")");
    }

    public static WindowSpecificationBuilder partitionBy(Column<?>... columns) {
        return new WindowSpecificationBuilder().partitionBy(columns);
    }

    public static WindowSpecificationBuilder orderBy(SortField... fields) {
        return new WindowSpecificationBuilder().orderBy(fields);
    }

    public static WindowFrameBoundary unboundedPreceding() {
        return new WindowFrameBoundary("UNBOUNDED PRECEDING");
    }

    public static WindowFrameBoundary unboundedFollowing() {
        return new WindowFrameBoundary("UNBOUNDED FOLLOWING");
    }

    public static WindowFrameBoundary currentRow() {
        return new WindowFrameBoundary("CURRENT ROW");
    }

    public static WindowFrameBoundary preceding(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("preceding value must be >= 0");
        }
        return new WindowFrameBoundary(value + " PRECEDING");
    }

    public static WindowFrameBoundary following(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("following value must be >= 0");
        }
        return new WindowFrameBoundary(value + " FOLLOWING");
    }

    public static WindowFunction<Long> rowNumber() {
        return new WindowFunction<>("ROW_NUMBER()", SQLType.BIGINT, Nullability.NOT_NULL);
    }

    public static WindowFunction<Long> rank() {
        return new WindowFunction<>("RANK()", SQLType.BIGINT, Nullability.NOT_NULL);
    }

    public static WindowFunction<Long> denseRank() {
        return new WindowFunction<>("DENSE_RANK()", SQLType.BIGINT, Nullability.NOT_NULL);
    }

    public static WindowFunction<Integer> ntile(int buckets) {
        if (buckets <= 0) {
            throw new IllegalArgumentException("buckets must be > 0");
        }
        return new WindowFunction<>("NTILE(" + buckets + ")", SQLType.INTEGER, Nullability.NOT_NULL);
    }

    public static <T> WindowFunction<T> lag(Column<T> column) {
        return lag(column, 1);
    }

    public static <T> WindowFunction<T> lag(Column<T> column, int offset) {
        if (column == null) {
            throw new IllegalArgumentException("column must not be null");
        }
        if (offset <= 0) {
            throw new IllegalArgumentException("offset must be > 0");
        }
        return new WindowFunction<>("LAG(" + column.name() + ", " + offset + ")", column.sqlType(), Nullability.NULLABLE);
    }

    public static <T> WindowFunction<T> lead(Column<T> column) {
        return lead(column, 1);
    }

    public static <T> WindowFunction<T> lead(Column<T> column, int offset) {
        if (column == null) {
            throw new IllegalArgumentException("column must not be null");
        }
        if (offset <= 0) {
            throw new IllegalArgumentException("offset must be > 0");
        }
        return new WindowFunction<>("LEAD(" + column.name() + ", " + offset + ")", column.sqlType(), Nullability.NULLABLE);
    }

    public static <T> WindowFunction<T> firstValue(Column<T> column) {
        if (column == null) {
            throw new IllegalArgumentException("column must not be null");
        }
        return new WindowFunction<>("FIRST_VALUE(" + column.name() + ")", column.sqlType(), Nullability.NULLABLE);
    }

    public static <T> WindowFunction<T> lastValue(Column<T> column) {
        if (column == null) {
            throw new IllegalArgumentException("column must not be null");
        }
        return new WindowFunction<>("LAST_VALUE(" + column.name() + ")", column.sqlType(), Nullability.NULLABLE);
    }

    public static <T> WindowFunction<T> nthValue(Column<T> column, int nth) {
        if (column == null) {
            throw new IllegalArgumentException("column must not be null");
        }
        if (nth <= 0) {
            throw new IllegalArgumentException("nth must be > 0");
        }
        return new WindowFunction<>("NTH_VALUE(" + column.name() + ", " + nth + ")", column.sqlType(), Nullability.NULLABLE);
    }

    public static InsertBuilder insertInto(Table<?> table) {
        return new InsertBuilder(table);
    }

    public static UpdateBuilder update(Table<?> table) {
        return new UpdateBuilder(table);
    }

    public static DeleteBuilder deleteFrom(Table<?> table) {
        return new DeleteBuilder(table);
    }

    public static <R> TriggerRowAccessor<R> newRow() {
        throw new UnsupportedOperationException("newRow() is only available inside @Trigger methods");
    }

    public static <R> ReadOnlyTriggerRowAccessor<R> oldRow() {
        throw new UnsupportedOperationException("oldRow() is only available inside @Trigger methods");
    }

    public static void abortWithError(String message) {
        throw new IllegalStateException(message == null ? "Trigger aborted" : message);
    }

    /**
     * Starts a searched CASE expression (audit D-7):
     * {@code when(cond, result).when(cond2, result2).otherwise(fallback)}.
     */
    public static <T> CaseBuilder<T> when(Condition condition, T result) {
        if (condition == null) {
            throw new IllegalArgumentException("condition must not be null");
        }
        return new CaseBuilder<>(condition, result);
    }

    public static Condition exists(SelectBuilder subquery) {
        if (subquery == null) {
            throw new IllegalArgumentException("subquery must not be null");
        }
        return Condition.fromFragment(SqlFragment.of("EXISTS (", subquery, ")"));
    }

    public static Condition notExists(SelectBuilder subquery) {
        return exists(subquery).not();
    }

    public static <T> Column<T> scalar(SelectBuilder1<T> subquery) {
        if (subquery == null) {
            throw new IllegalArgumentException("subquery must not be null");
        }
        return new Column<>("(" + subquery.toSql() + ")", subquery.projectionSqlType(0), Nullability.NULLABLE);
    }

    public static <T> Column<T> scalar(SelectBuilder subquery, SQLType sqlType) {
        if (subquery == null) {
            throw new IllegalArgumentException("subquery must not be null");
        }
        if (sqlType == null) {
            throw new IllegalArgumentException("sqlType must not be null");
        }
        if (subquery.projectionSize() != 1) {
            throw new IllegalArgumentException("scalar subquery must project exactly one column");
        }
        SQLType projectedType = subquery.projectionSqlType(0);
        if (projectedType != sqlType) {
            throw new IllegalArgumentException("scalar subquery projection type must match declared sqlType");
        }
        return new Column<>("(" + subquery.toSql() + ")", sqlType, Nullability.NULLABLE);
    }

    public static <T> Column<T> field(String name, Class<T> javaType) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("field name must not be blank");
        }
        if (javaType == null) {
            throw new IllegalArgumentException("javaType must not be null");
        }
        return new Column<>(name, sqlTypeFor(javaType), Nullability.NULLABLE);
    }

    public static <T> Column<T> field(String name, SQLType sqlType) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("field name must not be blank");
        }
        if (sqlType == null) {
            throw new IllegalArgumentException("sqlType must not be null");
        }
        return new Column<>(name, sqlType, Nullability.NULLABLE);
    }

    private static List<Column<?>> extractPublicColumns(TableLike<?> table) {
        List<ColumnWithName> discovered = new ArrayList<>();
        for (Field field : table.getClass().getFields()) {
            if (!Modifier.isPublic(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (!Column.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                Object value = field.get(table);
                if (value instanceof Column<?> column) {
                    discovered.add(new ColumnWithName(field.getName(), column));
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "Unable to access column field '" + field.getName() + "' on " + table.getClass().getName(),
                        e);
            }
        }
        discovered.sort(Comparator.comparing(ColumnWithName::fieldName));
        List<Column<?>> columns = new ArrayList<>(discovered.size());
        for (ColumnWithName columnWithName : discovered) {
            columns.add(columnWithName.column());
        }
        return columns;
    }

    private record ColumnWithName(String fieldName, Column<?> column) {
    }

    private static <T> Column<T> aggregate(String functionName, Column<?> column, SQLType sqlType) {
        if (column == null) {
            throw new IllegalArgumentException("column must not be null");
        }
        return new Column<>(functionName + "(" + column.name() + ")", sqlType, Nullability.NULLABLE);
    }

    static SQLType sqlTypeFor(Class<?> javaType) {
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

    private static String joinColumnNames(Column<?>... columns) {
        StringJoiner joiner = new StringJoiner(", ");
        for (Column<?> column : columns) {
            if (column == null) {
                throw new IllegalArgumentException("columns must not contain nulls");
            }
            joiner.add(column.name());
        }
        return joiner.toString();
    }

    public static final class CteNameStep {
        private final String name;
        private final String[] fieldNames;
        private final boolean structured;

        private CteNameStep(String name) {
            this(name, false);
        }

        CteNameStep(String name, boolean structured) {
            this(name, new String[0], structured);
        }

        private CteNameStep(String name, String[] fieldNames, boolean structured) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("CTE name must not be blank");
            }
            this.name = name;
            this.fieldNames = fieldNames;
            this.structured = structured;
        }

        public CteNameStep fields(String... fieldNames) {
            if (fieldNames == null || fieldNames.length == 0) {
                throw new IllegalArgumentException("fieldNames must contain at least one field");
            }
            String[] copy = Arrays.copyOf(fieldNames, fieldNames.length);
            for (String fieldName : copy) {
                if (fieldName == null || fieldName.isBlank()) {
                    throw new IllegalArgumentException("fieldNames must not contain blank values");
                }
            }
            return new CteNameStep(name, copy, structured);
        }

        public <R> CommonTableExpression<R> as(SelectBuilder selectBuilder) {
            if (selectBuilder == null) {
                throw new IllegalArgumentException("selectBuilder must not be null");
            }
            return capture(selectBuilder);
        }

        /**
         * Builds a recursive CTE query where the CTE can reference itself while constructing
         * the UNION/UNION ALL branch.
         */
        public <R> CommonTableExpression<R> asRecursive(Function<CommonTableExpression<R>, SelectBuilder> builder) {
            if (builder == null) {
                throw new IllegalArgumentException("builder must not be null");
            }
            CommonTableExpression<R> selfReference = new CommonTableExpression<>(name, RECURSIVE_SELF_REFERENCE_SQL, fieldNames);
            SelectBuilder selectBuilder = builder.apply(selfReference);
            if (selectBuilder == null) {
                throw new IllegalArgumentException("builder must return a SelectBuilder");
            }
            return capture(selectBuilder);
        }

        private <R> CommonTableExpression<R> capture(SelectBuilder query) {
            return structured ? new CommonTableExpression<>(name, query, fieldNames)
                    : new CommonTableExpression<>(name, query.toSql(), fieldNames);
        }

        public <R> CommonTableExpression<R> asSql(String sql) {
            if (sql == null || sql.isBlank()) {
                throw new IllegalArgumentException("sql must not be blank");
            }
            return new CommonTableExpression<>(name, sql, fieldNames);
        }
    }
}
