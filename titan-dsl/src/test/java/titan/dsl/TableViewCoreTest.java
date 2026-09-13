package titan.dsl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableViewCoreTest {

    @Test
    void tableAndViewExposeCoreMetadataAndBuilders() {
        class Accounts extends Table<Object> {
            Accounts() { super("accounts", "public"); }
        }
        class ActiveAccountsView extends View<Object> {
            ActiveAccountsView() { super("active_accounts", "public"); }
        }

        var accounts = new Accounts();
        var view = new ActiveAccountsView();

        assertEquals("accounts", accounts.name());
        assertEquals("public", accounts.schema());
        assertEquals("active_accounts", view.name());

        var id = accounts.column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        var pk = accounts.primaryKey(id);
        var uq = accounts.uniqueKey(id);

        assertTrue(pk.primary());
        assertFalse(uq.primary());
        assertEquals(1, pk.columns().size());
    }
}
