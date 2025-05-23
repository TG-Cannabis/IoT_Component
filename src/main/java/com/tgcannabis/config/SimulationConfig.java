package com.tgcannabis.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.Getter;

import java.util.*;

/**
 * Holds configuration parameters for the sensor data generation.
 * Provides a convenient way to load settings, typically from environment variables.
 */
@Getter
public class SimulationConfig {
    @Getter
    public static class ValueRange {
        private final double min;
        private final double max;

        public ValueRange(double min, double max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public String toString() {
            return "[" + min + " - " + max + "]";
        }
    }

    /**
     * Default values in case sensor types or locations are not given
     */
    static final String DEFAULT_SENSORS = "temperature,humidity,co2";
    static final String DEFAULT_LOCATIONS = "Cundinamarca,Antioquia,Valle del Cauca";

    private final List<String> sensorTypes;
    private final Map<String, ValueRange> valueRanges;
    private final List<String> locations;
    private final double failProbability;

    /**
     * Loads configuration from environment variables or .env file (as fallback).
     * Expects "SIM_SENSOR_TYPES", "SIM_LOCATIONS", "SIM_FAIL_PROB", and "SIM_TYPE_RANGE" variables.
     *
     * @return A SimulationConfig instance loaded from environment variables.
     * @throws IllegalStateException if required environment variables are missing or empty.
     */
    public static SimulationConfig loadFromEnv() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String typesStr = dotenv.get("SIM_SENSOR_TYPES", DEFAULT_SENSORS);
        String locationsStr = dotenv.get("SIM_LOCATIONS", DEFAULT_LOCATIONS);

        List<String> types = Arrays.asList(typesStr.split(","));
        List<String> locations = Arrays.asList(locationsStr.split(","));

        Map<String, ValueRange> ranges = new HashMap<>();

        for (String type : types) {
            String envKey = "SIM_" + type.toUpperCase() + "_RANGE";
            String rangeStr = dotenv.get(envKey);

            if (rangeStr == null || !rangeStr.contains(":")) {
                throw new IllegalArgumentException("Missing or malformed range for: " + type);
            }

            String[] parts = rangeStr.split(":");
            double min = Double.parseDouble(parts[0]);
            double max = Double.parseDouble(parts[1]);

            ranges.put(type, new ValueRange(min, max));
        }

        double failProbability = Double.parseDouble(dotenv.get("SIM_FAIL_PROB", "0.1"));

        return new SimulationConfig(types, ranges, locations, failProbability);
    }

    /**
     * @param sensorTypes     List of available sensor types. e.g. Temperature, Humidity, etc.
     * @param valueRanges     Map of value ranges for each sensor type. e.g. Temperature: 25-35
     * @param locations       List of available locations to tag data. e.g. Greenhouse-1
     * @param failProbability probability of generating a value out of given range
     */
    public SimulationConfig(List<String> sensorTypes, Map<String, ValueRange> valueRanges, List<String> locations, double failProbability) {
        this.sensorTypes = Objects.requireNonNull(sensorTypes, "Error loading available sensor types");
        this.valueRanges = Objects.requireNonNull(valueRanges, "Error loading sensors value ranges");
        this.locations = Objects.requireNonNull(locations, "Error loading available locations");
        this.failProbability = failProbability;
    }

    @Override
    public String toString() {
        return "SimulationConfig{" +
                "sensorTypes=" + sensorTypes +
                ", valueRanges=" + valueRanges +
                ", locations=" + locations +
                ", failProbability=" + failProbability +
                '}';
    }
}
