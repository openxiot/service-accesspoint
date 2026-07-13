package cc.openxiot.device.db.registry;

import cn.geekcity.xiot.spec.summary.Summary;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeviceRegistryTest {

    private static final String DID = "child-001";
    private static final String TYPE = "urn:test:device:sensor:00000001";
    private static final String ACCESS_POINT = "10.0.0.1:8080";

    private DeviceRegistry createRoot() {
        DeviceRegistry root = new DeviceRegistry();
        root.did = "root-001";
        root.accessKey = "secret-key";
        root.accessPoint = ACCESS_POINT;
        root.type = "urn:test:device:gateway:00000002";
        return root;
    }

    @Test
    void of_createsEntityFromSummaryAndRoot() {
        DeviceRegistry root = createRoot();
        Summary summary = new Summary(TYPE, true, "zigbee", "root-001", "root-001",
                List.of("member-1"));

        DeviceRegistry entity = DeviceRegistry.of(DID, summary, root);

        assertEquals(DID, entity.did);
        assertEquals(TYPE, entity.type);
        assertTrue(entity.online);
        assertEquals("root-001", entity.rootId);
        assertEquals("root-001", entity.parentId);
        assertEquals(List.of("member-1"), entity.members);
        assertEquals("secret-key", entity.accessKey);
        assertEquals(ACCESS_POINT, entity.accessPoint);
        assertNotNull(entity.create);
    }

    @Test
    void change_detectsTypeChange() {
        DeviceRegistry root = createRoot();
        DeviceRegistry entity = DeviceRegistry.of(DID, new Summary(TYPE, true, "zigbee", null, "root-001"), root);

        Summary newSummary = new Summary("urn:test:device:sensor:00000003", true, "zigbee", null, "root-001");
        boolean changed = entity.change(newSummary, root);

        assertTrue(changed);
        assertEquals("urn:test:device:sensor:00000003", entity.type);
    }

    @Test
    void change_detectsOnlineChange() {
        DeviceRegistry root = createRoot();
        DeviceRegistry entity = DeviceRegistry.of(DID, new Summary(TYPE, true, "zigbee", null, "root-001"), root);

        Summary newSummary = new Summary(TYPE, false, "zigbee", null, "root-001");
        boolean changed = entity.change(newSummary, root);

        assertTrue(changed);
        assertFalse(entity.online);
    }

    @Test
    void change_detectsRootIdChange() {
        DeviceRegistry root = createRoot();
        DeviceRegistry entity = DeviceRegistry.of(DID, new Summary(TYPE, true, "zigbee", null, "root-001"), root);

        DeviceRegistry newRoot = createRoot();
        newRoot.did = "new-root-001";
        newRoot.accessKey = "secret-key";
        newRoot.accessPoint = ACCESS_POINT;

        Summary newSummary = new Summary(TYPE, true, "zigbee", null, "new-root-001");
        boolean changed = entity.change(newSummary, newRoot);

        assertTrue(changed);
        assertEquals("new-root-001", entity.rootId);
    }

    @Test
    void change_detectsNullRootId_SetToRoot() {
        DeviceRegistry root = createRoot();
        DeviceRegistry entity = DeviceRegistry.of(DID, new Summary(TYPE, true, "zigbee", null, null), root);
        entity.rootId = null;  // explicitly set null

        Summary newSummary = new Summary(TYPE, true, "zigbee", null, null);
        boolean changed = entity.change(newSummary, root);

        assertTrue(changed);
        assertEquals("root-001", entity.rootId);
    }

    @Test
    void change_detectsParentIdChange() {
        DeviceRegistry root = createRoot();
        DeviceRegistry entity = DeviceRegistry.of(DID, new Summary(TYPE, true, "zigbee", "old-parent", "root-001"), root);

        Summary newSummary = new Summary(TYPE, true, "zigbee", "new-parent", "root-001");
        boolean changed = entity.change(newSummary, root);

        assertTrue(changed);
        assertEquals("new-parent", entity.parentId);
    }

    @Test
    void change_detectsMembersChange() {
        DeviceRegistry root = createRoot();
        DeviceRegistry entity = DeviceRegistry.of(DID, new Summary(TYPE, true, "zigbee", null, "root-001",
                List.of("member-1")), root);

        Summary newSummary = new Summary(TYPE, true, "zigbee", null, "root-001",
                List.of("member-1", "member-2"));
        boolean changed = entity.change(newSummary, root);

        assertTrue(changed);
        assertEquals(List.of("member-1", "member-2"), entity.members);
    }

    @Test
    void change_detectsAccessKeyChange() {
        DeviceRegistry root = createRoot();
        DeviceRegistry entity = DeviceRegistry.of(DID, new Summary(TYPE, true, "zigbee", null, "root-001"), root);

        DeviceRegistry newRoot = createRoot();
        newRoot.did = "root-001";
        newRoot.accessKey = "new-secret-key";
        newRoot.accessPoint = ACCESS_POINT;

        Summary newSummary = new Summary(TYPE, true, "zigbee", null, "root-001");
        boolean changed = entity.change(newSummary, newRoot);

        assertTrue(changed);
        assertEquals("new-secret-key", entity.accessKey);
    }

    @Test
    void change_noChange_whenSame() {
        DeviceRegistry root = createRoot();
        DeviceRegistry entity = DeviceRegistry.of(DID, new Summary(TYPE, true, "zigbee", null, "root-001"), root);

        Summary sameSummary = new Summary(TYPE, true, "zigbee", null, "root-001");
        boolean changed = entity.change(sameSummary, root);

        assertFalse(changed);
    }

    @Test
    void invalid_returnsTrue_whenKeyMismatch() {
        DeviceRegistry root = createRoot();
        DeviceRegistry entity = DeviceRegistry.of(DID, new Summary(TYPE, true, "zigbee", null, "root-001"), root);

        assertTrue(entity.invalid("wrong-key"));
    }

    @Test
    void invalid_returnsFalse_whenKeyMatches() {
        DeviceRegistry root = createRoot();
        DeviceRegistry entity = DeviceRegistry.of(DID, new Summary(TYPE, true, "zigbee", null, "root-001"), root);

        assertFalse(entity.invalid("secret-key"));
    }
}
