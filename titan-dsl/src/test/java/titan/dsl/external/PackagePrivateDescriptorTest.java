package titan.dsl.external;

import org.junit.jupiter.api.Test;
import titan.dsl.Column;
import titan.dsl.DSL;
import titan.dsl.Filter;
import titan.dsl.FilterPolicy;
import titan.dsl.Nullability;
import titan.dsl.SQLType;
import titan.dsl.Scope;
import titan.dsl.SqlDialect;
import titan.dsl.Table;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Descriptor reflection must work for a package-private descriptor class declared outside
 * {@code titan.dsl}: its public fields are only readable after {@code trySetAccessible}.
 */
class PackagePrivateDescriptorTest {

    static final Filter<Long> TENANT = Filter.of("tenant");

    static final class Notes extends Table<Object> {
        public final Column<Long> ID = column("id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> TENANT_ID = column("tenant_id", SQLType.BIGINT, Nullability.NOT_NULL);

        Notes() {
            super("notes", "app");
        }
    }

    @Test
    void reflectionReadsPublicFieldsOfAPackagePrivateDescriptor() {
        var notes = new Notes();
        assertEquals("SELECT id, tenant_id FROM app.notes", DSL.selectFrom(notes).render(SqlDialect.POSTGRESQL).sql());

        var policy = FilterPolicy.builder(notes).byColumn(TENANT, "tenant_id").build();
        var db = DSL.using(SqlDialect.POSTGRESQL).filters(policy).scoped(Scope.of(TENANT, 42L));
        assertEquals("SELECT id FROM app.notes WHERE app.notes.tenant_id = ?", db.select(notes.ID).from(notes).render().sql());
    }
}
