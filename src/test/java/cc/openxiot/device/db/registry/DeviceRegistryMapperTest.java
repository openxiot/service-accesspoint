package cc.openxiot.device.db.registry;

import cn.geekcity.xiot.spec.device.Device;
import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.summary.Summary;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeviceRegistryMapperTest {

    private static final String DID = "test-device-001";
    private static final String TYPE = "urn:test:device:switch:00000008";
    private static final String ACCESS_POINT = "10.0.0.1:8080";
    private static final String PROTOCOL = "wss";
    private static final String PARENT_ID = "parent-001";
    private static final String ROOT_ID = "root-001";

    @Test
    void toEntity_fromDeviceImage() {
        Summary summary = new Summary(TYPE, true, PROTOCOL, PARENT_ID, ROOT_ID,
                List.of("member-1", "member-2"));
        summary.lastOnline(new Date(1000));
        summary.lastOffline(new Date(2000));

        DeviceImage image = new DeviceImage(summary.type());
        image.did(DID);
        image.summary(summary);

        DeviceRegistry entity = DeviceRegistryMapper.toEntity(image, ACCESS_POINT);

        assertEquals(DID, entity.did);
        assertEquals(TYPE, entity.type);
        assertTrue(entity.online);
        assertEquals(PROTOCOL, entity.protocol);
        assertEquals(PARENT_ID, entity.parentId);
        assertEquals(ROOT_ID, entity.rootId);
        assertEquals(List.of("member-1", "member-2"), entity.members);
        assertEquals(ACCESS_POINT, entity.accessPoint);
        assertEquals(1000, entity.lastOnline.getTime());
        assertEquals(2000, entity.lastOffline.getTime());
    }

    @Test
    void toEntity_fromDidAndSummary() {
        Summary summary = new Summary(TYPE, false, "mqtt", null, null);
        summary.members(null);

        DeviceRegistry entity = DeviceRegistryMapper.toEntity(DID, summary, ACCESS_POINT);

        assertEquals(DID, entity.did);
        assertEquals(TYPE, entity.type);
        assertFalse(entity.online);
        assertEquals("mqtt", entity.protocol);
        assertNull(entity.parentId);
        assertNull(entity.rootId);
        assertTrue(entity.members.isEmpty());
        assertEquals(ACCESS_POINT, entity.accessPoint);
        assertNotNull(entity.lastOnline);
        assertNotNull(entity.lastOffline);
    }

    @Test
    void toEntity_withNullType() {
        Summary summary = new Summary();
        summary.online(true).protocol(PROTOCOL);
        // type is null

        DeviceRegistry entity = DeviceRegistryMapper.toEntity(DID, summary, ACCESS_POINT);

        assertEquals(DID, entity.did);
        assertNull(entity.type);
        assertTrue(entity.online);
    }

    @Test
    void toEntity_withNullMembers() {
        Summary summary = new Summary(TYPE, true, PROTOCOL, PARENT_ID, ROOT_ID, (List<String>) null);

        DeviceRegistry entity = DeviceRegistryMapper.toEntity(DID, summary, ACCESS_POINT);

        assertTrue(entity.members.isEmpty());
    }

    @Test
    void toEntity_withNullLastOnline() {
        Summary summary = new Summary(TYPE, true, PROTOCOL, PARENT_ID, ROOT_ID);
        summary.lastOnline((Date) null);
        summary.lastOffline((Date) null);

        DeviceRegistry entity = DeviceRegistryMapper.toEntity(DID, summary, ACCESS_POINT);

        assertNull(entity.lastOnline);
        assertNull(entity.lastOffline);
    }

    @Test
    void toEntities() {
        Summary summary1 = new Summary(TYPE, true, PROTOCOL, null, DID);
        Summary summary2 = new Summary("urn:test:device:sensor:00000001", false, PROTOCOL, DID, DID);

        DeviceImage image1 = new DeviceImage(summary1.type());
        image1.did("child-1");
        image1.summary(summary1);

        DeviceImage image2 = new DeviceImage(summary2.type());
        image2.did("child-2");
        image2.summary(summary2);

        List<DeviceRegistry> entities = DeviceRegistryMapper.toEntities(List.of(image1, image2), ACCESS_POINT);

        assertEquals(2, entities.size());
        assertEquals("child-1", entities.get(0).did);
        assertEquals(TYPE, entities.get(0).type);
        assertEquals("child-2", entities.get(1).did);
        assertEquals("urn:test:device:sensor:00000001", entities.get(1).type);
    }

    @Test
    void toDevice() {
        DeviceRegistry entity = new DeviceRegistry();
        entity.did = DID;
        entity.type = TYPE;
        entity.online = true;
        entity.protocol = PROTOCOL;
        entity.parentId = PARENT_ID;
        entity.rootId = ROOT_ID;
        entity.members = List.of("member-1");
        entity.lastOnline = new Date(1000);
        entity.lastOffline = new Date(2000);

        Device device = DeviceRegistryMapper.toDevice(entity);

        assertEquals(DID, device.did());
        assertEquals(TYPE, device.summary().type().toString());
        assertTrue(device.summary().online());
        assertEquals(PROTOCOL, device.summary().protocol());
        assertEquals(PARENT_ID, device.summary().parentId());
        assertEquals(ROOT_ID, device.summary().rootId());
        assertEquals(List.of("member-1"), device.summary().members());
        assertEquals(1000, device.summary().lastOnline().getTime());
        assertEquals(2000, device.summary().lastOffline().getTime());
    }

    @Test
    void toDevice_withNullFields() {
        DeviceRegistry entity = new DeviceRegistry();
        entity.did = DID;
        entity.online = false;
        entity.type = null;
        entity.protocol = null;
        entity.parentId = null;
        entity.rootId = null;
        entity.members = null;
        entity.lastOnline = null;
        entity.lastOffline = null;

        Device device = DeviceRegistryMapper.toDevice(entity);

        assertEquals(DID, device.did());
        assertNull(device.summary().type());
        assertFalse(device.summary().online());
        assertNull(device.summary().protocol());
        assertNull(device.summary().parentId());
        assertNull(device.summary().rootId());
        assertTrue(device.summary().members().isEmpty());
        assertNotNull(device.summary().lastOnline());
        assertNotNull(device.summary().lastOffline());
    }
}
