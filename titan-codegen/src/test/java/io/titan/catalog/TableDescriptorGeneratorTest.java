package io.titan.catalog;

import io.titan.introspect.SchemaModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableDescriptorGeneratorTest {

    @Test
    void generatesDescriptorWithSingletonColumnsKeysAndForeignKeys() {
        var accounts = new SchemaModel.TableMeta(
                "public",
                "accounts",
                List.of(
                        new SchemaModel.ColumnMeta("id", "integer", false, null, null, null),
                        new SchemaModel.ColumnMeta("email", "varchar", false, null, null, 255),
                        new SchemaModel.ColumnMeta("active", "boolean", false, null, null, null),
                        new SchemaModel.ColumnMeta("plan_id", "integer", true, null, null, null)
                ),
                List.of(
                        new SchemaModel.ConstraintMeta("accounts_pkey", SchemaModel.ConstraintType.PRIMARY_KEY, List.of("id")),
                        new SchemaModel.ConstraintMeta("accounts_email_key", SchemaModel.ConstraintType.UNIQUE, List.of("email"))
                ),
                List.of(
                        new SchemaModel.ForeignKeyMeta("accounts_plan_id_fkey", List.of("plan_id"), "public", "plans", List.of("id"))
                ),
                List.of()
        );

        var plans = new SchemaModel.TableMeta(
                "public",
                "plans",
                List.of(new SchemaModel.ColumnMeta("id", "integer", false, null, null, null)),
                List.of(new SchemaModel.ConstraintMeta("plans_pkey", SchemaModel.ConstraintType.PRIMARY_KEY, List.of("id"))),
                List.of(),
                List.of()
        );

        var schema = new SchemaModel(List.of(accounts, plans), List.of(), List.of());

        var generated = new TableDescriptorGenerator().generate(schema, "com.example.generated.titan");
        assertEquals(2, generated.size());

        String source = generated.get("com/example/generated/titan/public_/tables/Accounts.java");
        assertTrue(source.contains("@Generated(\"titan-generator\")"));
        assertTrue(source.contains("@PhysicalTable(name = \"accounts\", schema = \"public\")"));
        assertTrue(source.contains("public final class Accounts extends Table<AccountsRecord>"));
        assertTrue(source.contains("public static final Accounts ACCOUNTS = new Accounts();"));

        assertTrue(source.contains("@PhysicalColumn(\"id\")\n    public final Column<Integer> ID"));
        assertTrue(source.contains("@PhysicalColumn(\"email\")\n    public final Column<String> EMAIL"));
        assertTrue(source.contains("@PhysicalColumn(\"active\")\n    public final Column<Boolean> ACTIVE"));
        assertTrue(source.contains("@PhysicalColumn(\"plan_id\")\n    public final Column<Integer> PLAN_ID"));

        assertTrue(source.contains("public final UniqueKey<AccountsRecord> PK = primaryKey(ID);"));
        assertTrue(source.contains("public final UniqueKey<AccountsRecord> ACCOUNTS_EMAIL_KEY = uniqueKey(EMAIL);"));
        assertTrue(source.contains("public final ForeignKey<AccountsRecord, PlansRecord> ACCOUNTS_PLAN_ID_FKEY = foreignKey(PLAN_ID, Plans.PLANS, Plans.PLANS.ID);"));
    }
}
