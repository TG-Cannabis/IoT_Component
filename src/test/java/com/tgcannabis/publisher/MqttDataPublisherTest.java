package com.tgcannabis.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgcannabis.iot_component.model.SensorData;
import com.tgcannabis.config.PublisherConfig;
import com.tgcannabis.generator.SensorDataGenerator;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MqttDataPublisherTest {
    private PublisherConfig config;
    private SensorDataGenerator generator;
    private MqttDataPublisher publisher;
    private MqttClient mockClient;

    @BeforeEach
    void setUp() {
        config = new PublisherConfig(
                "tcp://mqtt-test:8083",
                "test-client",
                "iot/test"
        );

        generator = mock(SensorDataGenerator.class);
        mockClient = mock(MqttClient.class);
        when(mockClient.isConnected()).thenReturn(true);

        publisher = new MqttDataPublisher(config, generator, mockClient);
    }

    @Test
    void testPublishData_whenConnected_shouldPublish() throws Exception {
        // Given
        SensorData data = new SensorData();
        data.setSensorType("temperature");
        data.setSensorId("sensor_1");
        data.setLocation("room1");
        data.setTimestamp(123456789L); // Use fixed value for deterministic test
        data.setValue(22.5);

        // When
        publisher.connect();
        publisher.publishData(data);

        // Then
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mockClient).publish(eq("iot/test"), messageCaptor.capture());

        MqttMessage capturedMessage = messageCaptor.getValue();
        String payload = new String(capturedMessage.getPayload());

        // Assert payload content
        assertTrue(payload.contains("\"sensorType\":\"temperature\""));
        assertTrue(payload.contains("\"sensorId\":\"sensor_1\""));
        assertTrue(payload.contains("\"location\":\"room1\""));
        assertTrue(payload.contains("\"value\":22.5"));
        assertTrue(payload.contains("\"timestamp\":123456789"));

        // Assert deserialized object equality
        ObjectMapper mapper = new ObjectMapper();
        SensorData deserialized = mapper.readValue(payload, SensorData.class);

        assertEquals(data.getSensorType(), deserialized.getSensorType());
        assertEquals(data.getSensorId(), deserialized.getSensorId());
        assertEquals(data.getLocation(), deserialized.getLocation());
        assertEquals(data.getValue(), deserialized.getValue(), 0.001);
        assertEquals(data.getTimestamp(), deserialized.getTimestamp());
    }

    @Test
    void testPublishData_whenDataIsNull_shouldThrowNullPointerException() {
        when(mockClient.isConnected()).thenReturn(true);

        publisher = new MqttDataPublisher(config, generator, mockClient);
        assertThrows(NullPointerException.class, () -> publisher.publishData(null));
    }

    @Test
    void testPublishData_whenNotConnected_shouldThrowIllegalStateException() {
        when(mockClient.isConnected()).thenReturn(false); // Simulate disconnected

        publisher = new MqttDataPublisher(config, generator, mockClient);

        try {
            publisher.publishData(new SensorData());
            fail("Expected IllegalStateException to be thrown");
        } catch (IllegalStateException | MqttException ex) {
            assertEquals(IllegalStateException.class, ex.getClass());
        }
    }

    @Test
    void testConnect_whenAlreadyConnected_shouldNotReconnect() throws Exception {
        when(mockClient.isConnected()).thenReturn(true);

        publisher = new MqttDataPublisher(config, generator, mockClient);
        publisher.connect();

        verify(mockClient, never()).connect(any());
    }

    @Test
    void testStopPublishing_shouldSetRunningToFalse() {
        publisher = new MqttDataPublisher(config, generator, mock(MqttClient.class));
        publisher.stopPublishing();
        assertFalse(publisher.isRunning());
    }

    @Test
    void testDisconnect_whenConnected_shouldDisconnectAndClose() throws Exception {
        when(mockClient.isConnected()).thenReturn(true);

        publisher = new MqttDataPublisher(config, generator, mockClient);
        publisher.disconnect();

        verify(mockClient).disconnect();
        verify(mockClient).close();
    }

    @Test
    void testDisconnect_whenNotConnected_shouldStillClose() throws Exception {
        when(mockClient.isConnected()).thenReturn(false);

        publisher = new MqttDataPublisher(config, generator, mockClient);
        publisher.disconnect();

        verify(mockClient, never()).disconnect();
        verify(mockClient).close();
    }

    @Test
    void testStartPublishing_shouldPublishAtInterval() throws Exception {
        SensorData mockData = new SensorData();
        when(generator.generate()).thenReturn(mockData);
        when(mockClient.isConnected()).thenReturn(true);

        CountDownLatch latch = new CountDownLatch(2); // Wait for at least 2 publishes

        // Mock publish to count down the latch each time it's called
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockClient).publish(eq(config.getTopic()), any(MqttMessage.class));

        publisher = new MqttDataPublisher(config, generator, mockClient);
        publisher.connect();

        Thread thread = new Thread(() -> publisher.startPublishing(100, TimeUnit.MILLISECONDS));
        thread.start();

        // Wait until at least 2 publish calls are observed or timeout
        boolean published = latch.await(1, TimeUnit.SECONDS);
        publisher.stopPublishing();
        thread.join();

        assertTrue(published, "Expected at least 2 publish calls within the timeout");
        verify(mockClient, atLeast(2)).publish(eq(config.getTopic()), any(MqttMessage.class));
    }

    @Test
    void testConnect_whenConnectFails_shouldThrowAndCleanup() throws Exception {
        when(mockClient.isConnected()).thenReturn(false);
        doThrow(new MqttException(new Throwable("Connection failed"))).when(mockClient).connect(any());

        publisher = new MqttDataPublisher(config, generator, mockClient);

        assertThrows(MqttException.class, publisher::connect);

        verify(mockClient, never()).disconnect(); // Only called if connected
        verify(mockClient).close(); // Still should be closed even if connect failed
    }

    @Test
    void testPublishData_whenPublishFails_shouldPropagateException() throws Exception {
        SensorData data = new SensorData();
        data.setSensorType("temp");
        data.setSensorId("id");
        data.setLocation("loc");
        data.setTimestamp(System.currentTimeMillis());
        data.setValue(42.0);

        when(mockClient.isConnected()).thenReturn(true);
        doThrow(new RuntimeException("Publish failed")).when(mockClient).publish(anyString(), any(MqttMessage.class));

        publisher = new MqttDataPublisher(config, generator, mockClient);

        assertThrows(RuntimeException.class, () -> publisher.publishData(data));

        verify(mockClient).publish(anyString(), any(MqttMessage.class));
    }

    @Test
    void testDisconnect_whenExceptionDuringDisconnect_shouldStillClose() throws Exception {
        when(mockClient.isConnected()).thenReturn(true);
        doThrow(new MqttException(new Throwable("Disconnection failed"))).when(mockClient).disconnect();

        publisher = new MqttDataPublisher(config, generator, mockClient);
        publisher.disconnect();

        verify(mockClient).disconnect();
        verify(mockClient, atLeastOnce()).close();
    }

    @Test
    void testStartPublishing_whenInterrupted_shouldStopGracefully() throws Exception {
        SensorData mockData = new SensorData();
        when(generator.generate()).thenReturn(mockData);
        when(mockClient.isConnected()).thenReturn(true);

        publisher = new MqttDataPublisher(config, generator, mockClient);
        publisher.connect();

        CountDownLatch startedLatch = new CountDownLatch(1);

        Thread thread = new Thread(() -> {
            startedLatch.countDown(); // Signal that thread has started
            publisher.startPublishing(5, TimeUnit.SECONDS);
        });
        thread.start();

        // Wait until the publishing thread has started (entered startPublishing)
        assertTrue(startedLatch.await(1, TimeUnit.SECONDS), "Publishing thread did not start in time");

        thread.interrupt(); // Interrupt the thread
        thread.join(2000);

        assertFalse(publisher.isRunning());
    }

    @Test
    void testStartPublishing_whenGeneratorFails_shouldStop() throws Exception {
        SensorDataGenerator faultyGenerator = mock(SensorDataGenerator.class);
        when(faultyGenerator.generate()).thenThrow(new RuntimeException("Generator failed"));

        when(mockClient.isConnected()).thenReturn(true);

        publisher = new MqttDataPublisher(config, faultyGenerator, mockClient);
        publisher.connect();

        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<?> future = executor.submit(() -> publisher.startPublishing(100, TimeUnit.MILLISECONDS));

        // Wait up to 1 second for the task to complete (publisher should stop due to exception)
        try {
            future.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // Timeout means it didn't stop in time - fail the test
            fail("Publisher did not stop within timeout");
        } finally {
            executor.shutdownNow();
        }

        assertFalse(publisher.isRunning(), "Publisher should stop due to generator exception");
    }


}
