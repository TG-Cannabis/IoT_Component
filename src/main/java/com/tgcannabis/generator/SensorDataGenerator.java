package com.tgcannabis.generator;

import com.tgcannabis.iot_component.model.SensorData;
import com.tgcannabis.config.SimulationConfig;
import lombok.Getter;

import java.util.List;
import java.util.Random;

/**
 * Generates simulated SensorData objects.
 * Produces random readings using given parameters for sensor types, locations and value ranges from SimulationConfig
 */
public class SensorDataGenerator {
    private static final int MAX_SENSOR_ID = 3; // Generates sensor_1, sensor_2, sensor_3

    private final Random random;

    @Getter
    private final SimulationConfig config;

    /**
     * Constructs a SensorDataGenerator using simulation parameters loaded from env.
     *
     * @param config instance of SimulationConfig with simulation parameters.
     */
    public SensorDataGenerator(SimulationConfig config) {
        this.random = new Random();
        this.config = config;
    }

    /**
     * Generates a single simulated SensorData reading.
     * Randomly selects sensor type, location, and ID, then generates an appropriate value.
     *
     * @return A new SensorData object with simulated data and current timestamp.
     */
    public SensorData generate() {
        // Generate random elements
        String sensorType = getRandomElement(config.getSensorTypes());
        String location = getRandomElement(config.getLocations());
        String sensorId = "sensor_" + (random.nextInt(MAX_SENSOR_ID) + 1);

        // Decide whether to generate an out-of-range value
        boolean generateInvalid = random.nextDouble() < config.getFailProbability();

        SimulationConfig.ValueRange range = config.getValueRanges().get(sensorType);
        double value;

        if (generateInvalid) {
            // Generate an out-of-range value (either below min or above max)
            boolean below = random.nextBoolean();
            if (below) {
                value = range.getMin() - (random.nextDouble() * 10); // 0–10 below
            } else {
                value = range.getMax() + (random.nextDouble() * 10); // 0–10 above
            }
        } else {
            // Generate normal in-range value
            value = range.getMin() + (random.nextDouble() * (range.getMax() - range.getMin()));
        }        
        
        // Sensor measurement dat
        SensorData data = new SensorData();
        data.setSensorType(sensorType);
        data.setLocation(location);
        data.setSensorId(sensorId);
        data.setValue(value);
        data.setTimestamp(System.currentTimeMillis());

        return data;
    }

    /**
     * Helper method to get a random element from a list of any type.
     *
     * @param list The list to select from.
     * @return A randomly selected element from the list.
     */
    private <T> T getRandomElement(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }
}