package titan.dsl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static titan.dsl.DSL.field;
import static titan.dsl.DSL.scalar;
import static titan.dsl.DSL.select;

final class SubqueryDslTest {

    private static final class AccountsTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> EMAIL = column("email", SQLType.VARCHAR, Nullability.NOT_NULL);

        private AccountsTable() {
            super("accounts", "public");
        }
    }

    @Test
    void scalarTypedOverloadInfersProjectionType() {
        AccountsTable accounts = new AccountsTable();

        SelectBuilder1<Integer> idSubquery = select(accounts.ID).from(accounts).limit(1);
        Column<Integer> scalarColumn = scalar(idSubquery);

        assertEquals(SQLType.INTEGER, scalarColumn.sqlType());
        assertEquals("(SELECT id FROM public.accounts LIMIT 1)", scalarColumn.name());
    }

    @Test
    void scalarDeclaredSqlTypeMustMatchProjectionType() {
        AccountsTable accounts = new AccountsTable();

        SelectBuilder1<Integer> idSubquery = select(accounts.ID).from(accounts);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> scalar(idSubquery, SQLType.VARCHAR));

        assertEquals("scalar subquery projection type must match declared sqlType", ex.getMessage());
    }

    @Test
    void scalarRejectsMultiColumnSubquery() {
        AccountsTable accounts = new AccountsTable();

        SelectBuilder2<Integer, String> projection = select(accounts.ID, accounts.EMAIL).from(accounts);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> scalar(projection, SQLType.INTEGER));

        assertEquals("scalar subquery must project exactly one column", ex.getMessage());
    }

    @Test
    void fieldInfersSqlTypeFromJavaType() {
        Column<Integer> depth = field("org_tree.depth", Integer.class);

        assertEquals("org_tree.depth", depth.name());
        assertEquals(SQLType.INTEGER, depth.sqlType());
        assertEquals(Nullability.NULLABLE, depth.nullability());
    }

    @Test
    void fieldAllowsExplicitSqlType() {
        Column<String> label = field("lateral.last_email", SQLType.VARCHAR);

        assertEquals("lateral.last_email", label.name());
        assertEquals(SQLType.VARCHAR, label.sqlType());
        assertEquals(Nullability.NULLABLE, label.nullability());
    }

    @Test
    void fieldRejectsInvalidArguments() {
        IllegalArgumentException blankName = assertThrows(
                IllegalArgumentException.class,
                () -> field("  ", Integer.class));
        assertEquals("field name must not be blank", blankName.getMessage());

        IllegalArgumentException nullJavaType = assertThrows(
                IllegalArgumentException.class,
                () -> field("depth", (Class<Integer>) null));
        assertEquals("javaType must not be null", nullJavaType.getMessage());

        IllegalArgumentException nullSqlType = assertThrows(
                IllegalArgumentException.class,
                () -> field("depth", (SQLType) null));
        assertEquals("sqlType must not be null", nullSqlType.getMessage());
    }
}
