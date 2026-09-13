// SPDX-License-Identifier: GPL-3.0-only
package example;

import java.util.List;
import titan.dsl.BindValue;
import titan.dsl.Column;
import titan.dsl.DSL;
import titan.dsl.Nullability;
import titan.dsl.SQLType;
import titan.dsl.SqlDialect;
import titan.dsl.Table;

/** Renders a bound query without JDBC or a running database. */
public final class Quickstart {
    private Quickstart() {}

    public static void main(String[] args) {
        var users = new Users();
        for (var dialect : SqlDialect.values()) {
            var db = DSL.using(dialect);
            var rendered = db.select(users.id, users.name)
                    .from(users)
                    .where(users.name.eq("Ada"))
                    .render();
            var values = rendered.parameters().stream().map(BindValue::value).toList();

            // Keep this documented consumer example executable as a local smoke check.
            if (!rendered.sql().equals("SELECT id, name FROM app.users WHERE name = ?")
                    || !values.equals(List.of("Ada"))) {
                throw new IllegalStateException("Unexpected quickstart rendering for " + dialect);
            }
            System.out.println(dialect);
            System.out.println(rendered.sql());
            System.out.println(values);
        }
    }

    private static final class Users extends Table<Object> {
        final Column<Integer> id = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        final Column<String> name = column("name", SQLType.VARCHAR, Nullability.NOT_NULL);

        Users() {
            super("users", "app");
        }
    }
}
