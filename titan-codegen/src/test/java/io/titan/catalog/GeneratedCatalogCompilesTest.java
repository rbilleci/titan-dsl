package io.titan.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.titan.introspect.SchemaModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GAP G-8 end-to-end: schemas with Java-keyword columns, MySQL enums, self-referential FKs and
 * cross-schema FKs between identically named tables must generate code that actually compiles
 * (and, for the self-referential FK, classes that initialize without NPEing on their own
 * singleton).
 */
class GeneratedCatalogCompilesTest {

    @TempDir
    Path tempDir;

    @Test
    void generatedCatalogForHostileSchemaCompilesAndInitializes() throws Exception {
        var employees = new SchemaModel.TableMeta(
                "public",
                "employees",
                List.of(
                        notNull("id", "bigint"),
                        nullable("manager_id", "bigint"),
                        // Java keyword and digit-leading column names (G-8).
                        nullable("class", "varchar"),
                        nullable("2fa_enabled", "boolean")
                ),
                List.of(new SchemaModel.ConstraintMeta("employees_pkey",
                        SchemaModel.ConstraintType.PRIMARY_KEY, List.of("id"))),
                List.of(
                        // Self-referential FK: initialization-order hazard (G-8).
                        new SchemaModel.ForeignKeyMeta("employees_manager_fk",
                                List.of("manager_id"), "public", "employees", List.of("id")),
                        // Cross-schema FK to an identically named table: needs full qualification.
                        new SchemaModel.ForeignKeyMeta("employees_archive_fk",
                                List.of("id"), "archive", "employees", List.of("id"))
                ),
                List.of()
        );

        var archivedEmployees = new SchemaModel.TableMeta(
                "archive",
                "employees",
                List.of(notNull("id", "bigint")),
                List.of(new SchemaModel.ConstraintMeta("employees_pkey",
                        SchemaModel.ConstraintType.PRIMARY_KEY, List.of("id"))),
                List.of(),
                List.of()
        );

        var accounts = new SchemaModel.TableMeta(
                "public",
                "accounts",
                List.of(
                        notNull("id", "bigint"),
                        // MySQL-style enum column with a synthesized type name (G-8).
                        new SchemaModel.ColumnMeta("status", "enum", false, null, null, 9,
                                "enum", "accounts_status_enum", List.of("ACTIVE", "SUSPENDED"), false)
                ),
                List.of(new SchemaModel.ConstraintMeta("accounts_pkey",
                        SchemaModel.ConstraintType.PRIMARY_KEY, List.of("id"))),
                List.of(),
                List.of()
        );

        var schema = new SchemaModel(
                List.of(employees, archivedEmployees, accounts),
                List.of(),
                List.of(new SchemaModel.EnumTypeMeta("titan", "accounts_status_enum", List.of("ACTIVE", "SUSPENDED")))
        );

        Map<String, String> generated = new CatalogGenerator().generate(schema, "gen.catalog");

        // The MySQL enum type generates a valid class file name, not Enum('a','b').java.
        assertTrue(generated.containsKey("gen/catalog/titan/enums/AccountsStatusEnum.java"),
                "expected synthesized enum class, got: " + generated.keySet());

        // Keyword column generates a keyword-safe record component.
        String recordSource = generated.get("gen/catalog/public_/records/EmployeesRecord.java");
        assertNotNull(recordSource);
        assertTrue(recordSource.contains("String class_"), recordSource);
        assertTrue(recordSource.contains("Boolean _2faEnabled"), recordSource);

        // Self-referential FK references the instance under construction, not the singleton.
        String descriptorSource = generated.get("gen/catalog/public_/tables/Employees.java");
        assertNotNull(descriptorSource);
        assertTrue(descriptorSource.contains("foreignKey(MANAGER_ID, this, ID)"), descriptorSource);
        // Cross-schema FK is fully qualified (two schemas contain a table named employees).
        assertTrue(descriptorSource.contains("gen.catalog.archive.tables.Employees.EMPLOYEES"), descriptorSource);

        Path classes = compile(generated);

        // Loading the descriptor must run its full static initialization without NPE.
        try (URLClassLoader loader = new URLClassLoader(
                new URL[] {classes.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> descriptor = Class.forName("gen.catalog.public_.tables.Employees", true, loader);
            Object singleton = descriptor.getField("EMPLOYEES").get(null);
            assertNotNull(singleton);

            Object selfFk = descriptor.getField("EMPLOYEES_MANAGER_FK").get(singleton);
            assertNotNull(selfFk, "self-referential FK must initialize");
            Object referencedTable = selfFk.getClass().getMethod("referencedTable").invoke(selfFk);
            assertSame(singleton, referencedTable, "self-referential FK must point at the singleton instance");

            Class<?> enumClass = Class.forName("gen.catalog.titan.enums.AccountsStatusEnum", true, loader);
            assertEquals(2, enumClass.getEnumConstants().length);

            // The registry references every singleton, including the two same-named tables.
            Class<?> registry = Class.forName("gen.catalog.Catalog", true, loader);
            List<?> tables = (List<?>) registry.getField("TABLES").get(null);
            assertEquals(3, tables.size());
            assertTrue(tables.contains(singleton), "Catalog.TABLES must reference the generated singleton");
            assertEquals(0, ((List<?>) registry.getField("VIEWS").get(null)).size());
        }
    }

    private Path compile(Map<String, String> generated) throws Exception {
        Path sourceRoot = tempDir.resolve("sources");
        var sourceFiles = new ArrayList<File>();
        for (var entry : generated.entrySet()) {
            Path file = sourceRoot.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
            sourceFiles.add(file.toFile());
        }

        // Generated records import org.jspecify.annotations.Nullable; keep the test hermetic
        // with a source-compatible stub.
        Path stub = sourceRoot.resolve("org/jspecify/annotations/Nullable.java");
        Files.createDirectories(stub.getParent());
        Files.writeString(stub, """
                package org.jspecify.annotations;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE_USE)
                public @interface Nullable {
                }
                """, StandardCharsets.UTF_8);
        sourceFiles.add(stub.toFile());

        Path classes = Files.createDirectories(tempDir.resolve("classes"));
        File titanDsl = new File(Class.forName("titan.dsl.Table")
                .getProtectionDomain().getCodeSource().getLocation().toURI());

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new ByteArrayOutputStream();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classes.toFile()));
            fileManager.setLocation(StandardLocation.CLASS_PATH, List.of(titanDsl));
            boolean success = compiler.getTask(
                    new java.io.PrintWriter(diagnostics, true, StandardCharsets.UTF_8),
                    fileManager, null, null, null,
                    fileManager.getJavaFileObjectsFromFiles(sourceFiles)).call();
            assertTrue(success, "generated catalog must compile, javac said:\n" + diagnostics);
        }
        return classes;
    }

    private static SchemaModel.ColumnMeta notNull(String name, String type) {
        return new SchemaModel.ColumnMeta(name, type, false, null, null, null);
    }

    private static SchemaModel.ColumnMeta nullable(String name, String type) {
        return new SchemaModel.ColumnMeta(name, type, true, null, null, null);
    }
}
