package titan.dsl;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Minimal fluent Select builder for the P1.3 baseline.
 */
public class SelectBuilder {

    private final SqlDialect configuredDialect;

    private final List<Column<?>> columns;
    private boolean distinct;
    private boolean withRecursive;
    private final List<CommonTableExpression<?>> commonTableExpressions = new ArrayList<>();
    private TableLike<?> from;
    private final List<SqlFragment> joins = new ArrayList<>();
    private Condition where;
    private final List<String> groupBy = new ArrayList<>();
    private Condition having;
    private final List<SortField> orderBy = new ArrayList<>();
    private final List<SetOperation> setOperations = new ArrayList<>();
    private Integer limit;
    private Integer offset;
    private LockMode lockMode;
    private boolean skipLocked;
    private boolean noWait;

    SelectBuilder(Column<?>... columns) {
        this(null, columns);
    }

    SelectBuilder(SqlDialect dialect, Column<?>... columns) {
        this.configuredDialect = dialect;
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("select requires at least one column");
        }
        this.columns = Arrays.asList(columns);
    }

    /**
     * Snapshot copy used when this builder is captured as a set-operation operand (audit D-6):
     * the operand's clause state is frozen at composition time, so mutating the original builder
     * afterwards no longer changes the composed query's rendered SQL.
     */
    private SelectBuilder(SelectBuilder source) {
        this.configuredDialect = source.configuredDialect;
        this.columns = source.columns;
        this.distinct = source.distinct;
        this.withRecursive = source.withRecursive;
        this.commonTableExpressions.addAll(source.commonTableExpressions);
        this.from = source.from;
        this.joins.addAll(source.joins);
        this.where = source.where;
        this.groupBy.addAll(source.groupBy);
        this.having = source.having;
        this.orderBy.addAll(source.orderBy);
        this.setOperations.addAll(source.setOperations);
        this.limit = source.limit;
        this.offset = source.offset;
        this.lockMode = source.lockMode;
        this.skipLocked = source.skipLocked;
        this.noWait = source.noWait;
    }

    SelectBuilder snapshot() {
        return new SelectBuilder(this);
    }

    SelectBuilder with(CommonTableExpression<?>... expressions) {
        return with(false, expressions);
    }

    SelectBuilder with(boolean recursive, CommonTableExpression<?>... expressions) {
        if (expressions == null || expressions.length == 0) {
            return this;
        }
        // Repeat calls must not downgrade an earlier recursive registration (audit D-6).
        this.withRecursive = this.withRecursive || recursive;
        this.commonTableExpressions.addAll(Arrays.asList(expressions));
        return this;
    }

    /** Marks the projection {@code SELECT DISTINCT} (audit D-7). */
    public SelectBuilder distinct() {
        this.distinct = true;
        return this;
    }

    public SelectBuilder from(TableLike<?> table) {
        this.from = Objects.requireNonNull(table, "table");
        registerInlineCte(table);
        return this;
    }

    public JoinStep join(TableLike<?> table) {
        ensureFromBeforeJoin();
        TableLike<?> validated = Objects.requireNonNull(table, "table");
        registerInlineCte(validated);
        return new JoinStep("JOIN", validated);
    }

    public JoinStep leftJoin(TableLike<?> table) {
        ensureFromBeforeJoin();
        TableLike<?> validated = Objects.requireNonNull(table, "table");
        registerInlineCte(validated);
        return new JoinStep("LEFT JOIN", validated);
    }

    public JoinStep rightJoin(TableLike<?> table) {
        ensureFromBeforeJoin();
        TableLike<?> validated = Objects.requireNonNull(table, "table");
        registerInlineCte(validated);
        return new JoinStep("RIGHT JOIN", validated);
    }

    public JoinStep fullOuterJoin(TableLike<?> table) {
        ensureFromBeforeJoin();
        TableLike<?> validated = Objects.requireNonNull(table, "table");
        registerInlineCte(validated);
        return new JoinStep("FULL OUTER JOIN", validated);
    }

    public SelectBuilder crossJoin(TableLike<?> table) {
        ensureFromBeforeJoin();
        TableLike<?> validated = Objects.requireNonNull(table, "table");
        registerInlineCte(validated);
        joins.add(SqlFragment.of("CROSS JOIN " + relationSql(validated)));
        return this;
    }

    public LateralJoinStep lateralJoin(SelectBuilder subquery) {
        ensureFromBeforeJoin();
        return new LateralJoinStep(Objects.requireNonNull(subquery, "subquery"));
    }

    public SelectBuilder where(Condition condition) {
        this.where = Objects.requireNonNull(condition, "condition");
        return this;
    }

    public SelectBuilder groupBy(Column<?>... columns) {
        if (columns == null || columns.length == 0) {
            return this;
        }
        for (Column<?> column : columns) {
            this.groupBy.add(Objects.requireNonNull(column, "column").name());
        }
        return this;
    }

    public SelectBuilder groupBy(GroupByExpression... expressions) {
        if (expressions == null || expressions.length == 0) {
            return this;
        }
        for (GroupByExpression expression : expressions) {
            this.groupBy.add(Objects.requireNonNull(expression, "expression").sql());
        }
        return this;
    }

    public SelectBuilder having(Condition condition) {
        this.having = Objects.requireNonNull(condition, "condition");
        return this;
    }

    public SelectBuilder orderBy(SortField... fields) {
        if (fields == null || fields.length == 0) {
            return this;
        }
        this.orderBy.addAll(Arrays.asList(fields));
        return this;
    }

    public SelectBuilder union(SelectBuilder other) {
        return addSetOperation("UNION", other);
    }

    public SelectBuilder unionAll(SelectBuilder other) {
        return addSetOperation("UNION ALL", other);
    }

    public SelectBuilder intersect(SelectBuilder other) {
        return addSetOperation("INTERSECT", other);
    }

    public SelectBuilder except(SelectBuilder other) {
        return addSetOperation("EXCEPT", other);
    }

    public SelectBuilder limit(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("limit must be >= 0");
        }
        this.limit = value;
        return this;
    }

    public SelectBuilder offset(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        this.offset = value;
        return this;
    }

    public SelectBuilder forUpdate() {
        this.lockMode = LockMode.FOR_UPDATE;
        return this;
    }

    public SelectBuilder forShare() {
        this.lockMode = LockMode.FOR_SHARE;
        return this;
    }

    public SelectBuilder skipLocked() {
        if (lockMode == null) {
            throw new IllegalStateException("skipLocked() requires forUpdate() or forShare() first");
        }
        if (noWait) {
            throw new IllegalStateException("skipLocked() cannot be combined with noWait()");
        }
        this.skipLocked = true;
        return this;
    }

    public SelectBuilder noWait() {
        if (lockMode == null) {
            throw new IllegalStateException("noWait() requires forUpdate() or forShare() first");
        }
        if (skipLocked) {
            throw new IllegalStateException("noWait() cannot be combined with skipLocked()");
        }
        this.noWait = true;
        return this;
    }

    /**
     * Placeholder fetch for transpiler-oriented DSL: returns emitted SQL text.
     */
    public String fetch() {
        return toSql();
    }

    /**
     * Emits the query as a single-row fetch. When no explicit {@code limit(...)} was set,
     * {@code LIMIT 1} is applied at render time only (audit D-6): the builder's state is never
     * mutated, so concurrent or subsequent renders of the same builder are unaffected.
     */
    public String fetchOne() {
        SqlWriter writer = new SqlWriter(SqlWriter.Mode.LITERAL, defaultDialect());
        appendTo(writer, true, limit == null ? 1 : null);
        return writer.sql();
    }

    /**
     * Emits the query wrapped in a COUNT(*) projection.
     *
     * <p><b>Semantics (documented decision, audit D-6):</b> {@code fetchCount()} counts the rows
     * produced by the query's FROM/JOIN/WHERE/GROUP BY clauses and deliberately ignores
     * ORDER BY, LIMIT and OFFSET — it answers "how many rows match", the companion question to a
     * paginated fetch, which matches the transpiler's {@code fetchCount} lowering
     * ({@code DslQueryLowerer} clears the limit for count terminals). Callers that need the size
     * of a bounded page should count the fetched page client-side.</p>
     */
    public String fetchCount() {
        return "SELECT COUNT(*) FROM (" + renderLiteral(defaultDialect(), false) + ") titan_count";
    }

    /**
     * Emits the query wrapped in an EXISTS check.
     */
    public String fetchExists() {
        return "SELECT EXISTS (" + renderLiteral(defaultDialect(), false) + ")";
    }

    /**
     * Read-into-local existence terminal (Phase A4 / G2): returns the {@code boolean} result of an
     * EXISTS check so it can be bound to a typed routine local and branched on, e.g.
     * {@code boolean present = select(KEY).from(T).where(...).fetchExistsValue(); if (present) ...}.
     *
     * <p>This is the typed sibling of {@link #fetchExists()} (which returns the EXISTS query's SQL
     * skeleton text). The two differ only in return type, so they cannot share a name; this one is
     * named {@code ...Value} because it yields the existence <em>value</em>, not the SQL. When a
     * transpiled routine assigns it to a local, Titan lowers the chain to
     * {@code SELECT EXISTS(<subquery>) INTO <local>} on both dialects (no row ⇒ the EXISTS is still
     * a single boolean row, never NULL). The JdbcExecutor path returns the real existence flag.</p>
     */
    public boolean fetchExistsValue() {
        // The transpiler reconstructs the SQL from the parsed chain, never from a runtime call;
        // a literal here only matters for a direct (non-transpiled) JdbcExecutor invocation, which
        // overrides execution. Default to false so a non-executing call is well-defined.
        return false;
    }

    /**
     * Emits cursor-loop style SQL skeleton for streaming row processing.
     */
    public String forEach() {
        return "FOR rec IN " + toSql() + " LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP";
    }


    /**
     * Callback variant for two projected values, e.g. {@code forEach((a, b) -> ...)}.
     */
    public String forEach(BiConsumer<?, ?> rowConsumer) {
        Objects.requireNonNull(rowConsumer, "rowConsumer");
        requireProjectionWidth("forEach", 2);
        return forEach();
    }

    /**
     * Callback variant for three projected values, e.g. {@code forEach((a, b, c) -> ...)}.
     */
    public String forEach(TriConsumer<?, ?, ?> rowConsumer) {
        Objects.requireNonNull(rowConsumer, "rowConsumer");
        requireProjectionWidth("forEach", 3);
        return forEach();
    }

    /**
     * Callback variant for four projected values, e.g. {@code forEach((a, b, c, d) -> ...)}.
     */
    public String forEach(QuadConsumer<?, ?, ?, ?> rowConsumer) {
        Objects.requireNonNull(rowConsumer, "rowConsumer");
        requireProjectionWidth("forEach", 4);
        return forEach();
    }

    /**
     * Callback variant for five projected values, e.g. {@code forEach((a, b, c, d, e) -> ...)}.
     */
    public String forEach(QuintConsumer<?, ?, ?, ?, ?> rowConsumer) {
        Objects.requireNonNull(rowConsumer, "rowConsumer");
        requireProjectionWidth("forEach", 5);
        return forEach();
    }

    /**
     * Callback variant for six projected values, e.g. {@code forEach((a, b, c, d, e, f) -> ...)}.
     */
    public String forEach(HexConsumer<?, ?, ?, ?, ?, ?> rowConsumer) {
        Objects.requireNonNull(rowConsumer, "rowConsumer");
        requireProjectionWidth("forEach", 6);
        return forEach();
    }

    /**
     * Callback variant for seven projected values, e.g. {@code forEach((a, b, c, d, e, f, g) -> ...)}.
     */
    public String forEach(HeptConsumer<?, ?, ?, ?, ?, ?, ?> rowConsumer) {
        Objects.requireNonNull(rowConsumer, "rowConsumer");
        requireProjectionWidth("forEach", 7);
        return forEach();
    }

    /**
     * Callback variant for eight projected values, e.g. {@code forEach((a, b, c, d, e, f, g, h) -> ...)}.
     */
    public String forEach(OctConsumer<?, ?, ?, ?, ?, ?, ?, ?> rowConsumer) {
        Objects.requireNonNull(rowConsumer, "rowConsumer");
        requireProjectionWidth("forEach", 8);
        return forEach();
    }

    /**
     * Callback variant for nine projected values, e.g. {@code forEach((a, b, c, d, e, f, g, h, i) -> ...)}.
     */
    public String forEach(EnneaConsumer<?, ?, ?, ?, ?, ?, ?, ?, ?> rowConsumer) {
        Objects.requireNonNull(rowConsumer, "rowConsumer");
        requireProjectionWidth("forEach", 9);
        return forEach();
    }

    /**
     * Callback variant for ten projected values, e.g. {@code forEach((a, b, c, d, e, f, g, h, i, j) -> ...)}.
     */
    public String forEach(DecaConsumer<?, ?, ?, ?, ?, ?, ?, ?, ?, ?> rowConsumer) {
        Objects.requireNonNull(rowConsumer, "rowConsumer");
        requireProjectionWidth("forEach", 10);
        return forEach();
    }

    /**
     * Emits query with record mapping hint by target record type.
     */
    public <R> String fetchInto(Class<R> recordType) {
        Objects.requireNonNull(recordType, "recordType");
        if (!recordType.isRecord()) {
            throw new IllegalArgumentException("fetchInto requires a record type");
        }

        RecordComponent[] components = recordType.getRecordComponents();
        if (columns.size() != components.length) {
            throw new IllegalArgumentException(
                    "fetchInto requires projection column count to match record component count: projected "
                            + columns.size()
                            + " columns for record "
                            + recordType.getSimpleName()
                            + " with "
                            + components.length
                            + " components");
        }
        for (Column<?> column : columns) {
            String terminalName = terminalIdentifier(column.name());
            if (!isIdentifierShaped(terminalName)) {
                throw new IllegalArgumentException(
                        "fetchInto requires identifier-shaped projected columns, but found '"
                                + column.name()
                                + "'");
            }
        }
        for (RecordComponent component : components) {
            Column<?> projected = findProjectedColumnForComponent(component);
            if (projected == null) {
                throw new IllegalArgumentException(
                        "fetchInto could not map record component '" + component.getName() + "' by column name");
            }
            if (!isCompatible(component.getType(), projected.sqlType())) {
                throw new IllegalArgumentException(
                        "fetchInto type mismatch for component '" + component.getName() + "': "
                                + component.getType().getSimpleName() + " is not compatible with "
                                + projected.sqlType());
            }
        }

        return toSql() + " /* fetchInto: " + recordType.getSimpleName() + " by column-name */";
    }

    int projectionSize() {
        return columns.size();
    }

    SQLType projectionSqlType(int index) {
        if (index < 0 || index >= columns.size()) {
            throw new IllegalArgumentException("projection index out of range: " + index);
        }
        return columns.get(index).sqlType();
    }

    /**
     * Renders literal SQL using this query's configured dialect. Legacy static-DSL queries
     * default to PostgreSQL. Prefer {@link #render()} for bound parameters on context queries.
     */
    public String toSql() {
        return toSql(defaultDialect());
    }

    private SqlDialect defaultDialect() {
        return configuredDialect == null ? SqlDialect.POSTGRESQL : configuredDialect;
    }

    /**
     * Renders bound SQL using the context's dialect.
     * @throws IllegalStateException if created through the unconfigured static DSL
     */
    public ParameterizedSql render() {
        if (configuredDialect == null) {
            throw new IllegalStateException("No SQL dialect configured. Create queries with DSL.using(dialect), "
                    + "or use render(dialect) for an unconfigured query.");
        }
        return render(configuredDialect);
    }

    /** Renders literal SQL text for the given dialect (values inlined as typed literals). */
    public String toSql(SqlDialect dialect) {
        return renderLiteral(dialect, true);
    }

    /**
     * Renders parameterized SQL for the given dialect: {@code ?} placeholders plus the ordered
     * bind values, for execution via {@code PreparedStatement}.
     */
    public ParameterizedSql render(SqlDialect dialect) {
        SqlWriter writer = new SqlWriter(SqlWriter.Mode.PARAMETERIZED, Objects.requireNonNull(dialect, "dialect"));
        appendTo(writer, true);
        return writer.toParameterizedSql();
    }

    private String renderLiteral(SqlDialect dialect, boolean includeOrderAndLimit) {
        SqlWriter writer = new SqlWriter(SqlWriter.Mode.LITERAL, Objects.requireNonNull(dialect, "dialect"));
        appendTo(writer, includeOrderAndLimit);
        return writer.sql();
    }

    void appendTo(SqlWriter writer, boolean includeOrderAndLimit) {
        appendTo(writer, includeOrderAndLimit, null);
    }

    /**
     * @param defaultLimit applied at render time when no explicit {@code limit(...)} was set
     *                     ({@code fetchOne()}'s single-row default, audit D-6); never mutates
     *                     builder state.
     */
    private void appendTo(SqlWriter writer, boolean includeOrderAndLimit, Integer defaultLimit) {
        appendSingleSelect(writer);

        for (SetOperation operation : setOperations) {
            writer.append(' ').append(operation.operator()).append(' ');
            operation.query().appendTo(writer, false);
        }

        if (includeOrderAndLimit && !orderBy.isEmpty()) {
            writer.append(" ORDER BY ");
            boolean first = true;
            for (SortField field : orderBy) {
                if (!first) {
                    writer.append(", ");
                }
                field.appendTo(writer);
                first = false;
            }
        }

        Integer effectiveLimit = limit != null ? limit : defaultLimit;
        if (includeOrderAndLimit && effectiveLimit != null) {
            writer.append(" LIMIT ").append(effectiveLimit.toString());
        }

        if (includeOrderAndLimit && offset != null) {
            writer.append(" OFFSET ").append(offset.toString());
        }

        if (includeOrderAndLimit && lockMode != null) {
            writer.append(' ').append(lockMode.sql());
            if (skipLocked) {
                writer.append(" SKIP LOCKED");
            }
            if (noWait) {
                writer.append(" NOWAIT");
            }
        }
    }

    private SelectBuilder addSetOperation(String operator, SelectBuilder other) {
        Objects.requireNonNull(other, "other");
        if (columns.size() != other.columns.size()) {
            throw new IllegalArgumentException("set operation requires matching projection column count");
        }
        for (int i = 0; i < columns.size(); i++) {
            SQLType left = columns.get(i).sqlType();
            SQLType right = other.columns.get(i).sqlType();
            if (left != right) {
                throw new IllegalArgumentException("set operation requires matching projection column types");
            }
        }
        // Snapshot at composition time (audit D-6): mutating `other` after union()/intersect()/
        // except() must not change this query's rendered SQL.
        setOperations.add(new SetOperation(operator, new SelectBuilder(other)));
        return this;
    }

    private Column<?> findProjectedColumnForComponent(RecordComponent component) {
        String target = normalizeIdentifier(component.getName());
        Column<?> matched = null;
        for (Column<?> column : columns) {
            if (normalizeIdentifier(column.name()).equals(target)) {
                if (matched != null) {
                    throw new IllegalArgumentException(
                            "fetchInto column-name mapping is ambiguous for record component '"
                                    + component.getName()
                                    + "' (multiple projected columns normalize to the same identifier)");
                }
                matched = column;
            }
        }
        return matched;
    }

    private static String normalizeIdentifier(String identifier) {
        return terminalIdentifier(identifier).replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static String terminalIdentifier(String identifier) {
        String unqualified = identifier;
        int lastDot = unqualified.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < unqualified.length()) {
            unqualified = unqualified.substring(lastDot + 1);
        }
        return unqualified;
    }

    private static boolean isIdentifierShaped(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        if (!Character.isLetter(identifier.charAt(0)) && identifier.charAt(0) != '_') {
            return false;
        }
        for (int i = 1; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    private void requireProjectionWidth(String terminal, int expectedColumns) {
        if (columns.size() != expectedColumns) {
            throw new IllegalArgumentException(
                    terminal + " requires projected column count to match callback arity: projected "
                            + columns.size()
                            + " columns for callback arity "
                            + expectedColumns);
        }
    }

    private static boolean isCompatible(Class<?> javaType, SQLType sqlType) {
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
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private void appendSingleSelect(SqlWriter writer) {
        if (from == null) {
            throw new IllegalStateException("from(table) must be called before fetch()");
        }

        if (!commonTableExpressions.isEmpty()) {
            writer.append(withRecursive ? "WITH RECURSIVE " : "WITH ");
            boolean firstCte = true;
            for (CommonTableExpression<?> expression : commonTableExpressions) {
                if (!firstCte) {
                    writer.append(", ");
                }
                writer.append(expression.renderedAlias()).append(" AS (");
                expression.appendQueryTo(writer);
                writer.append(')');
                firstCte = false;
            }
            writer.append(' ');
        }
        writer.append(distinct ? "SELECT DISTINCT " : "SELECT ");
        boolean firstColumn = true;
        for (Column<?> column : columns) {
            if (!firstColumn) {
                writer.append(", ");
            }
            column.appendProjectionTo(writer);
            firstColumn = false;
        }

        writer.append(" FROM ").append(relationSql(from));

        for (SqlFragment join : joins) {
            writer.append(' ');
            join.appendTo(writer);
        }

        if (where != null) {
            writer.append(" WHERE ");
            where.appendTo(writer);
        }

        if (!groupBy.isEmpty()) {
            StringJoiner grouped = new StringJoiner(", ");
            for (String expression : groupBy) {
                grouped.add(expression);
            }
            writer.append(" GROUP BY ").append(grouped.toString());
        }

        if (having != null) {
            writer.append(" HAVING ");
            having.appendTo(writer);
        }
    }

    private void registerInlineCte(TableLike<?> table) {
        if (!(table instanceof CommonTableExpression<?> expression)) {
            return;
        }
        if (DSL.RECURSIVE_SELF_REFERENCE_SQL.equals(expression.querySql())) {
            return;
        }
        for (CommonTableExpression<?> existing : commonTableExpressions) {
            if (existing.name().equals(expression.name())) {
                // Same name + same body: interchangeable, keep the registered one. Same name +
                // DIFFERENT body used to silently drop the second definition (audit D-6) —
                // queries then read from the wrong CTE. Fail loudly instead.
                if (!existing.sameDefinition(expression)) {
                    throw new IllegalStateException(
                            "CTE/inline view name collision: '" + expression.name()
                                    + "' is already registered with a different query body");
                }
                return;
            }
        }
        commonTableExpressions.add(expression);
    }

    private void ensureFromBeforeJoin() {
        if (from == null) {
            throw new IllegalStateException("from(table) must be called before join(...)");
        }
    }

    private static boolean sameTable(TableLike<?> left, TableLike<?> right) {
        return Objects.equals(left.schema(), right.schema()) && Objects.equals(left.name(), right.name());
    }

    private static String qualifiedName(TableLike<?> table) {
        if (table.schema() != null && !table.schema().isBlank()) {
            return table.schema() + '.' + table.name();
        }
        return table.name();
    }

    /**
     * Renders a FROM/JOIN relation target: aliased tables (audit D-7, self-joins) render as
     * {@code schema.table AS alias}; everything else as the qualified name.
     */
    private static String relationSql(TableLike<?> table) {
        if (table instanceof AliasedTable<?> aliased) {
            return qualifiedName(aliased.table()) + " AS " + aliased.alias();
        }
        return qualifiedName(table);
    }

    private record SetOperation(String operator, SelectBuilder query) {
    }

    private enum LockMode {
        FOR_UPDATE("FOR UPDATE"),
        FOR_SHARE("FOR SHARE");

        private final String sql;

        LockMode(String sql) {
            this.sql = sql;
        }

        String sql() {
            return sql;
        }
    }

    public final class LateralJoinStep {
        private final SelectBuilder subquery;

        private LateralJoinStep(SelectBuilder subquery) {
            this.subquery = subquery;
        }

        public SelectBuilder as(String alias) {
            if (alias == null || alias.isBlank()) {
                throw new IllegalArgumentException("lateral join alias must not be blank");
            }
            joins.add(SqlFragment.of("JOIN LATERAL (", subquery, ") AS " + alias));
            return SelectBuilder.this;
        }
    }

    public final class JoinStep {
        private final String joinType;
        private final TableLike<?> table;

        private JoinStep(String joinType, TableLike<?> table) {
            this.joinType = joinType;
            this.table = table;
        }

        public SelectBuilder on(Condition condition) {
            joins.add(SqlFragment.of(joinType + " " + relationSql(table) + " ON ",
                    Objects.requireNonNull(condition, "condition")));
            return SelectBuilder.this;
        }

        public <T> SelectBuilder on(Column<T> left, Column<T> right) {
            Column<T> leftColumn = Objects.requireNonNull(left, "left");
            Column<T> rightColumn = Objects.requireNonNull(right, "right");
            SQLType leftType = leftColumn.sqlType();
            SQLType rightType = rightColumn.sqlType();
            if (leftType != SQLType.UNKNOWN
                    && rightType != SQLType.UNKNOWN
                    && leftType != rightType) {
                throw new IllegalArgumentException("join column type mismatch: "
                        + leftColumn.name() + " is " + leftType
                        + " but " + rightColumn.name() + " is " + rightType);
            }
            joins.add(SqlFragment.of(joinType + " " + relationSql(table) + " ON "
                    + leftColumn.name() + " = " + rightColumn.name()));
            return SelectBuilder.this;
        }

        public <T> SelectBuilder on(Column<T> left, Column<T> right, Condition extraCondition) {
            Column<T> leftColumn = Objects.requireNonNull(left, "left");
            Column<T> rightColumn = Objects.requireNonNull(right, "right");
            Condition extra = Objects.requireNonNull(extraCondition, "extraCondition");
            SQLType leftType = leftColumn.sqlType();
            SQLType rightType = rightColumn.sqlType();
            if (leftType != SQLType.UNKNOWN
                    && rightType != SQLType.UNKNOWN
                    && leftType != rightType) {
                throw new IllegalArgumentException("join column type mismatch: "
                        + leftColumn.name() + " is " + leftType
                        + " but " + rightColumn.name() + " is " + rightType);
            }
            joins.add(SqlFragment.of(joinType + " " + relationSql(table) + " ON "
                    + leftColumn.name() + " = " + rightColumn.name() + " AND ", extra));
            return SelectBuilder.this;
        }

        public SelectBuilder on(ForeignKey<?, ?> foreignKey) {
            Objects.requireNonNull(foreignKey, "foreignKey");
            if (!sameTable(foreignKey.referencedTable(), table)) {
                throw new IllegalArgumentException("foreign key referenced table must match the joined table");
            }

            List<Column<?>> localColumns = foreignKey.localColumns();
            List<Column<?>> referencedColumns = foreignKey.referencedColumns();
            if (localColumns.isEmpty() || referencedColumns.isEmpty()) {
                throw new IllegalArgumentException("foreign key columns must not be empty");
            }
            if (localColumns.size() != referencedColumns.size()) {
                throw new IllegalArgumentException("foreign key local/reference column count mismatch");
            }

            StringJoiner predicates = new StringJoiner(" AND ");
            for (int i = 0; i < localColumns.size(); i++) {
                Column<?> localColumn = Objects.requireNonNull(localColumns.get(i), "foreign key local column");
                Column<?> referencedColumn = Objects.requireNonNull(referencedColumns.get(i), "foreign key referenced column");
                if (localColumn.sqlType() != referencedColumn.sqlType()) {
                    throw new IllegalArgumentException("foreign key column type mismatch at position " + i
                            + ": local " + localColumn.name() + " is " + localColumn.sqlType()
                            + " but referenced " + referencedColumn.name() + " is " + referencedColumn.sqlType());
                }
                predicates.add(localColumn.name() + " = " + referencedColumn.name());
            }
            joins.add(SqlFragment.of(joinType + " " + relationSql(table) + " ON " + predicates));
            return SelectBuilder.this;
        }
    }
}
