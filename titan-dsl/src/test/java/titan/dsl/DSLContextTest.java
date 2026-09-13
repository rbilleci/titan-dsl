package titan.dsl;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DSLContextTest {
    private static final Users USERS = new Users();

    @Test void requiresExplicitConfigurationWithoutChangingLegacyDefaults() {
        assertThrows(NullPointerException.class, () -> DSL.using(null));
        var query = DSL.select(USERS.ID).from(USERS);
        assertTrue(assertThrows(IllegalStateException.class, query::render).getMessage().contains("DSL.using"));
        assertThrows(IllegalStateException.class, () -> DSL.insertInto(USERS).set(USERS.ID, 1).render());
        assertThrows(IllegalStateException.class, () -> DSL.update(USERS).set(USERS.ID, 1).render());
        assertThrows(IllegalStateException.class, () -> DSL.deleteFrom(USERS).render());
        assertEquals(query.toSql(SqlDialect.POSTGRESQL), query.toSql());
        assertEquals("SELECT id FROM app.users", query.render(SqlDialect.MYSQL).sql());
    }

    @Test void freshQueriesCarryContextThroughFluentClausesAndAllDmlFactories() {
        for (var dialect : SqlDialect.values()) {
            var db = DSL.using(dialect);
            assertEquals(dialect, db.dialect());
            var query = db.select(USERS.ID, USERS.NAME).from(USERS)
                    .where(USERS.ACTIVE.eq(true)).orderBy(USERS.NAME.asc()).limit(3).offset(2);
            assertEquals("SELECT id, name FROM app.users WHERE active = ? ORDER BY name ASC LIMIT 3 OFFSET 2",
                    query.render().sql());
            assertEquals(List.of(true), values(query.render()));
            assertEquals("SELECT id FROM app.users", db.select(USERS.ID).from(USERS).render().sql());
            assertTrue(db.selectFrom(USERS).render().sql().contains("FROM app.users"));
            assertEquals(List.of(1, "Ada", 2, "Grace"), values(db.insertInto(USERS)
                    .columns(USERS.ID, USERS.NAME).values(1, "Ada").values(2, "Grace").render()));
            assertEquals(List.of("Ada", 1), values(db.update(USERS).set(USERS.NAME, "Ada")
                    .where(USERS.ID.eq(1)).render()));
            assertEquals(List.of(1), values(db.deleteFrom(USERS).where(USERS.ID.eq(1)).render()));
            var employee = USERS.as("e");
            var manager = USERS.as("m");
            assertTrue(db.select(employee.col(USERS.NAME)).from(employee).leftJoin(manager)
                    .on(employee.col(USERS.ID), manager.col(USERS.ID)).render().sql().contains("LEFT JOIN"));
        }
    }

    @Test void dialectControlsUpsertsAndReturningAndExplicitOverrideIsLocal() {
        var pg = DSL.using(SqlDialect.POSTGRESQL);
        var mysql = DSL.using(SqlDialect.MYSQL);
        var query = mysql.insertInto(USERS).set(USERS.ID, 1).onConflict(USERS.ID)
                .doUpdate().set(USERS.NAME, "Ada");
        assertTrue(query.render().sql().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(query.render(SqlDialect.POSTGRESQL).sql().contains("ON CONFLICT"));
        assertTrue(query.render().sql().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(query.toSql().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(query.execute().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(pg.deleteFrom(USERS).returning(USERS.ID).render().sql().endsWith("RETURNING id"));
        assertThrows(IllegalStateException.class, () -> mysql.deleteFrom(USERS).returning(USERS.ID).render());
        assertThrows(IllegalStateException.class, () -> mysql.update(USERS).set(USERS.ID, 1).returning(USERS.ID).render());
        assertFalse(mysql.insertInto(USERS).set(USERS.ID, 1).returning(USERS.ID).render().sql().contains("RETURNING"));
    }

    @Test void literalAndFetchConveniencesUseTheContextDialect() {
        var mysql = DSL.using(SqlDialect.MYSQL);
        var query = mysql.select(USERS.NAME).from(USERS).where(USERS.NAME.eq("C:\\tmp"));
        String sql = query.toSql(SqlDialect.MYSQL);
        assertEquals(sql, query.toSql());
        assertEquals(sql, query.fetch());
        assertEquals(sql + " LIMIT 1", query.fetchOne());
        assertEquals("SELECT COUNT(*) FROM (" + sql + ") titan_count", query.fetchCount());
        assertEquals("SELECT EXISTS (" + sql + ")", query.fetchExists());
        assertNotEquals(query.toSql(SqlDialect.POSTGRESQL), sql);
        assertEquals(List.of("C:\\tmp"), values(query.render()));
    }

    @Test void outerDialectControlsStructuredSubqueriesWithoutMutatingTheirContext() {
        var mysql = DSL.using(SqlDialect.MYSQL);
        var child = DSL.using(SqlDialect.POSTGRESQL).select(USERS.ID).from(USERS)
                .where(USERS.NAME.eq("C:\\tmp"));
        var outer = mysql.select(USERS.ID).from(USERS).where(USERS.ID.in(child).and(DSL.exists(child)));
        assertEquals(List.of("C:\\tmp", "C:\\tmp"), values(outer.render()));
        assertTrue(outer.toSql().contains(child.toSql(SqlDialect.MYSQL)));
        assertEquals(child.toSql(SqlDialect.POSTGRESQL), child.toSql());
        assertEquals(List.of("C:\\tmp"), values(mysql.insertInto(USERS).columns(USERS.ID).select(child).render()));
        var union = mysql.select(USERS.ID).from(USERS).where(USERS.ID.eq(1)).unionAll(child);
        child.where(USERS.NAME.eq("changed"));
        assertEquals(List.of(1, "C:\\tmp"), values(union.render()));
        assertTrue(union.toSql().contains("C:\\\\tmp"));
    }

    @Test void contextCtesPreserveBindingsDialectAndSnapshotAtComposition() {
        var db = DSL.using(SqlDialect.MYSQL);
        var child = DSL.select(USERS.NAME).from(USERS).where(USERS.NAME.eq("C:\\tmp"));
        var cte = db.name("directory").fields("name").as(child);
        child.where(USERS.NAME.eq("changed"));
        var name = cte.field("name", String.class);
        var outer = db.with(cte).select(name).from(cte).where(name.eq("Ada"));
        assertEquals("WITH directory (name) AS (SELECT name FROM app.users WHERE name = ?) "
                + "SELECT directory.name FROM directory WHERE directory.name = ?", outer.render().sql());
        assertEquals(List.of("C:\\tmp", "Ada"), values(outer.render()));
        assertTrue(outer.toSql().contains("C:\\\\tmp"));
        assertTrue(outer.toSql(SqlDialect.POSTGRESQL).contains("C:\\tmp"));
        assertEquals(List.of("C:\\tmp"), values(db.select(name).from(cte).render()));
        // Static name(...) remains a literal-capture compatibility path.
        var legacy = DSL.name("legacy").as(db.select(USERS.NAME).from(USERS).where(USERS.NAME.eq("legacy")));
        assertTrue(db.with(legacy).select(legacy.field("name", String.class)).from(legacy).render().parameters().isEmpty());
    }

    @Test void recursiveContextCtesPreserveBothBranchesAndTheirBindings() {
        var db = DSL.using(SqlDialect.MYSQL);
        var cte = db.name("tree").fields("id").asRecursive(self ->
                db.select(USERS.ID).from(USERS).where(USERS.ID.eq(1)).unionAll(
                        db.select(self.<Integer>field("id", Integer.class)).from(self)
                                .where(self.<Integer>field("id", Integer.class).lt(10))));
        var result = db.withRecursive(cte).select(cte.field("id", Integer.class)).from(cte).render();
        assertTrue(result.sql().startsWith("WITH RECURSIVE tree (id) AS ("));
        assertEquals(List.of(1, 10), values(result));
    }

    @Test void cteRegistrationDoesNotSilentlyReplaceStructuredBindingsWithLiteralText() {
        var db = DSL.using(SqlDialect.MYSQL);
        var body = db.select(USERS.ID).from(USERS).where(USERS.ID.eq(1));
        var structured = db.name("ids").as(body);
        var raw = DSL.name("ids").asSql(body.toSql());
        assertThrows(IllegalStateException.class, () -> db.with(structured)
                .select(raw.field("id", Integer.class)).from(raw));
        var same = db.name("ids").as(body);
        assertEquals(List.of(1), values(db.with(structured)
                .select(same.field("id", Integer.class)).from(same).render()));
    }

    @Test void allTypedProjectionFactoriesRetainTheirReturnTypes() {
        var db = DSL.using(SqlDialect.MYSQL);
        var c = USERS.ID;
        SelectBuilder1<Integer> q1 = db.select(c).from(USERS);
        SelectBuilder2<Integer, Integer> q2 = db.select(c, c).from(USERS);
        SelectBuilder3<Integer, Integer, Integer> q3 = db.select(c, c, c).from(USERS);
        SelectBuilder4<Integer, Integer, Integer, Integer> q4 = db.select(c, c, c, c).from(USERS);
        SelectBuilder5<Integer, Integer, Integer, Integer, Integer> q5 = db.select(c, c, c, c, c).from(USERS);
        SelectBuilder6<Integer, Integer, Integer, Integer, Integer, Integer> q6 = db.select(c, c, c, c, c, c).from(USERS);
        SelectBuilder7<Integer, Integer, Integer, Integer, Integer, Integer, Integer> q7 = db.select(c, c, c, c, c, c, c).from(USERS);
        SelectBuilder8<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer> q8 = db.select(c, c, c, c, c, c, c, c).from(USERS);
        SelectBuilder9<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer> q9 = db.select(c, c, c, c, c, c, c, c, c).from(USERS);
        SelectBuilder10<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer> q10 = db.select(c, c, c, c, c, c, c, c, c, c).from(USERS);
        for (var query : List.of(q1, q2, q3, q4, q5, q6, q7, q8, q9, q10,
                db.select(new Column<?>[] {c, c, c, c, c, c, c, c, c, c, c}).from(USERS))) {
            assertTrue(query.render().sql().endsWith("FROM app.users"));
        }
    }

    @Test void contextCanBeSharedWhileBuildersAndDialectsRemainIndependent() {
        var pg = DSL.using(SqlDialect.POSTGRESQL);
        var mysql = DSL.using(SqlDialect.MYSQL);
        IntStream.range(0, 40).parallel().forEach(i -> {
            var db = i % 2 == 0 ? pg : mysql;
            var query = db.insertInto(USERS).set(USERS.ID, i).onConflict(USERS.ID).doUpdate().set(USERS.NAME, "n");
            assertEquals(List.of(i, "n"), values(query.render()));
            assertTrue(query.render().sql().contains(i % 2 == 0 ? "ON CONFLICT" : "ON DUPLICATE KEY"));
        });
    }

    private static List<Object> values(ParameterizedSql sql) {
        return sql.parameters().stream().map(BindValue::value).toList();
    }

    public static final class Users extends Table<Object> {
        public final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        public final Column<String> NAME = column("name", SQLType.VARCHAR, Nullability.NOT_NULL);
        public final Column<Boolean> ACTIVE = column("active", SQLType.BOOLEAN, Nullability.NOT_NULL);
        Users() { super("users", "app"); }
    }
}
