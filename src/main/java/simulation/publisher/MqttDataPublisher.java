package simulation.publisher;

import com.google.gson.Gson;
import lombok.Getter;
import model.SensorData; // Assuming this model class exists
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence; // Good default
import simulation.config.PublisherConfig;
import simulation.generator.SensorDataGenerator;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger; // Using Java Util Logging

/**
 * Connects to an MQTT broker and periodically publishes simulated sensor data.
 * Handles connection, disconnection, and data publishing loop.
 */
public class MqttDataPublisher implements AutoCloseable { // Implement AutoCloseable for try-with-resources

    // Using Java Util Logging for better logging control
    private static final Logger LOGGER = Logger.getLogger(MqttDataPublisher.class.getName());

    private final PublisherConfig config;
    private final SensorDataGenerator dataGenerator;
    private final Gson gson;
    private MqttClient mqttClient;
    private final Object lock = new Object(); // For synchronizing access to mqttClient

    /**
     * -- GETTER --
     *  Checks if the publisher loop is currently running.
     *
     * @return true if the publishing loop is active, false otherwise.
     */
    // Flag to control the publishing loop
    @Getter
    private volatile boolean running = false;

    /**
     * Constructs an MqttDataPublisher.
     *
     * @param config        The publisher configuration. Cannot be null.
     * @param dataGenerator The generator for sensor data. Cannot be null.
     */
    public MqttDataPublisher(PublisherConfig config, SensorDataGenerator dataGenerator) {
        this.config = Objects.requireNonNull(config, "PublisherConfig cannot be null");
        this.dataGenerator = Objects.requireNonNull(dataGenerator, "SensorDataGenerator cannot be null");
        this.gson = new Gson(); // Gson is thread-safe, can be reused
    }

    /**
     * Connects to the MQTT broker specified in the configuration.
     * Uses default MqttConnectOptions (clean session true).
     *
     * @throws MqttException if the connection fails.
     */
    public void connect() throws MqttException {
        synchronized (lock) {
            if (mqttClient != null && mqttClient.isConnected()) {
                LOGGER.info("Already connected.");
                return;
            }

                LOGGER.log(Level.INFO, "Connecting to MQTT broker: {0} with client ID: {1}",
                        new Object[]{config.getBrokerUrl(), config.getClientId()});
                // Using MemoryPersistence, suitable for most non-critical scenarios
                mqttClient = new MqttClient(config.getBrokerUrl(), config.getClientId(), new MemoryPersistence());

                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true); // Standard behavior for publishers like this
                options.setAutomaticReconnect(true); // Enable automatic reconnect
                options.setConnectionTimeout(10); // Connection timeout in seconds
                options.setKeepAliveInterval(20); // Keep alive interval in seconds

            try {
                mqttClient.connect(options);
                LOGGER.info("Successfully connected to MQTT broker.");
            } catch (MqttException e) {
                LOGGER.log(Level.SEVERE, "Failed to connect to MQTT broker.", e);
                // Clean up client if connection failed partially
                if (mqttClient != null) {
                    try {
                        if (mqttClient.isConnected()) {
                            mqttClient.disconnect();
                        }
                        mqttClient.close();
                    } catch (MqttException closeEx) {
                        LOGGER.log(Level.SEVERE, "Error closing MQTT client after connection failure.", closeEx);
                    } finally {
                        mqttClient = null;
                    }
                }
                throw e; // Re-throw the original exception
            }
        }
    }

    /**
     * Starts the process of periodically generating and publishing sensor data.
     * This method will run indefinitely until stopPublishing() is called or an unrecoverable error occurs.
     * Make sure connect() has been called successfully before starting.
     *
     * @param interval The interval between publishing messages.
     * @param timeUnit The time unit for the interval (e.g., TimeUnit.SECONDS).
     * @throws IllegalStateException if the client is not connected.
     */
    public void startPublishing(long interval, TimeUnit timeUnit) {
        synchronized (lock) {
            if (mqttClient == null || !mqttClient.isConnected()) {
                throw new IllegalStateException("MQTT client is not connected. Call connect() first.");
            }
        }

        running = true;
        LOGGER.log(Level.INFO, "Starting data publishing every {0} {1}", new Object[]{interval, timeUnit.name()});

        while (running) {
            try {
                SensorData data = dataGenerator.generate();
                publishData(data);
                // Wait for the next interval
                timeUnit.sleep(interval);
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Publishing thread interrupted. Stopping.");
                Thread.currentThread().interrupt(); // Re-interrupt the thread
                running = false; // Stop the loop
            } catch (MqttException e) {
                // Handle MQTT exceptions during publish (e.g., connection lost temporarily)
                // Paho's automatic reconnect might handle this, but logging is good.
                LOGGER.log(Level.SEVERE, "MQTT error during publishing. Attempting to continue.", e);
                // Optional: Implement a backoff strategy or check connection status more rigorously here.
                // If automatic reconnect is enabled, it might recover. If not, the loop might fail repeatedly.
                if (!mqttClient.isConnected()) {
                    LOGGER.severe("MQTT client is disconnected. Stopping publisher.");
                    running = false; // Stop if definitely disconnected
                }
                // Add a small delay to avoid spamming logs if errors persist
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            } catch (Exception e) {
                // Catch any other unexpected exceptions during data generation or publishing
                LOGGER.log(Level.SEVERE, "Unexpected error during publishing loop. Stopping.", e);
                running = false; // Stop on unexpected errors
            }
        }
        LOGGER.info("Data publishing stopped.");
    }

    /**
     * Publishes a single SensorData object to the configured MQTT topic.
     *
     * @param data The SensorData to publish. Cannot be null.
     * @throws MqttException if publishing fails.
     * @throws IllegalStateException if the client is not connected.
     * @throws NullPointerException if data is null.
     */
    public void publishData(SensorData data) throws MqttException {
        Objects.requireNonNull(data, "SensorData cannot be null");

        synchronized (lock) {
            if (mqttClient == null || !mqttClient.isConnected()) {
                throw new IllegalStateException("MQTT client is not connected.");
            }

            String payload = gson.toJson(data);
            MqttMessage message = new MqttMessage(payload.getBytes()); // Default charset UTF-8
            message.setQos(1); // Quality of Service: At least once

            // Perform the publish operation
            mqttClient.publish(config.getTopic(), message);
            LOGGER.log(Level.FINE, "Published data to topic ''{0}'': {1}", new Object[]{config.getTopic(), payload});
        }
    }

    /**
     * Stops the data publishing loop gracefully.
     */
    public void stopPublishing() {
        LOGGER.info("Received stop signal. Stopping publisher...");
        this.running = false;
    }

    /**
     * Disconnects from the MQTT broker and releases resources.
     * This method is idempotent (safe to call multiple times).
     */
    public void disconnect() {
        synchronized (lock) {
            if (mqttClient != null && mqttClient.isConnected()) {
                try {
                    LOGGER.info("Disconnecting from MQTT broker...");
                    mqttClient.disconnect();
                    LOGGER.info("Successfully disconnected.");
                } catch (MqttException e) {
                    LOGGER.log(Level.SEVERE, "Error while disconnecting from MQTT broker.", e);
                }
            }
            // Close the client to release resources, even if disconnect failed or was not connected
            if (mqttClient != null) {
                try {
                    mqttClient.close();
                } catch (MqttException e) {
                    LOGGER.log(Level.SEVERE, "Error while closing MQTT client.", e);
                } finally {
                    mqttClient = null; // Ensure client reference is cleared
                }
            }
        }
    }

    /**
     * Closes the publisher, ensuring disconnection.
     * Implements AutoCloseable for use in try-with-resources statements.
     */
    @Override
    public void close() {
        stopPublishing(); // Signal the loop to stop if it's running
        disconnect();     // Disconnect and close the client
    }

    /**
     * Checks if the MQTT client is currently connected to the broker.
     * @return true if connected, false otherwise.
     */
    public boolean isConnected() {
        synchronized(lock) {
            return mqttClient != null && mqttClient.isConnected();
        }
    }
}