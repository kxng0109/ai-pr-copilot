package io.github.kxng0109.aiprcopilot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "prcopilot.logging")
public class PrcopilotLoggingProperties {

    private boolean logPrompts;

    private boolean logResponses;
}
