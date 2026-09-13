package io.titan.catalog;

import io.titan.introspect.SchemaModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects name-mangling collisions before any catalog file is generated (audit G-8).
 *
 * <p>Two distinct database identifiers can mangle to the same Java name
 * ({@code user_account} / {@code user__account} → {@code UserAccount};
 * {@code user_id} / {@code userId} → {@code USER_ID}). Previously the later file silently
 * overwrote the earlier one (a catalog missing whole tables) or the generated class simply did
 * not compile (duplicate fields). This detector fails the build with a diagnostic naming every
 * offender, using the exact same mangling functions ({@link CatalogNaming}) the generators use.</p>
 */
public final class CatalogCollisionDetector {

    private CatalogCollisionDetector() {
    }

    /** @throws CatalogGenerationException when any generated name collides */
    public static void check(SchemaModel schema, String targetPackage) {
        var problems = new ArrayList<String>();
        checkClassPaths(schema, targetPackage, problems);
        for (var table : schema.tables()) {
            checkDescriptorFields(table, problems);
            checkRecordComponents(table, problems);
        }
        for (var view : schema.views()) {
            checkViewFields(view, problems);
        }
        if (!problems.isEmpty()) {
            throw new CatalogGenerationException(problems);
        }
    }

    private static void checkClassPaths(SchemaModel schema, String targetPackage, List<String> problems) {
        var owners = new LinkedHashMap<String, List<String>>();
        for (var table : schema.tables()) {
            String segment = CatalogNaming.schemaPackageSegment(table.schema());
            String className = CatalogNaming.toClassName(table.name(), "UnnamedTable");
            String owner = "table " + qualified(table.schema(), table.name());
            record(owners, path(targetPackage, segment, "tables", className), owner);
            record(owners, path(targetPackage, segment, "records", className + "Record"), owner);
        }
        for (var view : schema.views()) {
            String segment = CatalogNaming.schemaPackageSegment(view.schema());
            String className = CatalogNaming.toClassName(view.name(), "UnnamedView");
            record(owners, path(targetPackage, segment, "views", className), "view " + qualified(view.schema(), view.name()));
        }
        for (var enumType : schema.enumTypes()) {
            String segment = CatalogNaming.schemaPackageSegment(enumType.schema());
            String className = CatalogNaming.toClassName(enumType.name(), "UnnamedEnum");
            record(owners, path(targetPackage, segment, "enums", className), "enum type " + qualified(enumType.schema(), enumType.name()));
        }

        owners.forEach((path, names) -> {
            if (names.size() > 1) {
                problems.add("generated class " + path + " is produced by " + names.size() + " schema objects: "
                        + String.join(", ", names));
            }
        });
    }

    private static void checkDescriptorFields(SchemaModel.TableMeta table, List<String> problems) {
        var fields = new LinkedHashMap<String, List<String>>();
        for (var column : table.columns()) {
            record(fields, CatalogNaming.toConstantName(column.name()), "column " + column.name());
        }
        boolean hasPrimaryKey = table.constraints().stream()
                .anyMatch(c -> c.type() == SchemaModel.ConstraintType.PRIMARY_KEY);
        if (hasPrimaryKey) {
            record(fields, "PK", "the implicit PK field");
        }
        for (var constraint : table.constraints()) {
            if (constraint.type() == SchemaModel.ConstraintType.UNIQUE) {
                record(fields, CatalogNaming.toConstantName(constraint.name()), "unique constraint " + constraint.name());
            }
        }
        for (var foreignKey : table.foreignKeys()) {
            record(fields, CatalogNaming.toConstantName(foreignKey.name()), "foreign key " + foreignKey.name());
        }
        // The table singleton shares the descriptor's namespace too.
        record(fields, CatalogNaming.toClassName(table.name(), "UnnamedTable").toUpperCase(java.util.Locale.ROOT),
                "the table singleton");

        reportFieldCollisions(fields, "table " + qualified(table.schema(), table.name()) + " descriptor", problems);
    }

    private static void checkRecordComponents(SchemaModel.TableMeta table, List<String> problems) {
        var components = new LinkedHashMap<String, List<String>>();
        for (var column : table.columns()) {
            record(components, CatalogNaming.toJavaFieldName(column.name()), "column " + column.name());
        }
        reportFieldCollisions(components, "table " + qualified(table.schema(), table.name()) + " record", problems);
    }

    private static void checkViewFields(SchemaModel.ViewMeta view, List<String> problems) {
        var fields = new LinkedHashMap<String, List<String>>();
        for (var column : view.columns()) {
            record(fields, CatalogNaming.toConstantName(column.name()), "column " + column.name());
        }
        record(fields, CatalogNaming.toConstantName(view.name()), "the view singleton");
        reportFieldCollisions(fields, "view " + qualified(view.schema(), view.name()) + " descriptor", problems);
    }

    private static void reportFieldCollisions(Map<String, List<String>> fields, String context, List<String> problems) {
        fields.forEach((field, names) -> {
            if (names.size() > 1) {
                problems.add(context + ": generated member '" + field + "' is produced by "
                        + String.join(", ", names));
            }
        });
    }

    private static void record(Map<String, List<String>> map, String key, String owner) {
        map.computeIfAbsent(key, ignored -> new ArrayList<>()).add(owner);
    }

    private static String path(String targetPackage, String schemaSegment, String kind, String className) {
        return (targetPackage + "." + schemaSegment + "." + kind).replace('.', '/') + "/" + className + ".java";
    }

    private static String qualified(String schema, String name) {
        return schema == null ? name : schema + "." + name;
    }
}
