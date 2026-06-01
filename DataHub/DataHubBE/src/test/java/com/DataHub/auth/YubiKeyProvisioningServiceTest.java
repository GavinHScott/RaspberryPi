package com.DataHub.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class YubiKeyProvisioningServiceTest {

    @Test
    void pairWithYubiKeyRegistersClientWhenProofMatchesConfiguredGate() {
        DataHubKeyRegistry registry = new DataHubKeyRegistry("");
        YubiKeyProvisioningService service = new YubiKeyProvisioningService(registry, true, "valid-proof");

        DataHubClient client = service.pairWithYubiKey(
                "KitchenPi",
                "kitchenpi-main",
                "public-key",
                Set.of(DataHubPermission.READ, DataHubPermission.WRITE),
                "valid-proof");

        assertEquals("KitchenPi", client.name());
        assertEquals(client, registry.getClient("kitchenpi-main"));
        assertTrue(client.canWrite());
    }

    @Test
    void pairWithYubiKeyRejectsInvalidProof() {
        DataHubKeyRegistry registry = new DataHubKeyRegistry("");
        YubiKeyProvisioningService service = new YubiKeyProvisioningService(registry, true, "valid-proof");

        SecurityException exception = assertThrows(SecurityException.class, () -> service.pairWithYubiKey(
                "KitchenPi",
                "kitchenpi-main",
                "public-key",
                Set.of(DataHubPermission.READ),
                "wrong-proof"));

        assertEquals("valid YubiKey proof is required to pair a new key", exception.getMessage());
    }

    @Test
    void pairWithYubiKeyRejectsPairingWhenGateIsDisabled() {
        DataHubKeyRegistry registry = new DataHubKeyRegistry("");
        YubiKeyProvisioningService service = new YubiKeyProvisioningService(registry, false, "valid-proof");

        assertThrows(SecurityException.class, () -> service.pairWithYubiKey(
                "KitchenPi",
                "kitchenpi-main",
                "public-key",
                Set.of(DataHubPermission.READ),
                "valid-proof"));
    }

    @Test
    void pairWithYubiKeyRequiresClientName() {
        DataHubKeyRegistry registry = new DataHubKeyRegistry("");
        YubiKeyProvisioningService service = new YubiKeyProvisioningService(registry, true, "valid-proof");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.pairWithYubiKey(
                "",
                "kitchenpi-main",
                "public-key",
                Set.of(DataHubPermission.READ),
                "valid-proof"));

        assertEquals("clientName is required", exception.getMessage());
    }
}
