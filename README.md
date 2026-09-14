# Titan DSL

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

**Turn your database schema into a Java API for SQL.**

Generate typed tables, columns, relationships, and row records from your schema,
then use them to compose queries in Java. Titan DSL provides the query API and
PostgreSQL/MySQL rendering; the companion schema generator supplies the Java
catalog. Your application keeps control of connections, transactions, and results.

```java
import static generated.catalog.app.tables.Users.USERS;
import titan.dsl.DSL;
import titan.dsl.SqlDialect;

var db = DSL.using(SqlDialect.POSTGRESQL); // configure once, reuse for queries
var sql = db.select(USERS.ID, USERS.NAME)
        .from(USERS)
        .where(USERS.ACTIVE.eq(true).and(USERS.COUNTRY.eq("NL")))
        .orderBy(USERS.NAME.asc())
        .limit(20)
        .offset(40)
        .render();
```

```sql
SELECT id, name FROM app.users
WHERE (active = ?) AND (country = ?)
ORDER BY name ASC LIMIT 20 OFFSET 40
```

Bind values: `[true, "NL"]`. `USERS` and its columns come from the generated
catalog; the [schema-generation walkthrough](#generate-your-java-catalog-from-the-schema)
below shows the input, setup, and outputs.

**One repository, separate artifacts:** the Java 21 DSL library has no third-party
runtime dependencies. This checkout also contains `titan-codegen` and the
generation-only `io.titan.codegen` Gradle plugin. They belong in your build, not
your application's runtime classpath, and require no Titan core checkout.
Public registry releases are not configured yet; the setup below uses source.
The library also accepts hand-written descriptors for small examples.

## Why use Titan DSL?

### Make your schema the source of your Java query API

Maintain table names, column types, nullability, and relationships in the database,
then regenerate the Java catalog from that metadata. Query code refers to fields
such as `USERS.NAME` instead of repeating schema declarations by hand. After a
migration, regenerate into a clean output directory and compile: removed or renamed
fields and incompatible Java type changes can surface at the query's call sites.
This brings schema changes into the development feedback loop.

### Keep complex queries readable

Express reports in familiar SQL terms: joins, grouping, thresholds, CTEs, and
window functions. The Java follows the structure of the SQL, so a reviewer can
trace a query from its source tables to its result. See [aggregation and windows](#aggregation-and-windows).

### Make changing requirements easier to handle

Represent business predicates as reusable conditions and combine optional filters
with ordinary Java control flow. Structured conditions retain their values for
parameterized rendering, reducing string assembly and bind-index bookkeeping.
See [query construction](#construct-and-render-queries).

### Catch value-type mistakes while you code

A generated `Column<Integer>` accepts `eq(7)`, but not `eq("seven")`.
Typed comparisons and assignments give the Java compiler useful checks before
execution. Generation connects those declarations to the introspected schema;
it does not certify every SQL expression or detect later database drift at runtime.

### Know what you send to the database

Inspect rendered SQL and ordered bind values, assert them in tests, and choose
the dialect explicitly. PostgreSQL and MySQL [upserts](#inserts-updates-deletes-and-upserts)
use their respective syntax. Your application decides when and how SQL executes.

### Apply tenant and ownership filters automatically

Declare once which columns or foreign-key paths carry a tenant, organization,
or user scope. The context adds those predicates to every SELECT, UPDATE, and
DELETE it renders, and a table with no binding is rejected when the policy is
built rather than queried unfiltered. See [automatic filters](#automatic-filters).

### Adopt it query by query

Start with a search endpoint or report, generate the catalog for the schemas it
uses, and keep your existing JDBC and transaction setup. The generation tools
belong in the build; the DSL library remains a small runtime dependency.

## Contents

- [Install and try the library](#installation)
- [Generate a catalog from your schema](#generate-your-java-catalog-from-the-schema)
- [Understand table descriptors](#define-a-table-once)
- [Construct and render queries](#construct-and-render-queries)
- [Joins](#joins-and-aliases), [reporting](#aggregation-and-windows), [subqueries and CASE](#subqueries-and-case-expressions)
- [CTEs and set operations](#ctes-and-set-operations), [DML and upserts](#inserts-updates-deletes-and-upserts)
- [Automatic filters](#automatic-filters)
- [JDBC execution](#execute-through-your-applications-jdbc-connection)
- [Method behavior](#typed-projections-and-convenience-methods), [dialect and safety limits](#dialect-and-safety-limits)
- [Development and troubleshooting](#development-and-troubleshooting)

## Installation

The current source coordinate is `io.titan:titan-dsl:0.1.0`; this is an early API,
not a promise that a registry artifact exists. Install JDK 21 and set `JAVA_HOME`.

```bash
git clone https://github.com/rbilleci/titan-dsl.git
cd titan-dsl
./gradlew build
./gradlew -p examples/schema-codegen build run
./gradlew -p examples/quickstart run runFeatures
```

Use `gradlew.bat` on Windows. The wrapper downloads Gradle/build dependencies on
the first run. The schema-first example generates its catalog from a small DDL
fixture; the quickstart uses hand-written descriptors. Neither needs Docker or
a connected database. JDBC and container-backed generation are described below.

Choose one consumption path:

| Path | Application settings | Application dependencies |
| --- | --- | --- |
| Source checkout | Add `includeBuild("../titan-dsl")` to `settings.gradle.kts`, adjusting the path. | Add `implementation("io.titan:titan-dsl:0.1.0")`. |
| Local Maven artifact | First run `./gradlew publishToMavenLocal` here; add `mavenLocal()` to the application's repositories and omit the included build. | Use the same coordinate. |

The included-build path uses current source. Maven-local artifacts must be
republished after changes; they are not a public release channel. The
[example build](examples/quickstart/build.gradle.kts) is a complete source
consumer with a Java 21 toolchain.

## Generate your Java catalog from the schema

For a schema-backed application, generation is the recommended starting point.
The existing generator maps a `SchemaModel` into:

| Schema metadata | Generated Java |
| --- | --- |
| Tables and columns | `Table<RowRecord>` descriptors with typed column fields and physical names |
| Nullability and SQL types | Java types, SQL-type/nullability metadata, and nullable record components |
| Primary, unique, and foreign keys | Key descriptors and references for relationship-aware joins |
| Views | Typed view descriptors |
| Supported database enums | Java enum types |
| Table rows | Java records describing row shapes; result mapping remains application-owned |

Names are normalized for Java packages/identifiers, and detected naming collisions
fail generation. These are supported metadata mappings, not a guarantee that
every vendor-specific type or schema construct is representable.

### Choose your schema source

- **Live database:** `SchemaIntrospector` reads metadata from PostgreSQL or MySQL
  through a JDBC connection for the selected schemas.
- **DDL files:** the default generation path applies DDL to a scratch database
  and introspects it. This needs Docker and the matching JDBC driver.
- **DDL parser:** an explicit, Docker-free fallback supports a bounded subset of
  DDL and rejects unsupported statements. Use it for simple fixtures, not as a
  substitute for verifying a complex production schema.

`CatalogGenerator.generate(schemaModel, targetPackage)` produces the source map.
The Gradle task handles writing it to disk and registering it for compilation.
The implementation is in [`titan-codegen`](titan-codegen/src/main/java/io/titan/catalog/CatalogGenerator.java)
and [`titan-codegen-gradle-plugin`](titan-codegen-gradle-plugin/build.gradle.kts)
in this repository. The [schema-first example](examples/schema-codegen/README.md)
is a runnable version of the DDL workflow below.

### Configure the generation build

Place your application beside this checkout. No additional Titan repository is
needed:

```text
workspace/
  titan-dsl/
  my-app/
```

In `my-app/settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("../titan-dsl")
}
rootProject.name = "my-app"
includeBuild("../titan-dsl")
```

In `my-app/build.gradle.kts`:

```kotlin
plugins {
    java
    id("io.titan.codegen")
}

repositories { mavenCentral() }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

dependencies {
    implementation("io.titan:titan-dsl:0.1.0")
    compileOnly("org.jspecify:jspecify:1.0.0") // generated nullable record components
    titanJdbc("org.postgresql:postgresql:42.7.13")
}

titan {
    database {
        dialect.set("postgresql")
        schemas.set(listOf("app"))
        jdbcUrl.set(providers.environmentVariable("TITAN_DB_URL"))
        username.set(providers.environmentVariable("TITAN_DB_USER"))
        password.set(providers.environmentVariable("TITAN_DB_PASSWORD"))
    }
    catalog {
        targetPackage.set("generated.catalog")
    }
}
```

Set those environment variables for a database account that can read the required
schema metadata. The driver is a generation dependency; it does not become a
dependency of the DSL library. For MySQL, select `mysql` and provide the MySQL
driver in `titanJdbc`.

For DDL input, replace the three connection-property lines with:

```kotlin
ddlDir.set("src/main/resources/db/schema")
ddlMode.set("parser") // bounded, Docker-free mode for the simple fixture below
```

Omit `ddlMode` to use the default scratch-container path. For example, put this
fixture in `my-app/src/main/resources/db/schema/users.sql`:

```sql
CREATE TABLE app.users (
    id INTEGER PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    country VARCHAR(2) NOT NULL,
    manager_id INTEGER REFERENCES app.users(id)
);
```

From `my-app`, run:

```bash
../titan-dsl/gradlew titanGenerate compileJava
```

This generates `build/generated/sources/titan/generated/catalog/app/tables/Users.java`,
`.../app/records/UsersRecord.java`, and `.../generated/catalog/Catalog.java`, whose
`Catalog.TABLES` and `Catalog.VIEWS` list every generated singleton for
[automatic filters](#automatic-filters). The table exposes `Users.USERS` with
`ID`, `NAME`, `ACTIVE`, `COUNTRY`, and `MANAGER_ID` fields. Import that singleton
to write the query at the top of this README. A PostgreSQL schema named `public`
maps to the Java package segment `public_`.

The plugin wires generated sources into Java compilation: `build` and
`compileJava` automatically run the required generation tasks. It does not
register transpilation, SQL packaging, or deployment tasks. The full Titan
pipeline remains a separate downstream consumer of this tooling.

### Regenerate as the schema evolves

Apply migrations to the schema source used for generation, regenerate, and compile
the application before deploying. `titanIntrospect` acquires the schema once;
`titanGenerate` reads that exact snapshot. Live-database introspection and its
generation chain are not considered up-to-date from a previous run.

Use a dedicated generated-source directory. A manifest tracks generator-owned
files: regeneration updates them and removes obsolete sources, while leaving
unrelated files alone. Existing unowned files with matching names and symlinked
output paths are rejected. When migrating outputs from the older Titan generator,
choose a fresh output directory or explicitly clean the old generated-only
directory first. Do not modify generated sources manually.

A removed column can then become a missing Java field instead of a late query
failure. This depends on regeneration: a stale catalog cannot detect changes in
the live database. Review generated type mappings, especially vendor-specific
types, and exercise important queries against the target database.

## Define a table once

Generated descriptors are the normal schema-backed path. For a small prototype or
an example with no database, you can also declare descriptors directly. The
standalone recipes below use this hand-written `Users` class (lowercase fields);
the generated catalog above exposes uppercase fields such as `USERS.NAME`:

```java
import titan.dsl.*;

public final class Users extends Table<Object> {
    public final Column<Integer> id = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
    public final Column<String> name = column("name", SQLType.VARCHAR, Nullability.NOT_NULL);
    public final Column<Boolean> active = column("active", SQLType.BOOLEAN, Nullability.NOT_NULL);
    public final Column<String> country = column("country", SQLType.VARCHAR, Nullability.NOT_NULL);
    public final Column<Integer> managerId = column("manager_id", SQLType.INTEGER, Nullability.NULLABLE);

    public Users() {
        super("users", "app");
    }
}
```

Create `var users = new Users();`. `app` is a PostgreSQL schema or a MySQL database
name in the examples. The database/table must already exist if you execute SQL.

`Column<T>` controls accepted Java values, `SQLType` records the intended SQL
type, and `Nullability` records schema intent. For hand-written descriptors, keep
these consistent yourself. Generation derives them from a schema snapshot; the
query library does not revalidate that snapshot against a live database. The
table's generic parameter can describe a row shape but does not supply a row mapper.

For example, `users.id.eq(7)` compiles while `users.id.eq("seven")` does not.
Descriptors and raw expressions still allow SQL that the database may reject.
`DSL.selectFrom(table)` discovers public column fields; use explicit projections
when you want direct control over selected fields and order.

## Construct and render queries

Configure a context once for each database target and reuse it:

```java
var db = DSL.using(SqlDialect.POSTGRESQL);
```

`DSLContext` is immutable and safe to share. It holds only configuration, not a
connection, transaction, or query state. Each factory creates a fresh mutable
builder, so queries from the same context remain independent. Expressions such
as `DSL.count()`, `DSL.when(...)`, and `DSL.exists(...)` stay static utilities.

```java
var filters = users.active.eq(true);
String country = "NL"; // an optional application filter
if (country != null) {
    filters = filters.and(users.country.eq(country));
}
var query = db.select(users.id, users.name)
        .from(users)
        .where(filters)
        .orderBy(users.name.asc())
        .limit(20)
        .offset(40);
var rendered = query.render();
```

`rendered.sql()` contains `?` placeholders. `rendered.parameters()` contains
`BindValue` objects in SQL appearance order, here `true` and `"NL"`. Each bind
exposes `value()` and `sqlType()`; parameter list index zero corresponds to JDBC
parameter index one. `query.toSql()` instead inlines literals using the same configured dialect.
The SELECT and DML factories, `selectFrom`, `with`, and `withRecursive` all carry
that configuration through their fluent chains. Structured subqueries and set
operands use the outer rendering dialect without changing their own context.

For compatibility, static `DSL.select(...)` and DML factories still work with
`render(dialect)`. Calling `render()` on an unconfigured query fails with an
instruction to use `DSL.using(...)`; it never guesses PostgreSQL. The explicit
`render(dialect)` and `toSql(dialect)` overloads remain per-call overrides and do
not modify the context. Legacy unconfigured `toSql()` still defaults to PostgreSQL.

Conditions are immutable; `and`, `or`, and `not` return new conditions. Builders
are mutable: create them per query/request. A second `where(...)` replaces the
first, while calls such as `orderBy(...)` append entries. Do not treat a builder
as an immutable template or share it between threads during construction.

Column predicates include equality/comparison, `between`, `in`, `notIn`,
`isNull`, and `isNotNull`. `eq(null)` becomes `IS NULL`; `ne(null)` becomes
`IS NOT NULL`. Empty `in` and `notIn` lists become false and true predicates
respectively. Comparison operands and IN-list elements cannot be null.

## Joins and aliases

Bare column descriptors are not automatically qualified by their table. Use
aliases for overlapping names and self-joins:

```java
var employee = users.as("employee");
var manager = users.as("manager");
var rendered = db.select(employee.col(users.name), manager.col(users.name))
        .from(employee)
        .leftJoin(manager)
        .on(employee.col(users.managerId), manager.col(users.id))
        .render();
```

This renders `employee.manager_id = manager.id` and preserves the left join.
Join conditions also accept `Condition` and foreign-key descriptors. Inner,
left/right/full outer, cross, and lateral join forms exist, but support varies
by database. Alias qualification does not rename result columns; map duplicate
labels by position or design distinct projections in your execution layer.

## Aggregation and windows

```java
var count = DSL.count();
var report = db.select(users.country, count)
        .from(users)
        .groupBy(users.country)
        .having(count.gt(10L))
        .render();

var position = DSL.rowNumber()
        .over(DSL.partitionBy(users.country).orderBy(users.id.asc()));
var ranked = db.select(users.name, position).from(users)
        .render();
```

The first query binds the threshold `10L`; the second emits `ROW_NUMBER() OVER
(PARTITION BY country ORDER BY id ASC)`. Helpers include `count`, `sum`, `avg`,
`min`, `max`, `rank`, `denseRank`, `lag`, `lead`, and frame specifications through
`rowsBetween`, `rangeBetween`, and `groupsBetween`. Grouping sets, rollup, and
cube are also represented. These are SQL-construction features, not a guarantee
that the chosen database/version supports every combination.

## Subqueries and CASE expressions

IN subqueries keep structured predicate values in the outer parameter list:

```java
var activeIds = db.select(users.id).from(users).where(users.active.eq(true));
var names = db.select(users.name).from(users)
        .where(users.id.in(activeIds))
        .render();
```

This emits `WHERE id IN (SELECT id FROM app.users WHERE active = ?)` with one
boolean bind. IN subqueries must project one column with a matching SQL type.
`DSL.exists(subquery)` and `DSL.notExists(subquery)` provide predicate forms too.

Projected CASE expressions preserve condition and branch-result bindings:

```java
var label = DSL.when(users.active.eq(true), "active").otherwise("inactive");
var result = db.select(users.name, label).from(users)
        .render();
```

The SQL includes `CASE WHEN active = ? THEN ? ELSE ? END` and binds
`[true, "active", "inactive"]`. Use that structured expression directly in the
projection; converting it to a name/string is a different rendering path.

## CTEs and set operations

Name a query stage, expose its fields, then build an outer query:

```java
var directory = db.name("directory")
        .as(db.select(users.id, users.name).from(users));
var directoryName = directory.field("name", String.class);
var result = db.with(directory).select(directoryName).from(directory)
        .where(directoryName.eq("Ada"))
        .render();
```

The outer filter binds `"Ada"`. `field(name, Class<T>)` infers a SQL type and
defaults nullability to nullable; use the SQLType/Nullability overload for
explicit metadata. `withRecursive`/`asRecursive` model recursive stages, and
`union`, `unionAll`, `intersect`, and `except` compose compatible projections.
Set-operation operands are snapshotted at composition time. Recursive stages
must include an application-designed termination condition.

### Composition and binding boundaries

Parameterized rendering is not universal across every helper:

| Composition path | Current behavior |
| --- | --- |
| Structured WHERE/HAVING, ordinary DML values, IN/EXISTS subqueries, projected CASE | Values can remain ordered bind parameters. |
| `db.name(...).as(query)`, `db.name(...).asRecursive(...)` | Retain structured query snapshots: CTE values remain ordered binds and the outer dialect is applied at rendering. |
| Static `DSL.name(...).as(query)` / `asRecursive(...)`, `DSL.defineInlineView(...)`, `DSL.defineView(...)`, `DSL.scalar(query)` | Legacy helpers capture literal SQL using the child query's configured dialect (PostgreSQL if unconfigured). Child values do not become outer bind parameters. |
| `asSql(...)`, `Condition.of(...)`, raw field/identifier text | Text is used as supplied; no automatic value binding. |

For example, `db.name(...).as(db.select(...).where(users.active.eq(true)))`
retains `active = ?` and its boolean bind. The legacy `DSL.name(...)` version
instead captures `active = TRUE`. Adding an outer name predicate then gives two
binds in the context version, but only the outer bind in the legacy version.
Raw or previously captured SQL is not translated when the outer dialect changes.
Inspect both SQL and parameters when composing helpers, particularly with
dialect-sensitive literals or confidential values.

## Inserts, updates, deletes, and upserts

```java
var batch = db.insertInto(users).columns(users.id, users.name)
        .values(7, "Ada").values(8, "Grace")
        .render();
var update = db.update(users).set(users.name, "Grace")
        .where(users.id.eq(8)).render();
var delete = db.deleteFrom(users).where(users.id.eq(8))
        .render();
```

The bind lists are `[7, "Ada", 8, "Grace"]`, `["Grace", 8]`, and `[8]`.
The batch form generates one INSERT with multiple VALUES rows; it does not call
JDBC `addBatch`. Each values row must match the declared column count, but this
varargs surface does not provide the same Java type checking as `set(column, value)`.
UPDATE/DELETE without a WHERE clause are allowed and affect all rows if executed.

```java
for (var dialect : SqlDialect.values()) {
    var target = DSL.using(dialect);
    var upsert = target.insertInto(users).set(users.id, 7).set(users.name, "Ada")
            .onConflict(users.id).doUpdate().set(users.name, "Ada Lovelace");
    var rendered = upsert.render();
}
```

PostgreSQL gets `ON CONFLICT (id) DO UPDATE SET name = ?`; MySQL gets
`ON DUPLICATE KEY UPDATE name = ?`. Both bind `[7, "Ada", "Ada Lovelace"]`.
The database must have the appropriate uniqueness constraint. MySQL considers
its unique keys rather than an explicit conflict target. INSERT SELECT,
expression-valued assignments, and PostgreSQL RETURNING are also available;
review captured expression/literal behavior when supplying calculated values.
Conflict assignments belong on `doUpdate()`: `set`, `columns`, `values`, and
`select` are rejected after `onConflict(...)`. Several conflict assignments
currently need the `doUpdate()` step held in a variable; a fluent form is
tracked in [#6](https://github.com/rbilleci/titan-dsl/issues/6).

## Automatic filters

Declare row filters such as tenant, organization, or user scoping once, and let
the context add them to every SELECT, UPDATE, and DELETE it renders. A
`FilterPolicy` is validated against the whole catalog when it is built, so a
table without a binding is a startup error rather than an unfiltered query.

```java
import static titan.dsl.FilterPath.via;
import generated.catalog.Catalog;

static final Filter<Long> TENANT = Filter.of("tenant");
static final Filter<Long> ORG = Filter.of("org");
static final Filter<UUID> USER = Filter.of("user");

var policy = FilterPolicy.builder(Catalog.TABLES)
        .byColumn(TENANT, "tenant_id")   // every table that has the column
        .derive(TENANT)                  // the rest: nearest bound table through foreign keys
        .byColumn(ORG, "org_id").byColumn(USER, "owner_id").anyOf(ORG, USER)
        .bind(ORDER_LINES, USER, via(ORDER_LINES.ORDER_ID, ORDERS.CUSTOMER_ID).to(CUSTOMERS.USER_ID))
        .exempt(COUNTRIES)
        .build();

var db = DSL.using(SqlDialect.POSTGRESQL).filters(policy);   // once
var request = db.scoped(Scope.of(TENANT, 42L).with(ORG, 9L).with(USER, userId));
var rendered = request.select(ORDERS.ID).from(ORDERS).render();
```

```sql
SELECT id FROM app.orders
WHERE (app.orders.tenant_id = ?) AND ((app.orders.org_id = ?) OR (app.orders.owner_id = ?))
```

Filter binds (`[42, 9, userId]`) follow any binds from your own `where(...)`.
`Filter.of(name)` declares a typed key; the SQL type comes from the bound column.

| Binding | Declaration | Rendered form |
| --- | --- | --- |
| Column on the relation | `byColumn(TENANT, "tenant_id")` for the catalog, or `bind(ORDERS, TENANT, ORDERS.TENANT_ID)` | `alias.tenant_id = ?`, or `IN (?, ...)` for `Scope.withAny` |
| Foreign-key path | `bind(ORDER_LINES, TENANT, via(ORDER_LINES.ORDER_ID))`, optionally `.to(column)` | correlated `EXISTS (SELECT 1 FROM app.orders AS _tf1 WHERE ...)` |
| Derived path | `derive(TENANT)` | as above, following the shortest unambiguous route within `maxDepth` (default 2) |
| DSL lambda | `bind(ORDERS, LIVE, t -> t.col(ORDERS.DELETED_AT).isNull())` | the returned `Condition` |

- The policy is a conjunction. `anyOf(ORG, USER)` forms a group whose bound
  members are ORed; every relation needs at least one bound member.
- A value filter needs a value in the `Scope` or an explicit `Scope.skip(filter)`;
  a missing value is a rendering error. `Filter.predicate(name)` filters take no
  value and apply only where bound; `skip` disables them for a request too.
- Every catalog relation must be bound, derived, or `exempt` for each value
  filter; `build()` reports all gaps at once. Relations outside the catalog are
  rejected at render time. `policy.explain(table)` prints the resolved bindings.
- Filters are added at render time to every table and view in `FROM`/`JOIN` and
  to UPDATE/DELETE targets. A `LEFT JOIN` receives its filter in `ON`; other
  joins receive it in `WHERE`, so RIGHT and FULL joins lose unmatched rows from
  the filtered side. Structured subqueries and `db.name(...)` CTEs render with
  their own filters; raw SQL is not inspected.
- `db.unscoped()` applies no filters, and so do queries built with the static
  `DSL.select(...)` factories, including as subqueries or set-operation operands.
  `filters(policy, supplier)` reads the scope when a builder is created, for
  request-context integration.
- Path subqueries alias their tables `_tf1`, `_tf2`, ...; avoid that prefix.
  Only `bind(table, Filter<T>, Column<T>)` is checked at compile time; column
  conventions and lambdas are checked by the database.

Writes follow the same rule as reads: a statement may only produce rows the
scope could read.

- A column bound directly to a standalone filter is *checked*: an INSERT or
  `set(...)` value must equal the scope value (or be one of `withAny`), an
  expression is rejected, and an INSERT that omits the column gets it filled
  from a single-valued scope. `anyOf` columns need at least one member in scope.
- An UPDATE that moves an `anyOf` member out of scope affects only rows that
  stay visible through another member; the rendered WHERE carries that test.
- Upserts guard the existing row: PostgreSQL renders `DO UPDATE SET ... WHERE
  <scope>`, MySQL renders each assignment as `IF(<scope>, <new value>, column)`.
  A key conflict with another scope's row changes nothing in either dialect.
- `INSERT ... SELECT` is accepted only when each checked column is projected
  from the same filter's column of the source FROM relation, built from the
  same scoped context.
- Path and lambda bindings are not checked on writes; `explain()` reports them
  as `unchecked`. Enforce those in the schema, with a composite foreign key that
  carries the scope column, or with database row-level security.
- A scope value must be compatible with the bound column's SQL type, and one
  filter cannot bind columns of different SQL types; both are reported.

Hand-written descriptors can be listed directly:
`FilterPolicy.builder(new Users(), new Orders())`.

## Execute through your application's JDBC connection

Pass your configured context and an existing connection, then use the appropriate JDBC setters. This method handles
one non-null VARCHAR parameter; it is not a general-purpose binder:

```java
public static List<String> findUserNames(DSLContext db, Connection connection, String name) throws SQLException {
    Objects.requireNonNull(db, "db");
    Objects.requireNonNull(name, "name");
    var users = new Users();
    var rendered = db.select(users.name).from(users)
            .where(users.name.eq(name)).render();
    var names = new ArrayList<String>();
    try (var statement = connection.prepareStatement(rendered.sql())) {
        statement.setString(1, (String) rendered.parameters().getFirst().value());
        try (var rows = statement.executeQuery()) {
            while (rows.next()) {
                names.add(rows.getString("name"));
            }
        }
    }
    return names;
}
```

The full imports and compilable method are in
[FeatureExamples.java](examples/quickstart/src/main/java/example/FeatureExamples.java).
The demo does not call this method or contact a database. A general integration
must bind all parameters in order, choose JDBC types for nulls and special values
using `sqlType()`, manage transactions, and map results. Database drivers and
connection pools are application dependencies, not library dependencies.

## Typed projections and convenience methods

`db.select` and static `DSL.select` overloads for one through ten columns return typed
`SelectBuilder1..10` shapes, with corresponding `Tuple1..10` records. Wider
projections can use the general varargs builder; type/shape guarantees depend on
the overload and subsequent API calls. Tuples do not populate themselves from JDBC.

| Method | Standalone behavior |
| --- | --- |
| `render()` | Context dialect, SQL plus ordered binds on structured paths; unconfigured queries fail. |
| `render(dialect)` | Explicit per-call dialect override; does not change the query's context. |
| `toSql()` / `toSql(dialect)` | Literal SQL using the context or explicit override; only unconfigured no-argument calls default to PostgreSQL. |
| `fetch()` / DML `execute()` | Literal SQL text, not rows or an update count. |
| `fetchOne()` | SQL with a default LIMIT 1 unless a limit is already specified. |
| `fetchCount()` / `fetchExists()` | Literal wrapper queries, ignoring outer ordering/pagination. |
| `fetchInto(recordType)` | Validates the projection/record shape and returns SQL with a mapping comment; does not construct records. |
| `forEach(...)` | Returns a loop/query representation; does not iterate database rows or execute the callback. |
| `fetchScalar()` / `fetchExistsValue()` | Integration hooks returning placeholder null/false values when invoked directly. |

Metadata annotations such as `@StoredProcedure`, `@StoredFunction`, `@Trigger`,
`@ScheduledJob`, `@SQL`, and `@SecurityDefiner` are available for custom tools.
Annotations alone do not create or execute database routines.

## Dialect and safety limits

- Rendering has PostgreSQL and MySQL modes, not a database-version negotiation
  layer. Advanced join, set-operation, locking, grouping, and window clauses may
  be emitted without checking database support. Validate against your server.
- PostgreSQL supports the library's DML RETURNING forms. MySQL UPDATE/DELETE
  RETURNING is rejected; one-column INSERT RETURNING emits an ordinary INSERT
  and requires JDBC generated-key retrieval by the caller. Multiple returned
  INSERT columns are rejected in MySQL mode.
- Literal-mode escaping assumes PostgreSQL standard-conforming strings and
  MySQL's usual backslash-escape behavior. Prefer binds and check server SQL modes
  if you must use literal rendering.
- Identifiers are not universally quoted. Use trusted schema/table/column names
  and aliases; avoid reserved words or supply reviewed dialect-specific SQL.
  Raw fragment APIs do not escape untrusted input.
- MySQL rejects a self-referential automatic-filter path (a table reached
  through its own foreign key) in UPDATE and DELETE with error 1093; bind such
  tables directly or by lambda. PostgreSQL accepts the correlated subquery.
- The default test suite validates Java behavior and rendering without a live
  database; `./gradlew integrationTest` also executes the automatic-filter SQL
  shapes against PostgreSQL and MySQL containers. Neither certifies your
  execution environment or authorizes queries for you.

See [SECURITY.md](SECURITY.md) for reporting and safe-use guidance.

## Development and troubleshooting

The repository root coordinates three peer modules; it does not publish a JAR:

| Module | Published artifact / role |
| --- | --- |
| `titan-dsl/` | `io.titan:titan-dsl` — query API and rendering |
| `titan-codegen/` | `io.titan:titan-codegen` — schema introspection and Java catalog generation |
| `titan-codegen-gradle-plugin/` | `io.titan:titan-codegen-gradle-plugin` — the `io.titan.codegen` plugin |

Each module has its own `src/`, build script, and `build/` outputs. Shared Java,
testing, and publication conventions live in `build-logic/`; the release version
is set once in `gradle.properties`. The Gradle root name `titan-dsl-build` is only
a build identifier, not a new artifact coordinate. Existing `includeBuild` paths,
artifact coordinates, and plugin IDs are unchanged.

Run `./gradlew build` for the full library checks, or
`./gradlew :titan-dsl:test --tests titan.dsl.DslRenderingModesTest` for a focused slice.
Run `./gradlew -p examples/quickstart run runFeatures` to exercise the query recipes,
and `./gradlew -p examples/schema-codegen build run` for generation through compilation.
Run `./gradlew integrationTest` for the PostgreSQL/MySQL Docker-backed schema tests.
Javadoc and test reports live under each module's `build/docs/javadoc/` and
`build/reports/tests/test/`; for example, `titan-dsl/build/docs/javadoc/`.

If dependency resolution fails, check your included-build path or republish the
local artifact. If the Java toolchain is missing, install JDK 21 and set
`JAVA_HOME`. If SQL or bindings surprise you, inspect the explicit dialect,
repeated `where` calls, alias qualification, and the composition table above.

The library build also generates its internal `SelectBuilder1..10`, `Tuple1..10`,
and callback classes automatically. That is separate from application schema
catalog generation. Its templates are in `titan-dsl/build.gradle.kts`.
Binary/source/Javadoc JARs are in each module's `build/libs/`; generated Java
sources are included in the DSL source JAR. Use artifact coordinates or composite
builds instead of hardcoding paths to build outputs.

For implementation conventions and generated sources, see
[CONTRIBUTING.md](CONTRIBUTING.md). For a compact list of types, see the
[API overview](docs/api-overview.md).

## Project information

- [Schema-first example](examples/schema-codegen/README.md) · [Query feature examples](examples/quickstart/README.md)
- [Contributing and testing](CONTRIBUTING.md)
- [Projection arity policy](docs/post-loop-design-considerations.md)
- [Release preparation](docs/releasing.md) · [Changelog](CHANGELOG.md)

## License

Project-owned source, examples, documentation, and generated DSL classes use
**GNU GPL version 3 only** (`GPL-3.0-only`), without an additional linking
exception. See [LICENSE](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.md).
The project-owned codegen and Gradle plugin modules use the same license;
their resolved dependencies retain their respective licenses.
