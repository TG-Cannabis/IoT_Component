package com.tgcannabis;

import com.tgcannabis.config.PublisherConfig;
import com.tgcannabis.config.SimulationConfig;
import com.tgcannabis.generator.SensorDataGenerator;
import com.tgcannabis.publisher.MqttDataPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class SimulationAppTest {
    private MqttDataPublisher mockPublisher;
    private SensorDataGenerator mockGenerator;

    @BeforeEach
    void setup() {
        mockPublisher = mock(MqttDataPublisher.class);
        mockGenerator = mock(SensorDataGenerator.class);
    }

    @Test
    void shouldConnectAndStartPublishing() throws Exception {
        // Create mocks
        PublisherConfig mockPublisherConfig = mock(PublisherConfig.class);
        SimulationConfig mockSimulationConfig = mock(SimulationConfig.class);
        SensorDataGenerator mockGenerator = mock(SensorDataGenerator.class);
        MqttDataPublisher mockPublisher = mock(MqttDataPublisher.class);

        // Stub behavior
        when(mockPublisher.isConnected()).thenReturn(true);
        doNothing().when(mockPublisher).connect();
        doNothing().when(mockPublisher).startPublishing(anyLong(), any());
        doNothing().when(mockPublisher).close();

        // Create the application with mocks
        SimulationApp app = new SimulationApp(
                mockPublisherConfig,
                mockSimulationConfig,
                mockGenerator,
                mockPublisher
        );

        // Run the app in a background thread
        Thread appThread = new Thread(() -> {
            try {
                app.start();
            } catch (Exception e) {
                System.err.println("App thread error: " + e.getMessage());
            }
        });
        appThread.start();

        // Await until methods are called
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(mockPublisher).connect();
            verify(mockPublisher).startPublishing(30, TimeUnit.SECONDS);
        });

        mockPublisher.close();

        // Await cleanup
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(mockPublisher).close();
        });

        appThread.join(2000); // Allow thread to finish gracefully
    }
}
