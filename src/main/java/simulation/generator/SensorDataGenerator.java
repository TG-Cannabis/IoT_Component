package simulation.generator;

import model.SensorData;
import model.SensorInformation;
import java.util.Random;

/**
 * Generates simulated SensorData objects.
 * Produces random temperature or humidity readings from predefined locations and sensor IDs.
 */
public class SensorDataGenerator {

    private static final String[] SENSOR_TYPES = {"temperature", "humidity"};
    private static final String[] LOCATIONS = {"Office", "Warehouse", "Lab"};
    private static final int MAX_SENSOR_ID = 3; // Generates sensor_1, sensor_2, sensor_3

    // Sensor value ranges
    private static final double TEMP_BASE = 20.0;
    private static final double TEMP_RANGE = 10.0;
    private static final double HUMIDITY_BASE = 40.0;
    private static final double HUMIDITY_RANGE = 30.0;

    private final Random random;

    /**
     * Constructs a SensorDataGenerator using a default Random instance.
     */
    public SensorDataGenerator() {
        this.random = new Random();
    }

    /**
     * Constructs a SensorDataGenerator using the provided Random instance.
     * Useful for testing or controlling randomness.
     *
     * @param random The Random instance to use for generation. Cannot be null.
     */
    public SensorDataGenerator(Random random) {
        this.random = java.util.Objects.requireNonNull(random, "Random instance cannot be null");
    }

    /**
     * Generates a single simulated SensorData reading.
     * Randomly selects sensor type, location, and ID, then generates an appropriate value.
     *
     * @return A new SensorData object with simulated data and current timestamp.
     */
    public SensorData generate() {
        // Create sensor information
        SensorInformation sensorInfo = new SensorInformation();
        String sensorType = getRandomElement(SENSOR_TYPES);
        String location = getRandomElement(LOCATIONS);
        String sensorId = "sensor_" + (random.nextInt(MAX_SENSOR_ID) + 1);

        sensorInfo.setSensorType(sensorType);
        sensorInfo.setLocation(location);
        sensorInfo.setId(sensorId); // Assuming SensorInformation has setId method

        // Generate sensor value based on type
        double value;
        if ("temperature".equals(sensorType)) {
            value = TEMP_BASE + (random.nextDouble() * TEMP_RANGE); // Temperature
        } else { // humidity
            value = HUMIDITY_BASE + (random.nextDouble() * HUMIDITY_RANGE); // Humidity
        }

        // Create SensorData object
        SensorData data = new SensorData();
        data.setSensorName(sensorInfo); // Assuming SensorData has setSensorName(SensorInformation)
        data.setValue(value);
        data.setTimestamp(System.currentTimeMillis());

        return data;
    }

    /**
     * Helper method to get a random element from a String array.
     * @param array The array to select from.
     * @return A randomly selected element from the array.
     */
    private String getRandomElement(String[] array) {
        if (array == null || array.length == 0) {
            return null; // Or throw an exception
        }
        return array[random.nextInt(array.length)];
    }
}