package com.SmartDeviceManager.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

class DeviceRegistryTest {

    @Test
    void loadsValidDevicesAndFindsByNameOrRefName() throws Exception {
        DeviceRegistry registry = registry("""
                {"devices":[
                  {"refName":"ref1","name":"Lamp","ip":"127.0.0.1","offlineDetection":true},
                  {"refName":"ref2","name":"Desk","ip":"127.0.0.2","offlineDetection":false},
                  {"refName":null,"name":"Ignored","ip":"127.0.0.3"}
                ]}
                """);

        assertEquals(2, registry.getAll().size());
        assertEquals("ref1", registry.getByName("lamp").getRefName());
        assertEquals("Desk", registry.getByRefName("ref2").getName());
        assertTrue(registry.getByRefName("ref1").isOfflineDetection());
        assertNull(registry.getByName("missing"));
        assertNull(registry.getByRefName("missing"));
    }

    @Test
    void getAllReturnsDefensiveCopy() throws Exception {
        DeviceRegistry registry = registry("""
                {"devices":[{"refName":"ref","name":"Lamp","ip":"127.0.0.1"}]}
                """);

        assertNotSame(registry.getAll(), registry.getAll());
        assertThrows(UnsupportedOperationException.class, () -> registry.getAll().clear());
    }

    @Test
    void emptyOrMissingDevicesFileLoadsNoDevices() throws Exception {
        assertTrue(registry("{}").getAll().isEmpty());
        assertTrue(registry("null").getAll().isEmpty());
    }

    @Test
    void invalidJsonIsWrappedAsIOException() {
        assertThrows(IOException.class, () -> registry("{not-json"));
    }

    private DeviceRegistry registry(String json) throws Exception {
        DeviceRegistry registry = new DeviceRegistry();
        ReflectionTestUtils.setField(registry, "devicesResource", new ByteArrayResource(json.getBytes()));
        Method init = DeviceRegistry.class.getDeclaredMethod("init");
        init.setAccessible(true);
        try {
            init.invoke(registry);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
        return registry;
    }
}
