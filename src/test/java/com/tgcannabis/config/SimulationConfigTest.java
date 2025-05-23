package com.tgcannabis.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

class SimulationConfigTest {
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

    private void setupDotenvMock(String sensorTypesEnv, String locationsEnv, String failProbEnv,
                                 Map<String, String> customRanges) {
        Dotenv mockDotenv = mock(Dotenv.class);
        DotenvBuilder mockBuilder = mock(DotenvBuilder.class);

        dotenvStatic.when(Dotenv::configure).thenReturn(mockBuilder);
        when(mockBuilder.ignoreIfMissing()).thenReturn(mockBuilder);
        when(mockBuilder.load()).thenReturn(mockDotenv);

        // Mock dotenv.get(key) calls. If the key exists, it will return the value.
        // If not, it will return null (which is the default mock behavior if not stubbed).
        when(mockDotenv.get("SIM_SENSOR_TYPES")).thenReturn(sensorTypesEnv);
        when(mockDotenv.get("SIM_LOCATIONS")).thenReturn(locationsEnv);
        when(mockDotenv.get("SIM_FAIL_PROB")).thenReturn(failProbEnv);


        // Mock dotenv.get(key, defaultValue) calls.
        // This is crucial for handling default values correctly.
        // Mock it to return what it WOULD return if the first arg .get() was called and returned null.
        when(mockDotenv.get("SIM_SENSOR_TYPES", SimulationConfig.DEFAULT_SENSORS))
                .thenReturn(sensorTypesEnv != null ? sensorTypesEnv : SimulationConfig.DEFAULT_SENSORS);
        when(mockDotenv.get("SIM_LOCATIONS", SimulationConfig.DEFAULT_LOCATIONS))
                .thenReturn(locationsEnv != null ? locationsEnv : SimulationConfig.DEFAULT_LOCATIONS);
        when(mockDotenv.get("SIM_FAIL_PROB", "0.1"))
                .thenReturn(failProbEnv != null ? failProbEnv : "0.1"); // Explicitly return the default string

        // Mock custom ranges
        if (customRanges != null) {
            customRanges.forEach((key, value) -> when(mockDotenv.get(key)).thenReturn(value));
        }

        // Ensure that any un-mocked .get() for range keys returns null by default
        // This is important for tests where a range is intentionally missing.
        when(mockDotenv.get(argThat(arg -> arg.startsWith("SIM_") && arg.endsWith("_RANGE") && (customRanges == null || !customRanges.containsKey(arg)))))
                .thenReturn(null);
    }

    @Test
    void shouldLoadAllConfigsFromEnvWithCustomValues() {
        // Arrange
        Map<String, String> customRanges = Map.of(
                "SIM_TEMPERATURE_RANGE", "10.0:30.0",
                "SIM_HUMIDITY_RANGE", "40.0:60.0",
                "SIM_CO2_RANGE", "400.0:800.0"
        );
        setupDotenvMock(
                "temperature,humidity,co2",
                "room1,room2",
                "0.05",
                customRanges
        );

        // Act
        SimulationConfig config = SimulationConfig.loadFromEnv();

        // Assert
        assertNotNull(config);
        assertEquals(Arrays.asList("temperature", "humidity", "co2"), config.getSensorTypes());
        assertEquals(Arrays.asList("room1", "room2"), config.getLocations());
        assertEquals(0.05, config.getFailProbability(), 0.001);

        // Verify value ranges
        assertNotNull(config.getValueRanges());
        assertEquals(3, config.getValueRanges().size());

        SimulationConfig.ValueRange tempRange = config.getValueRanges().get("temperature");
        assertNotNull(tempRange);
        assertEquals(10.0, tempRange.getMin(), 0.001);
        assertEquals(30.0, tempRange.getMax(), 0.001);

        SimulationConfig.ValueRange humRange = config.getValueRanges().get("humidity");
        assertNotNull(humRange);
        assertEquals(40.0, humRange.getMin(), 0.001);
        assertEquals(60.0, humRange.getMax(), 0.001);

        SimulationConfig.ValueRange co2Range = config.getValueRanges().get("co2");
        assertNotNull(co2Range);
        assertEquals(400.0, co2Range.getMin(), 0.001);
        assertEquals(800.0, co2Range.getMax(), 0.001);
    }

    @Test
    void shouldLoadWithDefaultSensorTypesAndLocations() {
        // Arrange
        // No custom SIM_SENSOR_TYPES or SIM_LOCATIONS, so defaults should be used
        Map<String, String> defaultRanges = Map.of(
                "SIM_TEMPERATURE_RANGE", "0:50",
                "SIM_HUMIDITY_RANGE", "0:100",
                "SIM_CO2_RANGE", "300:1000"
        );
        setupDotenvMock(
                "temperature,humidity,co2", // Explicitly set to default to ensure ranges are picked
                "Cundinamarca,Antioquia,Valle del Cauca", // Explicitly set to default
                "0.1",
                defaultRanges
        );

        // Act
        SimulationConfig config = SimulationConfig.loadFromEnv();

        // Assert
        assertNotNull(config);
        assertEquals(Arrays.asList("temperature", "humidity", "co2"), config.getSensorTypes());
        assertEquals(Arrays.asList("Cundinamarca", "Antioquia", "Valle del Cauca"), config.getLocations());
        assertEquals(0.1, config.getFailProbability(), 0.001);

        // Verify ranges are loaded for default types
        assertNotNull(config.getValueRanges().get("temperature"));
        assertNotNull(config.getValueRanges().get("humidity"));
        assertNotNull(config.getValueRanges().get("co2"));
    }

    @Test
    void shouldLoadWithDefaultFailProbability() {
        // Arrange
        Map<String, String> ranges = Map.of(
                "SIM_TEMPERATURE_RANGE", "0:50" // Need at least one range for the default sensor type
        );
        setupDotenvMock(
                "temperature",
                "location1",
                null, // Fail probability is null, so default "0.1" should be used
                ranges
        );

        // Act
        SimulationConfig config = SimulationConfig.loadFromEnv();

        // Assert
        assertNotNull(config);
        assertEquals(0.1, config.getFailProbability(), 0.001);
    }

    @Test
    void shouldThrowIfRangeMissingForASensorType() {
        // Arrange
        // We define two sensor types, but only provide range for one
        setupDotenvMock(
                "temperature,pressure", // Two sensor types
                "loc",
                "0.1",
                Map.of("SIM_TEMPERATURE_RANGE", "0:50") // Only temperature range provided
        );

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, SimulationConfig::loadFromEnv);
        assertEquals("Missing or malformed range for: pressure", ex.getMessage());
    }

    @Test
    void shouldThrowIfRangeMalformedForASensorType() {
        // Arrange
        setupDotenvMock(
                "temperature",
                "loc",
                "0.1",
                Map.of("SIM_TEMPERATURE_RANGE", "20-30") // Malformed range (needs colon)
        );

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, SimulationConfig::loadFromEnv);
        assertEquals("Missing or malformed range for: temperature", ex.getMessage());
    }

    @Test
    void shouldThrowIfRangeContainsNonDoubleValues() {
        // Arrange
        setupDotenvMock(
                "temperature",
                "loc",
                "0.1",
                Map.of("SIM_TEMPERATURE_RANGE", "10.5:notadouble") // Malformed range (non-double)
        );

        // Act & Assert
        NumberFormatException ex = assertThrows(NumberFormatException.class, SimulationConfig::loadFromEnv);
        // The exact message might vary by JVM, but it will be a NumberFormatException
        assertNotNull(ex.getMessage());
    }

    @Test
    void shouldThrowIfFailProbabilityMalformed() {
        // Arrange
        Map<String, String> ranges = Map.of(
                "SIM_TEMPERATURE_RANGE", "0:50"
        );
        setupDotenvMock(
                "temperature",
                "loc",
                "notadouble", // Malformed fail probability
                ranges
        );

        // Act & Assert
        NumberFormatException ex = assertThrows(NumberFormatException.class, SimulationConfig::loadFromEnv);
        assertNotNull(ex.getMessage());
    }

    // --- Tests for Constructor ---

    @Test
    void constructorShouldSetFieldsCorrectly() {
        // Arrange
        List<String> types = Arrays.asList("temp", "hum");
        Map<String, SimulationConfig.ValueRange> ranges = Map.of(
                "temp", new SimulationConfig.ValueRange(0, 100)
        );
        List<String> locs = Arrays.asList("loc1", "loc2");
        double prob = 0.02;

        // Act
        SimulationConfig config = new SimulationConfig(types, ranges, locs, prob);

        // Assert
        assertEquals(types, config.getSensorTypes());
        assertEquals(ranges, config.getValueRanges());
        assertEquals(locs, config.getLocations());
        assertEquals(prob, config.getFailProbability(), 0.001);
    }

    @Test
    void constructorShouldThrowIfSensorTypesIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new SimulationConfig(null, Collections.emptyMap(), Collections.emptyList(), 0.1));
        assertEquals("Error loading available sensor types", ex.getMessage());
    }

    @Test
    void constructorShouldThrowIfValueRangesIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new SimulationConfig(Collections.emptyList(), null, Collections.emptyList(), 0.1));
        assertEquals("Error loading sensors value ranges", ex.getMessage());
    }

    @Test
    void constructorShouldThrowIfLocationsIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new SimulationConfig(Collections.emptyList(), Collections.emptyMap(), null, 0.1));
        assertEquals("Error loading available locations", ex.getMessage());
    }

    // --- Tests for ValueRange nested class ---

    @Test
    void valueRangeConstructorShouldSetMinAndMax() {
        SimulationConfig.ValueRange range = new SimulationConfig.ValueRange(1.0, 5.0);
        assertEquals(1.0, range.getMin(), 0.001);
        assertEquals(5.0, range.getMax(), 0.001);
    }

    @Test
    void valueRangeToStringShouldReturnCorrectFormat() {
        SimulationConfig.ValueRange range = new SimulationConfig.ValueRange(10.5, 20.2);
        assertEquals("[10.5 - 20.2]", range.toString());
    }

    // --- Test for toString() of SimulationConfig ---

    @Test
    void simulationConfigToStringShouldReturnCorrectFormat() {
        List<String> types = Arrays.asList("t", "h");
        Map<String, SimulationConfig.ValueRange> ranges = Map.of("t", new SimulationConfig.ValueRange(0, 100));
        List<String> locs = Arrays.asList("a", "b");
        double prob = 0.5;

        SimulationConfig config = new SimulationConfig(types, ranges, locs, prob);
        String expected = "SimulationConfig{" +
                "sensorTypes=[t, h]" +
                ", valueRanges={t=[0.0 - 100.0]}" +
                ", locations=[a, b]" +
                ", failProbability=" + prob +
                '}';
        assertEquals(expected, config.toString());
    }
}
