package io.titan.catalog;

import io.titan.introspect.SchemaModel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Facade over the catalog generators: validates the schema for name-mangling collisions
 * first ({@link CatalogCollisionDetector}, audit G-8), then produces all generated sources in a
 * single map, refusing duplicate output paths instead of silently overwriting.
 */
public final class CatalogGenerator {

    public Map<String, String> generate(SchemaModel schema, String targetPackage) {
        CatalogCollisionDetector.check(schema, targetPackage);

        var output = new LinkedHashMap<String, String>();
        mergeInto(output, new TableDescriptorGenerator().generate(schema, targetPackage));
        mergeInto(output, new RowRecordGenerator().generate(schema, targetPackage));
        mergeInto(output, new ViewDescriptorGenerator().generate(schema, targetPackage));
        mergeInto(output, new EnumTypeGenerator().generate(schema, targetPackage));
        mergeInto(output, new CatalogRegistryGenerator().generate(schema, targetPackage));
        return output;
    }

    // Defense in depth behind the collision detector: a duplicate path must never overwrite.
    private static void mergeInto(Map<String, String> output, Map<String, String> generated) {
        for (var entry : generated.entrySet()) {
            String previous = output.putIfAbsent(entry.getKey(), entry.getValue());
            if (previous != null) {
                throw new CatalogGenerationException(
                        "Catalog generation produced the same file twice: " + entry.getKey()
                                + ". This is a bug in collision detection — please report it.");
            }
        }
    }
}
