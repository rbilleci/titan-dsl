package example;

import static generated.catalog.app.tables.Users.USERS;
import titan.dsl.DSL;
import titan.dsl.SqlDialect;

/** Generates its catalog during compilation; only renders SQL, never contacts a database. */
public final class GeneratedCatalogQuery {
    public static void main(String[] args) {
        var db = DSL.using(SqlDialect.POSTGRESQL);
        var sql = db.select(USERS.ID, USERS.NAME).from(USERS)
                .where(USERS.ACTIVE.eq(true).and(USERS.COUNTRY.eq("NL")))
                .orderBy(USERS.NAME.asc()).limit(20).offset(40)
                .render();
        String expected = "SELECT id, name FROM app.users WHERE (active = ?) AND (country = ?) "
                + "ORDER BY name ASC LIMIT 20 OFFSET 40";
        if (!expected.equals(sql.sql()) || sql.parameters().size() != 2
                || !Boolean.TRUE.equals(sql.parameters().get(0).value())
                || !"NL".equals(sql.parameters().get(1).value())) {
            throw new AssertionError(sql);
        }
        System.out.println(sql.sql());
        System.out.println(sql.parameters());
    }
}
