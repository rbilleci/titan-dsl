package io.titan.catalog;

import io.titan.introspect.SchemaModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewDescriptorGeneratorTest {

    @Test
    void generatesViewDescriptorWithSingletonAndTypedColumns() {
        var activeAccounts = new SchemaModel.ViewMeta(
                "public",
                "active_accounts",
                List.of(
                        new SchemaModel.ColumnMeta("account_id", "integer", false, null, null, null),
                        new SchemaModel.ColumnMeta("email", "varchar", true, null, null, 255),
                        new SchemaModel.ColumnMeta("last_login", "date", true, null, null, null)
                )
        );

        var schema = new SchemaModel(List.of(), List.of(activeAccounts), List.of());

        var generated = new ViewDescriptorGenerator().generate(schema, "com.example.generated.titan");
        assertEquals(1, generated.size());

        String source = generated.get("com/example/generated/titan/public_/views/ActiveAccounts.java");
        assertTrue(source.contains("@Generated(\"titan-generator\")"));
        assertTrue(source.contains("public final class ActiveAccounts extends View<ActiveAccountsRecord>"));
        assertTrue(source.contains("public static final ActiveAccounts ACTIVE_ACCOUNTS = new ActiveAccounts();"));

        assertTrue(source.contains("public final Column<Integer> ACCOUNT_ID"));
        assertTrue(source.contains("public final Column<String> EMAIL"));
        assertTrue(source.contains("public final Column<java.time.LocalDate> LAST_LOGIN"));
        assertTrue(source.contains("super(\"active_accounts\", \"public\")"));
    }
}
