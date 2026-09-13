package titan.dsl;

/**
 * Shared code-defined view descriptor; emitted by transpiler as CREATE VIEW artifacts.
 */
public final class ViewDef<R> extends View<R> {

    private final String querySql;

    ViewDef(String name, String schema, String querySql) {
        super(name, schema);
        if (querySql == null || querySql.isBlank()) {
            throw new IllegalArgumentException("querySql must not be blank");
        }
        this.querySql = querySql;
    }

    public String querySql() {
        return querySql;
    }
}
