package io.titan.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CatalogNamingTest {

    @Test
    void keywordColumnsGetKeywordSafeFieldNames() {
        assertEquals("class_", CatalogNaming.toJavaFieldName("class"));
        assertEquals("int_", CatalogNaming.toJavaFieldName("int"));
        assertEquals("default_", CatalogNaming.toJavaFieldName("default"));
        assertEquals("true_", CatalogNaming.toJavaFieldName("true"));
        assertEquals("userId", CatalogNaming.toJavaFieldName("user_id"));
    }

    @Test
    void fieldNamesNeverStartWithADigit() {
        assertEquals("_2faEnabled", CatalogNaming.toJavaFieldName("2fa_enabled"));
        assertEquals("_2FA_ENABLED", CatalogNaming.toConstantName("2fa_enabled"));
        assertEquals("_2faCodes", CatalogNaming.toClassName("2fa_codes", "Unnamed"));
    }

    @Test
    void classNamesSanitizeArbitraryDatabaseIdentifiers() {
        assertEquals("UserAccount", CatalogNaming.toClassName("user_account", "Unnamed"));
        assertEquals("UserAccount", CatalogNaming.toClassName("user__account", "Unnamed"));
        // The G-8 regression: raw MySQL enum column types must never become file names.
        assertEquals("EnumAB", CatalogNaming.toClassName("enum('a','b')", "Unnamed"));
        assertEquals("Unnamed", CatalogNaming.toClassName("$$$", "Unnamed"));
    }

    @Test
    void keywordSchemasGetSafePackageSegments() {
        assertEquals("public_", CatalogNaming.schemaPackageSegment("public"));
        assertEquals("default_", CatalogNaming.schemaPackageSegment(null));
        assertEquals("_2025_archive", CatalogNaming.schemaPackageSegment("2025-archive"));
    }
}
