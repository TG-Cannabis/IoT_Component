package com.tgcannabis.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgcannabis.iot_component.model.SensorData;
import com.tgcannabis.config.PublisherConfig;
import com.tgcannabis.generator.SensorDataGenerator;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MqttDataPublisherTest {
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
        MqttClient mockClient = mock(MqttClient.class);
        when(mockClient.isConnected()).thenReturn(true);

        publisher = new MqttDataPublisher(config, generator, mockClient);
        assertThrows(NullPointerException.class, () -> publisher.publishData(null));
    }

    @Test
    void testPublishData_whenNotConnected_shouldThrowIllegalStateException() {
        MqttClient mockClient = mock(MqttClient.class);
        when(mockClient.isConnected()).thenReturn(false); // Simulate disconnected

        publisher = new MqttDataPublisher(config, generator, mockClient);
        assertThrows(IllegalStateException.class, () -> publisher.publishData(new SensorData()));
    }

    @Test
    void testConnect_whenAlreadyConnected_shouldNotReconnect() throws Exception {
        MqttClient mockClient = mock(MqttClient.class);
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
        MqttClient mockClient = mock(MqttClient.class);
        when(mockClient.isConnected()).thenReturn(true);

        publisher = new MqttDataPublisher(config, generator, mockClient);
        publisher.disconnect();

        verify(mockClient).disconnect();
        verify(mockClient).close();
    }

    @Test
    void testDisconnect_whenNotConnected_shouldStillClose() throws Exception {
        MqttClient mockClient = mock(MqttClient.class);
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

        MqttClient mockClient = mock(MqttClient.class);
        when(mockClient.isConnected()).thenReturn(true);

        publisher = new MqttDataPublisher(config, generator, mockClient);
        publisher.connect();

        Thread thread = new Thread(() -> publisher.startPublishing(100, TimeUnit.MILLISECONDS));
        thread.start();

        Thread.sleep(300);
        publisher.stopPublishing();
        thread.join();

        verify(mockClient, atLeastOnce()).publish(eq(config.getTopic()), any(MqttMessage.class));
    }

    @Test
    void testConnect_whenConnectFails_shouldThrowAndCleanup() throws Exception {
        MqttClient mockClient = mock(MqttClient.class);
        when(mockClient.isConnected()).thenReturn(false);
        doThrow(new RuntimeException("Connection failed")).when(mockClient).connect(any());

        publisher = new MqttDataPublisher(config, generator, mockClient);

        assertThrows(RuntimeException.class, publisher::connect);

        verify(mockClient).connect(any());
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

        MqttClient mockClient = mock(MqttClient.class);
        when(mockClient.isConnected()).thenReturn(true);
        doThrow(new RuntimeException("Publish failed")).when(mockClient).publish(anyString(), any(MqttMessage.class));

        publisher = new MqttDataPublisher(config, generator, mockClient);

        assertThrows(RuntimeException.class, () -> publisher.publishData(data));

        verify(mockClient).publish(anyString(), any(MqttMessage.class));
    }

    @Test
    void testDisconnect_whenExceptionDuringDisconnect_shouldStillClose() throws Exception {
        MqttClient mockClient = mock(MqttClient.class);
        when(mockClient.isConnected()).thenReturn(true);
        doThrow(new RuntimeException("Disconnect failed")).when(mockClient).disconnect();

        publisher = new MqttDataPublisher(config, generator, mockClient);
        publisher.disconnect();

        verify(mockClient).disconnect();
        verify(mockClient).close(); // Still ensure close is called even if disconnect fails
    }

    @Test
    void testStartPublishing_whenInterrupted_shouldStopGracefully() throws Exception {
        SensorData mockData = new SensorData();
        when(generator.generate()).thenReturn(mockData);

        MqttClient mockClient = mock(MqttClient.class);
        when(mockClient.isConnected()).thenReturn(true);

        publisher = new MqttDataPublisher(config, generator, mockClient);
        publisher.connect();

        Thread thread = new Thread(() -> publisher.startPublishing(5, TimeUnit.SECONDS));
        thread.start();

        Thread.sleep(200); // Give it time to enter sleep
        thread.interrupt(); // Simulate interruption
        thread.join(2000);

        assertFalse(publisher.isRunning());
    }

    @Test
    void testStartPublishing_whenGeneratorFails_shouldStop() throws Exception {
        SensorDataGenerator faultyGenerator = mock(SensorDataGenerator.class);
        when(faultyGenerator.generate()).thenThrow(new RuntimeException("Generator failed"));

        MqttClient mockClient = mock(MqttClient.class);
        when(mockClient.isConnected()).thenReturn(true);

        publisher = new MqttDataPublisher(config, faultyGenerator, mockClient);
        publisher.connect();

        Thread thread = new Thread(() -> publisher.startPublishing(100, TimeUnit.MILLISECONDS));
        thread.start();

        thread.join(1000);
        assertFalse(publisher.isRunning()); // Should stop due to generator exception
    }
}
