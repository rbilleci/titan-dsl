package io.titan.catalog;

import io.titan.introspect.SchemaModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogRegistryGeneratorTest {

    @Test
    void listsEveryTableAndViewSingletonFullyQualified() {
        var schema = new SchemaModel(
                List.of(
                        new SchemaModel.TableMeta("public", "employees", List.of(column("id"))),
                        new SchemaModel.TableMeta("archive", "employees", List.of(column("id")))),
                List.of(new SchemaModel.ViewMeta("public", "active_employees", List.of(column("id")))),
                List.of());

        var generated = new CatalogRegistryGenerator().generate(schema, "com.example.gen");
        assertEquals(1, generated.size());

        String source = generated.get("com/example/gen/Catalog.java");
        assertTrue(source.contains("package com.example.gen;"), source);
        assertTrue(source.contains("public final class Catalog {"), source);
        assertTrue(source.contains("public static final List<Table<?>> TABLES = List.of(\n"
                + "            com.example.gen.public_.tables.Employees.EMPLOYEES,\n"
                + "            com.example.gen.archive.tables.Employees.EMPLOYEES);"), source);
        assertTrue(source.contains("public static final List<View<?>> VIEWS = List.of(\n"
                + "            com.example.gen.public_.views.ActiveEmployees.ACTIVE_EMPLOYEES);"), source);
    }

    @Test
    void emptySchemaProducesEmptyLists() {
        var generated = new CatalogRegistryGenerator().generate(new SchemaModel(List.of(), List.of(), List.of()), "gen");
        String source = generated.get("gen/Catalog.java");
        assertTrue(source.contains("TABLES = List.of();"), source);
        assertTrue(source.contains("VIEWS = List.of();"), source);
    }

    private static SchemaModel.ColumnMeta column(String name) {
        return new SchemaModel.ColumnMeta(name, "integer", false, null, null, null);
    }
}
