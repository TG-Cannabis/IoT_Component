package simulation.config;

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
     * @throws NullPointerException if any argument is null.
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
     * Loads configuration from environment variables using Dotenv.
     * Expects "MQTT_BROKER", "MQTT_PUBLISHER_ID", and "MQTT_TOPIC" variables.
     * Assumes a .env file exists in "src/main/resources".
     *
     * @return A PublisherConfig instance loaded from environment variables.
     * @throws IllegalStateException if required environment variables are missing or empty.
     */
    public static PublisherConfig loadFromEnv() {
        // Consider making the dotenv loading path configurable if needed
        Dotenv dotenv = Dotenv.configure().directory("src/main/resources").ignoreIfMissing().load();

        String broker = dotenv.get("MQTT_BROKER");
        String clientId = dotenv.get("MQTT_PUBLISHER_ID");
        String topic = dotenv.get("MQTT_TOPIC");

        if (broker == null || broker.trim().isEmpty()) {
            throw new IllegalStateException("Missing or empty environment variable: MQTT_BROKER");
        }
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalStateException("Missing or empty environment variable: MQTT_PUBLISHER_ID");
        }
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalStateException("Missing or empty environment variable: MQTT_TOPIC");
        }

        return new PublisherConfig(broker, clientId, topic);
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