package titan.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable sequence of SQL parts: literal text, {@link BindValue}s, nested {@link Condition}s,
 * and nested {@link SelectBuilder} subqueries. Rendering is deferred until a dialect and mode
 * are known, so captured values stay typed instead of being pre-rendered to literal strings
 * (audit finding D-1).
 */
final class SqlFragment {

    private final List<Object> parts;

    private SqlFragment(List<Object> parts) {
        this.parts = List.copyOf(parts);
    }

    static SqlFragment of(Object... parts) {
        List<Object> validated = new ArrayList<>(parts.length);
        for (Object part : parts) {
            validated.add(validatePart(part));
        }
        return new SqlFragment(validated);
    }

    static Builder builder() {
        return new Builder();
    }

    void appendTo(SqlWriter writer) {
        for (Object part : parts) {
            if (part instanceof String text) {
                writer.append(text);
            } else if (part instanceof BindValue value) {
                writer.appendValue(value);
            } else if (part instanceof Condition condition) {
                condition.appendTo(writer);
            } else if (part instanceof SelectBuilder subquery) {
                subquery.appendTo(writer, true);
            } else if (part instanceof SqlFragment fragment) {
                fragment.appendTo(writer);
            } else {
                throw new IllegalStateException("Unsupported SQL fragment part: " + part.getClass());
            }
        }
    }

    private static Object validatePart(Object part) {
        Objects.requireNonNull(part, "fragment part");
        if (part instanceof String || part instanceof BindValue || part instanceof Condition
                || part instanceof SelectBuilder || part instanceof SqlFragment) {
            return part;
        }
        throw new IllegalArgumentException("Unsupported SQL fragment part: " + part.getClass());
    }

    static final class Builder {
        private final List<Object> parts = new ArrayList<>();

        Builder add(Object part) {
            parts.add(validatePart(part));
            return this;
        }

        SqlFragment build() {
            return new SqlFragment(parts);
        }
    }
}
