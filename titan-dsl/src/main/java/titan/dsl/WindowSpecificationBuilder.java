package titan.dsl;

import java.util.StringJoiner;

public final class WindowSpecificationBuilder {

    private final StringJoiner clauses = new StringJoiner(" ");

    public WindowSpecificationBuilder partitionBy(Column<?>... columns) {
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("partition columns must not be empty");
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (Column<?> column : columns) {
            if (column == null) {
                throw new IllegalArgumentException("partition columns must not contain nulls");
            }
            joiner.add(column.name());
        }
        clauses.add("PARTITION BY " + joiner);
        return this;
    }

    public WindowSpecificationBuilder orderBy(SortField... fields) {
        if (fields == null || fields.length == 0) {
            throw new IllegalArgumentException("order fields must not be empty");
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (SortField field : fields) {
            if (field == null) {
                throw new IllegalArgumentException("order fields must not contain nulls");
            }
            joiner.add(field.sql());
        }
        clauses.add("ORDER BY " + joiner);
        return this;
    }

    public WindowSpecificationBuilder rowsBetween(WindowFrameBoundary start, WindowFrameBoundary end) {
        clauses.add("ROWS BETWEEN " + start.sql() + " AND " + end.sql());
        return this;
    }

    public WindowSpecificationBuilder rangeBetween(WindowFrameBoundary start, WindowFrameBoundary end) {
        clauses.add("RANGE BETWEEN " + start.sql() + " AND " + end.sql());
        return this;
    }

    public WindowSpecificationBuilder groupsBetween(WindowFrameBoundary start, WindowFrameBoundary end) {
        clauses.add("GROUPS BETWEEN " + start.sql() + " AND " + end.sql());
        return this;
    }

    String toSql() {
        if (clauses.length() == 0) {
            throw new IllegalStateException("window specification must not be empty");
        }
        return clauses.toString();
    }
}
