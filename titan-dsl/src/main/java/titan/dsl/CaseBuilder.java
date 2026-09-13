package titan.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Searched CASE expression builder (audit D-7): {@code DSL.when(condition, result)} →
 * {@code .when(condition, result)}* → {@code .otherwise(result)} yields a typed {@link Column}
 * usable in projections.
 *
 * <p>Branch results are captured as typed {@link BindValue}s, so rendering stays correct in
 * both modes: {@code ?} placeholders when parameterized, per-dialect literals when literal.</p>
 */
public final class CaseBuilder<T> {

    private record Branch<T>(Condition condition, T result) {
    }

    private final List<Branch<T>> branches = new ArrayList<>();

    CaseBuilder(Condition condition, T result) {
        when(condition, result);
    }

    /** Adds another {@code WHEN condition THEN result} branch. */
    public CaseBuilder<T> when(Condition condition, T result) {
        branches.add(new Branch<>(Objects.requireNonNull(condition, "condition"), result));
        return this;
    }

    /** Terminates the CASE with {@code ELSE result END} and returns the projected column. */
    public Column<T> otherwise(T result) {
        SQLType sqlType = inferSqlType(result);
        SqlFragment.Builder fragment = SqlFragment.builder().add("CASE");
        for (Branch<T> branch : branches) {
            fragment.add(" WHEN ").add(branch.condition()).add(" THEN ")
                    .add(BindValue.of(branch.result(), sqlType));
        }
        fragment.add(" ELSE ").add(BindValue.of(result, sqlType)).add(" END");
        boolean allNonNull = result != null && branches.stream().allMatch(branch -> branch.result() != null);
        return new Column<>(fragment.build(), sqlType, allNonNull ? Nullability.NOT_NULL : Nullability.NULLABLE);
    }

    private SQLType inferSqlType(T elseResult) {
        for (Branch<T> branch : branches) {
            if (branch.result() != null) {
                return DSL.sqlTypeFor(branch.result().getClass());
            }
        }
        if (elseResult != null) {
            return DSL.sqlTypeFor(elseResult.getClass());
        }
        return SQLType.UNKNOWN;
    }
}
