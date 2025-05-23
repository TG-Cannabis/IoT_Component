package com.tgcannabis.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PublisherConfigTest {
    private MockedStatic<Dotenv> dotenvStatic;

    @BeforeEach
    void setUp() {
        dotenvStatic = mockStatic(Dotenv.class);
    }

    @AfterEach
    void teardown() {
        if (dotenvStatic != null) {
            dotenvStatic.close();
        }
    }

    private void setupDotenvMock(String broker, String clientId, String topic) {
        Dotenv mockDotenv = mock(Dotenv.class);
        DotenvBuilder mockBuilder = mock(DotenvBuilder.class);

        // Configure the static mock for Dotenv
        dotenvStatic.when(Dotenv::configure).thenReturn(mockBuilder);
        when(mockBuilder.ignoreIfMissing()).thenReturn(mockBuilder);
        when(mockBuilder.load()).thenReturn(mockDotenv);

        // Mock dotenv.get() for specific variables
        when(mockDotenv.get("MQTT_BROKER")).thenReturn(broker);
        when(mockDotenv.get("MQTT_PUBLISHER_ID")).thenReturn(clientId);
        when(mockDotenv.get("MQTT_TOPIC")).thenReturn(topic);

    }

    @Test
    void shouldThrowIfBrokerUrlMissing() {
        // Arrange
        setupDotenvMock(null, "publisher-123", "sensor/data"); // Broker missing

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, PublisherConfig::loadFromEnv);
        assertEquals("Missing or empty environment variable: MQTT_BROKER", ex.getMessage());
    }

    @Test
    void shouldThrowIfBrokerUrlEmpty() {
        // Arrange
        setupDotenvMock("  ", "publisher-123", "sensor/data"); // Broker empty string

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, PublisherConfig::loadFromEnv);
        assertEquals("Broker URL cannot be empty", ex.getMessage());
    }

    @Test
    void shouldThrowIfClientIdMissing() {
        // Arrange
        setupDotenvMock("tcp://localhost:1883", null, "sensor/data"); // Client ID missing

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, PublisherConfig::loadFromEnv);
        assertEquals("Missing or empty environment variable: MQTT_PUBLISHER_ID", ex.getMessage());
    }

    @Test
    void shouldThrowIfClientIdEmpty() {
        // Arrange
        setupDotenvMock("tcp://localhost:1883", " ", "sensor/data"); // Client ID empty string

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, PublisherConfig::loadFromEnv);
        assertEquals("Client ID cannot be empty", ex.getMessage());
    }

    @Test
    void shouldThrowIfTopicMissing() {
        // Arrange
        setupDotenvMock("tcp://localhost:1883", "publisher-123", null); // Topic missing

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, PublisherConfig::loadFromEnv);
        assertEquals("Missing or empty environment variable: MQTT_TOPIC", ex.getMessage());
    }

    @Test
    void shouldThrowIfTopicEmpty() {
        // Arrange
        setupDotenvMock("tcp://localhost:1883", "publisher-123", "\t"); // Topic empty string

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, PublisherConfig::loadFromEnv);
        assertEquals("Topic cannot be empty", ex.getMessage());
    }

    @Test
    void constructorShouldThrowIfBrokerUrlIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new PublisherConfig(null, "clientId", "topic"));
        assertEquals("Broker URL cannot be null", ex.getMessage());
    }

    @Test
    void constructorShouldThrowIfClientIdIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new PublisherConfig("brokerUrl", null, "topic"));
        assertEquals("Client ID cannot be null", ex.getMessage());
    }

    @Test
    void constructorShouldThrowIfTopicIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new PublisherConfig("brokerUrl", "clientId", null));
        assertEquals("Topic cannot be null", ex.getMessage());
    }

    @Test
    void constructorShouldThrowIfBrokerUrlIsEmpty() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new PublisherConfig("", "clientId", "topic"));
        assertEquals("Broker URL cannot be empty", ex.getMessage());
    }

    @Test
    void constructorShouldThrowIfClientIdIsEmpty() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new PublisherConfig("brokerUrl", " ", "topic"));
        assertEquals("Client ID cannot be empty", ex.getMessage());
    }

    @Test
    void constructorShouldThrowIfTopicIsEmpty() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new PublisherConfig("brokerUrl", "clientId", "\n"));
        assertEquals("Topic cannot be empty", ex.getMessage());
    }

    @Test
    void toStringShouldReturnCorrectFormat() {
        PublisherConfig config = new PublisherConfig("tcp://test:1883", "test-client", "test/topic");
        String expected = "PublisherConfig{brokerUrl='tcp://test:1883', clientId='test-client', topic='test/topic'}";
        assertEquals(expected, config.toString());
    }
}
