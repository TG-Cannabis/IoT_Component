package com.tgcannabis.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.Getter;

import java.util.Objects;

/**
 * Holds configuration parameters for the MQTT publisher.
 * Provides a convenient way to load settings, typically from environment variables.
 */
@Getter
public class PublisherConfig {

    private final String brokerUrl;
    private final String clientId;
    private final String topic;

    /**
     * Constructs a configuration object with specified parameters.
     *
     * @param brokerUrl The URL of the MQTT broker (e.g., "tcp://localhost:1883"). Cannot be null or empty.
     * @param clientId  The client ID to use when connecting to the broker. Cannot be null or empty.
     * @param topic     The MQTT topic to publish messages to. Cannot be null or empty.
     * @throws NullPointerException     if any argument is null.
     * @throws IllegalArgumentException if any argument is empty.
     */
    public PublisherConfig(String brokerUrl, String clientId, String topic) {
        this.brokerUrl = Objects.requireNonNull(brokerUrl, "Broker URL cannot be null");
        this.clientId = Objects.requireNonNull(clientId, "Client ID cannot be null");
        this.topic = Objects.requireNonNull(topic, "Topic cannot be null");

        if (brokerUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Broker URL cannot be empty");
        }
        if (clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be empty");
        }
        if (topic.trim().isEmpty()) {
            throw new IllegalArgumentException("Topic cannot be empty");
        }
    }

    /**
     * Loads configuration from environment variables or .env file (as fallback).
     * Expects "MQTT_BROKER", "MQTT_PUBLISHER_ID", and "MQTT_TOPIC" variables.
     *
     * @return A PublisherConfig instance loaded from environment variables.
     * @throws IllegalStateException if required environment variables are missing or empty.
     */
    public static PublisherConfig loadFromEnv() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        // Use the shared helper methods to get values
        String broker = getEnvOrThrow(dotenv, "MQTT_BROKER", "Missing or empty environment variable: MQTT_BROKER");
        String clientId = getEnvOrThrow(dotenv, "MQTT_PUBLISHER_ID", "Missing or empty environment variable: MQTT_PUBLISHER_ID");
        String topic = getEnvOrThrow(dotenv, "MQTT_TOPIC", "Missing or empty environment variable: MQTT_TOPIC");

        return new PublisherConfig(broker, clientId, topic);
    }

    /**
     * Gets a value from System env variables (Or Dotenv file as fallback), throwing an exception if not found.
     *
     * @param dotenv       Dotenv instance
     * @param varName      Environment variable name
     * @param errorMessage Error message if not found
     * @return The value found
     * @throws IllegalStateException if the variable is missing or empty
     */
    private static String getEnvOrThrow(Dotenv dotenv, String varName, String errorMessage) {
        String value = System.getenv(varName);
        if (value != null) return value;

        value = dotenv.get(varName);
        if (value != null) return value;

        throw new IllegalArgumentException(errorMessage);
    }

    @Override
    public String toString() {
        return "PublisherConfig{" +
                "brokerUrl='" + brokerUrl + '\'' +
                ", clientId='" + clientId + '\'' +
                ", topic='" + topic + '\'' +
                '}';
    }
}