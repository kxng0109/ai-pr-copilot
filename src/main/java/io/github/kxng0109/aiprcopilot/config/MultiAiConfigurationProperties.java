package io.github.kxng0109.aiprcopilot.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for managing multiple AI providers in PR Copilot.
 *
 * <p>Defines settings for primary and fallback providers, token usage limits,
 * request timeouts, and behavior for automatic fallback.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "prcopilot.ai")
public class MultiAiConfigurationProperties {

    @NotNull
    private AiProvider provider = AiProvider.OPENAI;

    private AiProvider fallbackProvider;

    private boolean autoFallback = false;

    @Min(value = 0, message = "Temperature must be between 0.0 and 1.0 inclusive")
    @Max(value = 1, message = "Temperature must be between 0.0 and 1.0 inclusive")
    private double temperature;

    @Min(value = 1, message = "Max tokens can not be less than 1")
    private int maxTokens;

    @Min(value = 1000, message = "Request timeout must be at least 1000ms")
    private long timeoutMillis;

    @Min(value = 1, message = "SaaS bulkhead concurrency must be at least 1")
    private int bulkheadSaasMaxConcurrent = 15;

    @Min(value = 1, message = "Ollama bulkhead concurrency must be at least 1")
    private int bulkheadOllamaMaxConcurrent = 3;

    @Min(value = 0, message = "Bulkhead max wait must not be negative")
    private long bulkheadMaxWaitMillis = 0;

    @Min(value = 1, message = "Rate limiter period limit must be at least 1")
    private int ratelimitForPeriod = 30;

    private String ratelimitRefreshPeriod = "1m";

    @Min(value = 0, message = "Rate limiter timeout must not be negative")
    private long ratelimitTimeoutMillis = 0;
}
