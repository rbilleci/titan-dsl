package io.titan.introspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaChangeDetectorTest {

    private final SchemaChangeDetector detector = new SchemaChangeDetector();

    @Test
    void detectsAddedRemovedAndChangedColumns() {
        SchemaModel previousModel = new SchemaModel(List.of(
                new SchemaModel.TableMeta("public", "accounts", List.of(
                        new SchemaModel.ColumnMeta("id", "integer", false, 32, 0, null, null, null, List.of(), true),
                        new SchemaModel.ColumnMeta("email", "varchar", false, null, null, 255),
                        new SchemaModel.ColumnMeta("status", "varchar", true, null, null, 20)
                ))
        ));

        SchemaModel currentModel = new SchemaModel(List.of(
                new SchemaModel.TableMeta("public", "accounts", List.of(
                        new SchemaModel.ColumnMeta("id", "integer", false, 32, 0, null, null, null, List.of(), true),
                        new SchemaModel.ColumnMeta("status", "varchar", false, null, null, 20),
                        new SchemaModel.ColumnMeta("created_at", "timestamp", false, null, null, null)
                ))
        ));

        Map<String, String> previous = detector.fingerprint(previousModel);
        Map<String, String> current = detector.fingerprint(currentModel);
        SchemaChangeDetector.SchemaDiff diff = detector.diff(previous, current);

        assertEquals(List.of("public.accounts.created_at"), diff.addedColumns());
        assertEquals(List.of("public.accounts.email"), diff.removedColumns());
        assertEquals(1, diff.changedColumns().size());
        assertEquals("public.accounts.status", diff.changedColumns().getFirst().columnPath());
    }

    @Test
    void supportsRoundTripSnapshotSerialization() {
        Map<String, String> fingerprint = Map.of(
                "public.accounts.id", "integer~false~32~0~~~true~",
                "public.accounts.email", "varchar~false~~~255~~~false~"
        );

        List<String> serialized = detector.serialize(fingerprint);
        Map<String, String> deserialized = detector.deserialize(serialized);

        assertEquals(fingerprint, deserialized);
    }

    @Test
    void checksumChangesWhenSchemaChanges() {
        SchemaModel base = new SchemaModel(List.of(
                new SchemaModel.TableMeta("public", "accounts", List.of(
                        new SchemaModel.ColumnMeta("id", "integer", false, 32, 0, null)
                ))
        ));
        SchemaModel changed = new SchemaModel(List.of(
                new SchemaModel.TableMeta("public", "accounts", List.of(
                        new SchemaModel.ColumnMeta("id", "bigint", false, 64, 0, null)
                ))
        ));

        String baseChecksum = detector.checksum(detector.fingerprint(base));
        String changedChecksum = detector.checksum(detector.fingerprint(changed));

        assertFalse(baseChecksum.isBlank());
        assertNotEquals(baseChecksum, changedChecksum);
        assertTrue(baseChecksum.matches("[a-f0-9]{64}"));
    }
}
