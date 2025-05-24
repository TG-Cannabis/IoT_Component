package com.tgcannabis;

import com.tgcannabis.config.PublisherConfig;
import com.tgcannabis.config.SimulationConfig;
import com.tgcannabis.generator.SensorDataGenerator;
import com.tgcannabis.publisher.MqttDataPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class SimulationAppTest {
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

    @Test
    void noArgsConstructor_shouldLoadConfigsAndCreateGeneratorAndPublisher() {
        final Object[] generatorArgsHolder = new Object[1];
        final Object[] publisherArgsHolder = new Object[2];

        try (
                MockedStatic<PublisherConfig> pubConfigMock = mockStatic(PublisherConfig.class);
                MockedStatic<SimulationConfig> simConfigMock = mockStatic(SimulationConfig.class);

                MockedConstruction<SensorDataGenerator> generatorConstruction = mockConstruction(SensorDataGenerator.class,
                        (mock, context) -> {
                            generatorArgsHolder[0] = context.arguments().getFirst();
                        });

                MockedConstruction<MqttDataPublisher> publisherConstruction = mockConstruction(MqttDataPublisher.class,
                        (mock, context) -> {
                            publisherArgsHolder[0] = context.arguments().get(0);
                            publisherArgsHolder[1] = context.arguments().get(1);
                        });
        ) {
            PublisherConfig pubConfig = mock(PublisherConfig.class);
            SimulationConfig simConfig = mock(SimulationConfig.class);

            pubConfigMock.when(PublisherConfig::loadFromEnv).thenReturn(pubConfig);
            simConfigMock.when(SimulationConfig::loadFromEnv).thenReturn(simConfig);

            // Call constructor
            new SimulationApp();

            // Verify static calls
            pubConfigMock.verify(PublisherConfig::loadFromEnv);
            simConfigMock.verify(SimulationConfig::loadFromEnv);

            // Verify that SensorDataGenerator constructor received simConfig
            assertSame(simConfig, generatorArgsHolder[0]);

            // Verify that MqttDataPublisher constructor received pubConfig and generator mock
            assertSame(pubConfig, publisherArgsHolder[0]);
            assertSame(generatorConstruction.constructed().getFirst(), publisherArgsHolder[1]);
        }
    }

}
