package com.tgcannabis;

import com.tgcannabis.config.PublisherConfig;
import com.tgcannabis.config.SimulationConfig;
import com.tgcannabis.generator.SensorDataGenerator;
import com.tgcannabis.publisher.MqttDataPublisher;
import org.eclipse.paho.client.mqttv3.MqttClient;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application class to run the MQTT sensor data simulation.
 * Loads configuration, creates publisher and generator, and manages the lifecycle.
 */
public class SimulationApp {

    private static final Logger LOGGER = Logger.getLogger(SimulationApp.class.getName());
    private static final long PUBLISH_INTERVAL_SECONDS = 30; // Publish interval

    private final PublisherConfig publisherConfig;
    private final SimulationConfig simulationConfig;
    private final MqttDataPublisher publisher;
    private final SensorDataGenerator generator;

    public SimulationApp() {
        this.publisherConfig = PublisherConfig.loadFromEnv();
        this.simulationConfig = SimulationConfig.loadFromEnv();
        generator = new SensorDataGenerator(simulationConfig);
        publisher = new MqttDataPublisher(publisherConfig, generator);
    }

    public SimulationApp(PublisherConfig publisherConfig, SimulationConfig simulationConfig, SensorDataGenerator generator, MqttDataPublisher publisher) {
        this.publisherConfig = publisherConfig;
        this.simulationConfig = simulationConfig;
        this.generator = generator;
        this.publisher = publisher;
    }

    void start() {
        try {
            // 1. Load Configuration
            PublisherConfig publisherConfig = PublisherConfig.loadFromEnv();
            LOGGER.log(Level.INFO, "Publisher configuration loaded: {0}", publisherConfig);
            SimulationConfig simulationConfig = SimulationConfig.loadFromEnv();
            LOGGER.log(Level.INFO, "Simulation configuration loaded: {0}", simulationConfig);

            // 4. Add Shutdown Hook for graceful termination
            // This ensures disconnection even if the application is stopped externally (Ctrl+C)
            MqttDataPublisher finalPublisher = publisher; // Need effectively final variable for lambda
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Shutdown hook triggered. Cleaning up...");
                finalPublisher.close(); // Calls stopPublishing() and disconnect()
                LOGGER.info("Cleanup finished.");
            }));


            // 5. Connect to MQTT Broker
            publisher.connect(); // Handle potential MqttException during connection

            // 6. Start Publishing Loop (runs until stopped or error)
            publisher.startPublishing(PUBLISH_INTERVAL_SECONDS, TimeUnit.SECONDS);

            // The startPublishing loop runs in the main thread here.
            // The application will keep running until interrupted (e.g., Ctrl+C),
            // which triggers the shutdown hook.

        } catch (Exception e) { // Catch broader exceptions during setup or connection
            LOGGER.log(Level.SEVERE, "Simulation failed to start or encountered a critical error.", e);
            // Ensure cleanup if publisher was partially initialized
            if (publisher != null) {
                publisher.close();
            }
            System.exit(1); // Exit with error code
        }
        // Note: If startPublishing completed normally (e.g., was stopped internally),
        // the application would exit here. The shutdown hook still runs on normal exit too.
        LOGGER.info("Simulation main thread finished.");
    }

    public static void main(String[] args) {
        SimulationApp app = new SimulationApp();
        app.start();
    }
}