package com.SmartDeviceManager.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.SmartDeviceManager.TestDevices;
import com.SmartDeviceManager.model.DeviceCommand;
import com.SmartDeviceManager.model.SmartDevice;
import com.SmartDeviceManager.network.DeviceUdpClient;
import com.SmartDeviceManager.payload.PayloadBuilder;
import com.SmartDeviceManager.registry.DeviceRegistry;

class CommandParserTest {

    private final DeviceRegistry registry = mock(DeviceRegistry.class);
    private final PayloadBuilder payloadBuilder = mock(PayloadBuilder.class);
    private final DeviceUdpClient udpClient = mock(DeviceUdpClient.class);
    private final SunriseService sunriseService = mock(SunriseService.class);
    private final SunsetService sunsetService = mock(SunsetService.class);
    private final SmartDevice device = TestDevices.device("ref", "lamp");
    private final CommandParser parser = new CommandParser(registry, payloadBuilder, udpClient, sunriseService, sunsetService);

    @BeforeEach
    void setUp() {
        when(registry.getByName("lamp")).thenReturn(device);
    }

    @Test
    void rejectsBlankAndMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> parser.parseAndExecute(null));
        assertThrows(IllegalArgumentException.class, () -> parser.parseAndExecute("   "));
        assertThrows(IllegalArgumentException.class, () -> parser.parseAndExecute("lamp"));
    }

    @Test
    void rejectsUnknownDeviceAndCommand() {
        when(registry.getByName("missing")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> parser.parseAndExecute("missing on"));
        assertThrows(IllegalArgumentException.class, () -> parser.parseAndExecute("lamp sparkle"));
    }

    @Test
    void validatesCommandParameters() {
        assertThrows(IllegalArgumentException.class, () -> parser.parseAndExecute("lamp on 1"));
        assertThrows(IllegalArgumentException.class, () -> parser.parseAndExecute("lamp temp"));
        assertThrows(IllegalArgumentException.class, () -> parser.parseAndExecute("lamp fade nope"));
        assertThrows(IllegalArgumentException.class, () -> parser.parseAndExecute("lamp rgb 1 2"));
        assertThrows(IllegalArgumentException.class, () -> parser.parseAndExecute("lamp transition 101"));
        assertThrows(IllegalArgumentException.class, () -> parser.parseAndExecute("lamp sunrise 0"));
    }

    @Test
    void buildsAndSendsDeviceCommand() throws Exception {
        when(payloadBuilder.build(new DeviceCommand("ref", "rgb", List.of("1", "2", "3")))).thenReturn("payload");

        parser.parseAndExecute(" lamp RGB 1 2 3 ");

        verify(udpClient).send("ref", "payload");
        verify(sunsetService).recordManualCommand(device, "rgb");
    }

    @Test
    void startsSunriseWithoutSendingUdpPayload() throws Exception {
        parser.parseAndExecute("lamp sunrise 30");

        verify(sunriseService).start("ref", 30);
        verify(payloadBuilder, never()).build(any());
        verify(udpClient, never()).send(any(), any());
        verify(sunsetService).recordManualCommand(device, "sunrise");
    }
}
