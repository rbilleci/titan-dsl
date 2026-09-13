package io.titan.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.titan.introspect.SchemaModel;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * GAP G-8: duplicate generated class paths and duplicate generated member names fail with a
 * diagnostic listing the offending tables/columns instead of silently overwriting files or
 * emitting uncompilable classes.
 */
class CatalogCollisionDetectorTest {

    private static SchemaModel.TableMeta table(String name, SchemaModel.ColumnMeta... columns) {
        return new SchemaModel.TableMeta("public", name, List.of(columns));
    }

    private static SchemaModel.ColumnMeta column(String name) {
        return new SchemaModel.ColumnMeta(name, "integer", true, null, null, null);
    }

    @Test
    void duplicateGeneratedClassPathsFailWithBothTablesNamed() {
        var schema = new SchemaModel(List.of(
                table("user_account", column("id")),
                table("user__account", column("id"))
        ), List.of(), List.of());

        var failure = assertThrows(CatalogGenerationException.class,
                () -> CatalogCollisionDetector.check(schema, "gen"));

        assertTrue(failure.getMessage().contains("UserAccount"), failure.getMessage());
        assertTrue(failure.getMessage().contains("table public.user_account"), failure.getMessage());
        assertTrue(failure.getMessage().contains("table public.user__account"), failure.getMessage());
    }

    @Test
    void duplicateColumnConstantNamesFailWithBothColumnsNamed() {
        var schema = new SchemaModel(List.of(
                table("accounts", column("user_id"), column("userId"))
        ), List.of(), List.of());

        var failure = assertThrows(CatalogGenerationException.class,
                () -> CatalogCollisionDetector.check(schema, "gen"));

        assertTrue(failure.getMessage().contains("USER_ID"), failure.getMessage());
        assertTrue(failure.getMessage().contains("column user_id"), failure.getMessage());
        assertTrue(failure.getMessage().contains("column userId"), failure.getMessage());
    }

    @Test
    void columnCollidingWithThePkFieldFails() {
        var schema = new SchemaModel(List.of(
                new SchemaModel.TableMeta(
                        "public", "accounts",
                        List.of(column("id"), column("pk")),
                        List.of(new SchemaModel.ConstraintMeta("accounts_pkey",
                                SchemaModel.ConstraintType.PRIMARY_KEY, List.of("id"))),
                        List.of(), List.of())
        ), List.of(), List.of());

        var failure = assertThrows(CatalogGenerationException.class,
                () -> CatalogCollisionDetector.check(schema, "gen"));
        assertTrue(failure.getMessage().contains("'PK'"), failure.getMessage());
    }

    @Test
    void tableAndViewWithSameNameDoNotCollide() {
        var schema = new SchemaModel(
                List.of(table("accounts", column("id"))),
                List.of(new SchemaModel.ViewMeta("public", "accounts", List.of(column("id")))),
                List.of());

        assertDoesNotThrow(() -> CatalogCollisionDetector.check(schema, "gen"));
    }

    @Test
    void enumTypesCollidingWithEachOtherFail() {
        var schema = new SchemaModel(List.of(), List.of(), List.of(
                new SchemaModel.EnumTypeMeta("public", "account_status", List.of("A")),
                new SchemaModel.EnumTypeMeta("public", "account__status", List.of("B"))
        ));

        var failure = assertThrows(CatalogGenerationException.class,
                () -> CatalogCollisionDetector.check(schema, "gen"));
        assertTrue(failure.getMessage().contains("AccountStatus"), failure.getMessage());
    }

    @Test
    void distinctNamesPass() {
        var schema = new SchemaModel(List.of(
                table("accounts", column("id"), column("user_id")),
                table("plans", column("id"))
        ), List.of(), List.of());

        assertDoesNotThrow(() -> CatalogCollisionDetector.check(schema, "gen"));
    }
}
