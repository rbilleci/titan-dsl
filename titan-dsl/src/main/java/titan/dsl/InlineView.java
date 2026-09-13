package titan.dsl;

/**
 * Procedure-scoped inline view descriptor; emitted as a CTE, never as CREATE VIEW.
 */
public final class InlineView<R> extends CommonTableExpression<R> {

    InlineView(String name, String querySql) {
        super(name, querySql);
    }
}
