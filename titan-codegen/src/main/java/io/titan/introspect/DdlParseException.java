package io.titan.introspect;

/**
 * Thrown by {@link DdlSchemaParser} when a DDL script contains SQL the fallback parser does not
 * understand. The parser must never silently drop statements (audit G-6): anything it cannot
 * faithfully represent in a {@link SchemaModel} is a hard error that names the offending SQL and
 * points at container-backed introspection, which handles full dialect syntax by applying the
 * DDL to a real database.
 */
public class DdlParseException extends RuntimeException {

    public DdlParseException(String message) {
        super(message);
    }

    static DdlParseException unrecognizedStatement(String statement) {
        return new DdlParseException(
                "The DDL fallback parser does not recognize this statement and refuses to drop it silently:\n\n"
                        + indent(statement) + "\n\n"
                        + CONTAINER_MODE_HINT);
    }

    static DdlParseException unrecognizedTablePart(String tableName, String part) {
        return new DdlParseException(
                "The DDL fallback parser does not recognize this clause in CREATE TABLE " + tableName
                        + " and refuses to drop it silently:\n\n"
                        + indent(part) + "\n\n"
                        + CONTAINER_MODE_HINT);
    }

    private static final String CONTAINER_MODE_HINT =
            "Use container-backed DDL introspection instead (titan { database.ddlMode = 'container' }, the default), "
                    + "which applies the DDL to a scratch Testcontainers database and introspects it via JDBC — "
                    + "it supports the full dialect syntax. The regex fallback parser "
                    + "(database.ddlMode = 'parser') only exists for Docker-less environments and intentionally "
                    + "fails on anything it cannot represent faithfully.";

    private static String indent(String text) {
        return "    " + text.strip().replace("\n", "\n    ");
    }
}
