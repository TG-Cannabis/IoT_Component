package com.tgcannabis.generator;

import com.tgcannabis.iot_component.model.SensorData;
import com.tgcannabis.config.SimulationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SensorDataGeneratorTest {
    private SensorDataGenerator inRangeGenerator;
    private SensorDataGenerator outOfRangeGenerator;

    @BeforeEach
    void setUp() {
        SimulationConfig.ValueRange tempRange = new SimulationConfig.ValueRange(15.0, 25.0);
        SimulationConfig configInRange = new SimulationConfig(
                List.of("temperature"),
                Map.of("temperature", tempRange),
                List.of("room1", "room2"),
                0.0 // Always in range
        );
        inRangeGenerator = new SensorDataGenerator(configInRange);

        SimulationConfig configOutOfRange = new SimulationConfig(
                List.of("temperature"),
                Map.of("temperature", tempRange),
                List.of("room1", "room2"),
                1.0 // Always out of range
        );
        outOfRangeGenerator = new SensorDataGenerator(configOutOfRange);
    }

    @Test
    void testGenerateInRangeSensorData() {
        SensorData data = inRangeGenerator.generate();
        assertEquals("temperature", data.getSensorType());
        assertTrue(data.getValue() >= 15.0 && data.getValue() <= 25.0);
    }

    // Repeated test to ensure that data can be off from both upper and lower limits
    @RepeatedTest(10)
    void testGenerateOutOfRangeSensorData() {
        SensorData data = outOfRangeGenerator.generate();
        assertTrue(data.getValue() < 15.0 || data.getValue() > 25.0, "Expected value out of range");
    }
}
