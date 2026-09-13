package io.titan.gradle;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import javax.inject.Inject;

/** Schema acquisition and catalog generation settings shared with the full Titan plugin. */
public abstract class TitanCodegenExtension {
    private final Database database;
    private final Catalog catalog;

    @Inject
    public TitanCodegenExtension(ObjectFactory objects) {
        database = objects.newInstance(Database.class);
        catalog = objects.newInstance(Catalog.class);
    }

    public Database getDatabase() { return database; }
    public Catalog getCatalog() { return catalog; }
    public void database(Action<? super Database> action) { action.execute(database); }
    public void catalog(Action<? super Catalog> action) { action.execute(catalog); }

    public abstract static class Database {
        public abstract Property<String> getJdbcUrl();
        public abstract Property<String> getUsername();
        public abstract Property<String> getPassword();
        public abstract Property<String> getDdlDir();

        /**
         * How DDL files are turned into a schema model (audit G-6/G-7).
         *
         * <p>{@code "container"} (default): apply the DDL to a scratch Testcontainers database
         * for the configured dialect and introspect it through the JDBC path — equivalent to
         * live-database introspection by construction. Requires Docker and a JDBC driver on the
         * {@code titanJdbc} configuration.</p>
         *
         * <p>{@code "parser"}: explicit no-Docker fallback using the regex DDL parser. It
         * supports a reduced DDL subset (no ALTER TABLE, CREATE TYPE, CREATE SEQUENCE,
         * CREATE OR REPLACE, table options such as {@code ENGINE=...}; view columns stay
         * untyped) and fails hard on statements it cannot represent instead of dropping them
         * silently.</p>
         */
        public abstract Property<String> getDdlMode();

        public abstract Property<String> getDialect();
        public abstract ListProperty<String> getSchemas();
    }

    public abstract static class Catalog {
        public abstract Property<String> getTargetPackage();
        public abstract Property<String> getOutputDir();
    }

}
