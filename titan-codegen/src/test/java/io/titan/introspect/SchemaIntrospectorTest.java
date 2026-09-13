package io.titan.introspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaIntrospectorTest {

    @Test
    void normalizesTypesForPostgresAndMySql() {
        assertEquals("integer", SchemaIntrospector.normalizeType(Dialect.POSTGRESQL, "int4"));
        assertEquals("varchar", SchemaIntrospector.normalizeType(Dialect.POSTGRESQL, "character varying"));
        assertEquals("timestamptz", SchemaIntrospector.normalizeType(Dialect.POSTGRESQL, "timestamp with time zone"));

        assertEquals("integer", SchemaIntrospector.normalizeType(Dialect.MYSQL, "int"));
        assertEquals("timestamp", SchemaIntrospector.normalizeType(Dialect.MYSQL, "datetime"));
    }

    @Test
    void readsTablesColumnsConstraintsForeignKeysAndIndexesFromInformationSchema() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement enumStmt = mock(PreparedStatement.class);
        PreparedStatement tablesStmt = mock(PreparedStatement.class);
        PreparedStatement viewsStmt = mock(PreparedStatement.class);
        PreparedStatement columnsStmt = mock(PreparedStatement.class);
        PreparedStatement constraintsStmt = mock(PreparedStatement.class);
        PreparedStatement foreignKeysStmt = mock(PreparedStatement.class);
        PreparedStatement indexesStmt = mock(PreparedStatement.class);

        ResultSet enumRs = mock(ResultSet.class);
        ResultSet tablesRs = mock(ResultSet.class);
        ResultSet viewsRs = mock(ResultSet.class);
        ResultSet columnsRs = mock(ResultSet.class);
        ResultSet constraintsRs = mock(ResultSet.class);
        ResultSet foreignKeysRs = mock(ResultSet.class);
        ResultSet indexesRs = mock(ResultSet.class);

        when(connection.prepareStatement(anyString()))
                .thenReturn(enumStmt)
                .thenReturn(tablesStmt)
                .thenReturn(viewsStmt)
                .thenReturn(columnsStmt)
                .thenReturn(constraintsStmt)
                .thenReturn(foreignKeysStmt)
                .thenReturn(indexesStmt);

        when(enumStmt.executeQuery()).thenReturn(enumRs);
        when(tablesStmt.executeQuery()).thenReturn(tablesRs);
        when(viewsStmt.executeQuery()).thenReturn(viewsRs);
        when(columnsStmt.executeQuery()).thenReturn(columnsRs);
        when(constraintsStmt.executeQuery()).thenReturn(constraintsRs);
        when(foreignKeysStmt.executeQuery()).thenReturn(foreignKeysRs);
        when(indexesStmt.executeQuery()).thenReturn(indexesRs);

        when(enumRs.next()).thenReturn(false);

        when(tablesRs.next()).thenReturn(true, false);
        when(tablesRs.getString("table_schema")).thenReturn("public");
        when(tablesRs.getString("table_name")).thenReturn("accounts");

        when(viewsRs.next()).thenReturn(false);

        when(columnsRs.next()).thenReturn(true, true, false);
        when(columnsRs.getString("column_name")).thenReturn("id", "email");
        when(columnsRs.getString("data_type")).thenReturn("int4", "character varying");
        when(columnsRs.getString("is_nullable")).thenReturn("NO", "YES");
        when(columnsRs.getString("is_identity")).thenReturn("YES", "NO");
        when(columnsRs.getString("column_default")).thenReturn((String) null, (String) null);

        when(columnsRs.getInt("numeric_precision")).thenReturn(32, 0);
        when(columnsRs.getInt("numeric_scale")).thenReturn(0, 0);
        when(columnsRs.getInt("character_maximum_length")).thenReturn(0, 255);
        when(columnsRs.wasNull()).thenReturn(false, false, true, true, true, false);

        when(constraintsRs.next()).thenReturn(true, true, false);
        when(constraintsRs.getString("constraint_name")).thenReturn("accounts_pkey", "accounts_email_key");
        when(constraintsRs.getString("constraint_type")).thenReturn("PRIMARY KEY", "UNIQUE");
        when(constraintsRs.getString("column_name")).thenReturn("id", "email");

        when(foreignKeysRs.next()).thenReturn(false);

        when(indexesRs.next()).thenReturn(true, false);
        when(indexesRs.getString("index_name")).thenReturn("idx_accounts_email");
        when(indexesRs.getBoolean("is_unique")).thenReturn(true);
        when(indexesRs.getString("column_name")).thenReturn("email");

        SchemaModel model = new SchemaIntrospector().introspect(connection, Dialect.POSTGRESQL, List.of("public"));

        assertEquals(1, model.tables().size());
        var table = model.tables().getFirst();
        assertEquals("accounts", table.name());
        assertEquals(2, table.columns().size());
        assertEquals("integer", table.columns().get(0).sqlType());
        assertTrue(table.columns().get(0).autoIncrement());
        assertTrue(table.columns().get(1).nullable());
        assertEquals(false, table.columns().get(1).autoIncrement());

        assertEquals(2, table.constraints().size());
        assertTrue(table.constraints().stream().anyMatch(c -> c.type() == SchemaModel.ConstraintType.PRIMARY_KEY && c.columns().equals(List.of("id"))));

        assertEquals(1, table.indexes().size());
        assertEquals("idx_accounts_email", table.indexes().get(0).name());
        assertTrue(table.indexes().get(0).unique());
        assertEquals(List.of("email"), table.indexes().get(0).columns());

        assertTrue(table.foreignKeys().isEmpty());
    }

    @Test
    void introspectsMultipleSchemasAndPreservesCrossSchemaForeignKeyTargets() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement enumStmt = mock(PreparedStatement.class);
        PreparedStatement tablesStmt = mock(PreparedStatement.class);
        PreparedStatement viewsStmt = mock(PreparedStatement.class);
        PreparedStatement columnsStmt = mock(PreparedStatement.class);
        PreparedStatement constraintsStmt = mock(PreparedStatement.class);
        PreparedStatement foreignKeysStmt = mock(PreparedStatement.class);
        PreparedStatement indexesStmt = mock(PreparedStatement.class);

        ResultSet enumRs = mock(ResultSet.class);
        ResultSet tablesRs = mock(ResultSet.class);
        ResultSet viewsRs = mock(ResultSet.class);
        ResultSet accountsColumnsRs = mock(ResultSet.class);
        ResultSet plansColumnsRs = mock(ResultSet.class);
        ResultSet accountsConstraintsRs = mock(ResultSet.class);
        ResultSet plansConstraintsRs = mock(ResultSet.class);
        ResultSet accountsForeignKeysRs = mock(ResultSet.class);
        ResultSet plansForeignKeysRs = mock(ResultSet.class);
        ResultSet accountsIndexesRs = mock(ResultSet.class);
        ResultSet plansIndexesRs = mock(ResultSet.class);

        when(connection.prepareStatement(anyString()))
                .thenReturn(enumStmt)
                .thenReturn(tablesStmt)
                .thenReturn(viewsStmt)
                .thenReturn(columnsStmt)
                .thenReturn(constraintsStmt)
                .thenReturn(foreignKeysStmt)
                .thenReturn(indexesStmt)
                .thenReturn(columnsStmt)
                .thenReturn(constraintsStmt)
                .thenReturn(foreignKeysStmt)
                .thenReturn(indexesStmt);

        when(enumStmt.executeQuery()).thenReturn(enumRs);
        when(tablesStmt.executeQuery()).thenReturn(tablesRs);
        when(viewsStmt.executeQuery()).thenReturn(viewsRs);
        when(columnsStmt.executeQuery()).thenReturn(accountsColumnsRs, plansColumnsRs);
        when(constraintsStmt.executeQuery()).thenReturn(accountsConstraintsRs, plansConstraintsRs);
        when(foreignKeysStmt.executeQuery()).thenReturn(accountsForeignKeysRs, plansForeignKeysRs);
        when(indexesStmt.executeQuery()).thenReturn(accountsIndexesRs, plansIndexesRs);

        when(enumRs.next()).thenReturn(false);

        when(tablesRs.next()).thenReturn(true, true, false);
        when(tablesRs.getString("table_schema")).thenReturn("public", "app");
        when(tablesRs.getString("table_name")).thenReturn("accounts", "plans");

        when(viewsRs.next()).thenReturn(false);

        when(accountsColumnsRs.next()).thenReturn(true, true, false);
        when(accountsColumnsRs.getString("column_name")).thenReturn("id", "plan_id");
        when(accountsColumnsRs.getString("data_type")).thenReturn("int4", "int4");
        when(accountsColumnsRs.getString("is_nullable")).thenReturn("NO", "YES");
        when(accountsColumnsRs.getString("is_identity")).thenReturn("YES", "NO");
        when(accountsColumnsRs.getString("column_default")).thenReturn((String) null, (String) null);
        when(accountsColumnsRs.getInt("numeric_precision")).thenReturn(32, 32);
        when(accountsColumnsRs.getInt("numeric_scale")).thenReturn(0, 0);
        when(accountsColumnsRs.getInt("character_maximum_length")).thenReturn(0, 0);
        when(accountsColumnsRs.wasNull()).thenReturn(false, false, true, true, true, true);

        when(plansColumnsRs.next()).thenReturn(true, false);
        when(plansColumnsRs.getString("column_name")).thenReturn("id");
        when(plansColumnsRs.getString("data_type")).thenReturn("int4");
        when(plansColumnsRs.getString("is_nullable")).thenReturn("NO");
        when(plansColumnsRs.getString("is_identity")).thenReturn("YES");
        when(plansColumnsRs.getString("column_default")).thenReturn((String) null);
        when(plansColumnsRs.getInt("numeric_precision")).thenReturn(32);
        when(plansColumnsRs.getInt("numeric_scale")).thenReturn(0);
        when(plansColumnsRs.getInt("character_maximum_length")).thenReturn(0);
        when(plansColumnsRs.wasNull()).thenReturn(false, true, true);

        when(accountsConstraintsRs.next()).thenReturn(true, false);
        when(accountsConstraintsRs.getString("constraint_name")).thenReturn("accounts_pkey");
        when(accountsConstraintsRs.getString("constraint_type")).thenReturn("PRIMARY KEY");
        when(accountsConstraintsRs.getString("column_name")).thenReturn("id");

        when(plansConstraintsRs.next()).thenReturn(true, false);
        when(plansConstraintsRs.getString("constraint_name")).thenReturn("plans_pkey");
        when(plansConstraintsRs.getString("constraint_type")).thenReturn("PRIMARY KEY");
        when(plansConstraintsRs.getString("column_name")).thenReturn("id");

        when(accountsForeignKeysRs.next()).thenReturn(true, false);
        when(accountsForeignKeysRs.getString("constraint_name")).thenReturn("fk_accounts_plan");
        when(accountsForeignKeysRs.getString("column_name")).thenReturn("plan_id");
        when(accountsForeignKeysRs.getString("referenced_table_schema")).thenReturn("app");
        when(accountsForeignKeysRs.getString("referenced_table_name")).thenReturn("plans");
        when(accountsForeignKeysRs.getString("referenced_column_name")).thenReturn("id");

        when(plansForeignKeysRs.next()).thenReturn(false);

        when(accountsIndexesRs.next()).thenReturn(false);
        when(plansIndexesRs.next()).thenReturn(false);

        SchemaModel model = new SchemaIntrospector().introspect(connection, Dialect.POSTGRESQL, List.of("public", "app"));

        assertEquals(2, model.tables().size());
        assertTrue(model.tables().stream().anyMatch(t -> t.schema().equals("public") && t.name().equals("accounts")));
        assertTrue(model.tables().stream().anyMatch(t -> t.schema().equals("app") && t.name().equals("plans")));

        var accounts = model.tables().stream()
                .filter(t -> t.schema().equals("public") && t.name().equals("accounts"))
                .findFirst()
                .orElseThrow();

        assertEquals(1, accounts.foreignKeys().size());
        var fk = accounts.foreignKeys().getFirst();
        assertEquals("app", fk.referencedSchema());
        assertEquals("plans", fk.referencedTable());
        assertEquals(List.of("plan_id"), fk.columns());
        assertEquals(List.of("id"), fk.referencedColumns());
    }
}
