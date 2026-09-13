package io.titan.catalog;

import java.util.List;

/**
 * Catalog generation refused to produce output because the schema mangles into colliding or
 * otherwise uncompilable Java names (audit G-8). The message lists every offending table/column
 * instead of silently overwriting generated files.
 */
public class CatalogGenerationException extends RuntimeException {

    public CatalogGenerationException(List<String> problems) {
        super("Catalog generation aborted: the schema produces colliding generated names.\n"
                + "Rename the offending database objects (or split them across schemas) — silently "
                + "overwriting generated files would ship a catalog that is missing tables.\n\n  - "
                + String.join("\n  - ", problems));
    }

    public CatalogGenerationException(String message) {
        super(message);
    }
}
