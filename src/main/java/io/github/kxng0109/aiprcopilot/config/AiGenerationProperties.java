package io.github.kxng0109.aiprcopilot.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ai")
public class AiGenerationProperties {

    @Min(0)
    @Max(1)
    private double temperature;

    @Min(1)
    private int maxTokens;

    @Min(1000)
    private long timeoutMillis;
}
