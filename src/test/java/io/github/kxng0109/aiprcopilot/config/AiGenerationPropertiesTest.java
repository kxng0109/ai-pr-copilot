package io.github.kxng0109.aiprcopilot.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class AiGenerationPropertiesTest {

    @Autowired
    private AiGenerationProperties aiGenerationProperties;

    @Test
    void shouldBindDefaultsFromApplicationYaml(){
        assertEquals(0.1, aiGenerationProperties.getTemperature());
        assertEquals(1024, aiGenerationProperties.getMaxTokens());
        assertEquals(30000L, aiGenerationProperties.getTimeoutMillis());
    }
}
