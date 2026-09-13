// SPDX-License-Identifier: GPL-3.0-only
package example;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import titan.dsl.BindValue;
import titan.dsl.Column;
import titan.dsl.DSL;
import titan.dsl.DSLContext;
import titan.dsl.Nullability;
import titan.dsl.ParameterizedSql;
import titan.dsl.SQLType;
import titan.dsl.SqlDialect;
import titan.dsl.Table;

/** Executable recipes for the README and developer guide; no database is required. */
public final class FeatureExamples {
    private FeatureExamples() {}

    public static void main(String[] args) {
        var users = new Users();
        for (var dialect : SqlDialect.values()) {
            var db = DSL.using(dialect);
            var filters = users.active.eq(true);
            String country = "NL";
            if (country != null) {
                filters = filters.and(users.country.eq(country));
            }
            check("filters", dialect, db.select(users.id, users.name).from(users)
                            .where(filters).orderBy(users.name.asc()).limit(20).offset(40).render(),
                    "SELECT id, name FROM app.users WHERE (active = ?) AND (country = ?)"
                            + " ORDER BY name ASC LIMIT 20 OFFSET 40", true, "NL");

            var employee = users.as("employee");
            var manager = users.as("manager");
            check("self-join", dialect,
                    db.select(employee.col(users.name), manager.col(users.name))
                            .from(employee).leftJoin(manager)
                            .on(employee.col(users.managerId), manager.col(users.id)).render(),
                    "SELECT employee.name, manager.name FROM app.users AS employee"
                            + " LEFT JOIN app.users AS manager ON employee.manager_id = manager.id");

            var count = DSL.count();
            check("aggregation", dialect, db.select(users.country, count).from(users)
                            .groupBy(users.country).having(count.gt(10L)).render(),
                    "SELECT country, COUNT(*) FROM app.users GROUP BY country HAVING COUNT(*) > ?", 10L);

            var upsert = db.insertInto(users).set(users.id, 7).set(users.name, "Ada")
                    .onConflict(users.id).doUpdate().set(users.name, "Ada Lovelace");
            check("upsert", dialect, upsert.render(),
                    "INSERT INTO app.users (id, name) VALUES (?, ?) "
                            + (dialect == SqlDialect.POSTGRESQL
                            ? "ON CONFLICT (id) DO UPDATE SET name = ?"
                            : "ON DUPLICATE KEY UPDATE name = ?"), 7, "Ada", "Ada Lovelace");

            var directory = db.name("directory").as(db.select(users.id, users.name).from(users));
            var directoryName = directory.field("name", String.class);
            check("cte", dialect, db.with(directory).select(directoryName).from(directory)
                            .where(directoryName.eq("Ada")).render(),
                    "WITH directory AS (SELECT id, name FROM app.users)"
                            + " SELECT directory.name FROM directory WHERE directory.name = ?", "Ada");

            var position = DSL.rowNumber().over(DSL.partitionBy(users.country).orderBy(users.id.asc()));
            check("window", dialect, db.select(users.name, position).from(users).render(),
                    "SELECT name, ROW_NUMBER() OVER (PARTITION BY country ORDER BY id ASC) FROM app.users");

            var activeIds = db.select(users.id).from(users).where(users.active.eq(true));
            check("subquery", dialect, db.select(users.name).from(users)
                            .where(users.id.in(activeIds)).render(),
                    "SELECT name FROM app.users WHERE id IN (SELECT id FROM app.users WHERE active = ?)", true);

            var label = DSL.when(users.active.eq(true), "active").otherwise("inactive");
            check("case", dialect, db.select(users.name, label).from(users).render(),
                    "SELECT name, CASE WHEN active = ? THEN ? ELSE ? END FROM app.users",
                    true, "active", "inactive");

            check("batch-insert", dialect, db.insertInto(users).columns(users.id, users.name)
                            .values(7, "Ada").values(8, "Grace").render(),
                    "INSERT INTO app.users (id, name) VALUES (?, ?), (?, ?)", 7, "Ada", 8, "Grace");
            check("update", dialect, db.update(users).set(users.name, "Grace")
                            .where(users.id.eq(8)).render(),
                    "UPDATE app.users SET name = ? WHERE id = ?", "Grace", 8);
            check("delete", dialect, db.deleteFrom(users).where(users.id.eq(8)).render(),
                    "DELETE FROM app.users WHERE id = ?", 8);

            // Legacy static DSL.name captures literal SQL; db.name retains structured bindings.
            var activeDirectory = DSL.name("active_directory")
                    .as(db.select(users.name).from(users).where(users.active.eq(true)));
            var activeName = activeDirectory.field("name", String.class);
            check("cte-literal-boundary", dialect, db.with(activeDirectory).select(activeName)
                            .from(activeDirectory).where(activeName.eq("Ada")).render(),
                    "WITH active_directory AS (SELECT name FROM app.users WHERE active = TRUE)"
                            + " SELECT active_directory.name FROM active_directory WHERE active_directory.name = ?",
                    "Ada");
        }
    }

    /** Optional JDBC integration: caller supplies the connection; not invoked by the demo. */
    public static List<String> findUserNames(DSLContext db, Connection connection, String name) throws SQLException {
        Objects.requireNonNull(db, "db");
        Objects.requireNonNull(name, "name");
        var users = new Users();
        var rendered = db.select(users.name).from(users)
                .where(users.name.eq(name)).render();
        var names = new ArrayList<String>();
        try (var statement = connection.prepareStatement(rendered.sql())) {
            // This query has exactly one non-null VARCHAR parameter.
            statement.setString(1, (String) rendered.parameters().getFirst().value());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    names.add(rows.getString("name"));
                }
            }
        }
        return names;
    }

    private static void check(String feature, SqlDialect dialect, ParameterizedSql rendered,
                              String expectedSql, Object... expectedValues) {
        var values = rendered.parameters().stream().map(BindValue::value).toList();
        if (!rendered.sql().equals(expectedSql) || !values.equals(List.of(expectedValues))) {
            throw new IllegalStateException(feature + " / " + dialect + ": " + rendered);
        }
        System.out.println(feature + " / " + dialect);
        System.out.println(rendered.sql());
        System.out.println(values);
    }

    public static final class Users extends Table<Object> {
        public final Column<Integer> id = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        public final Column<String> name = column("name", SQLType.VARCHAR, Nullability.NOT_NULL);
        public final Column<Boolean> active = column("active", SQLType.BOOLEAN, Nullability.NOT_NULL);
        public final Column<String> country = column("country", SQLType.VARCHAR, Nullability.NOT_NULL);
        public final Column<Integer> managerId = column("manager_id", SQLType.INTEGER, Nullability.NULLABLE);

        public Users() {
            super("users", "app");
        }
    }
}
