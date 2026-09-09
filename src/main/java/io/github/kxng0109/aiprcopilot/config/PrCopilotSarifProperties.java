package io.github.kxng0109.aiprcopilot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SARIF output settings.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "prcopilot.sarif")
public class PrCopilotSarifProperties {

    /**
     * Target consumer: {@code github} (Code Scanning) or {@code sonar}.
     */
    private String consumer = "github";

    /**
     * SARIF run category ({@code runAutomationDetails.id}).
     */
    private String category = "ai-pr-copilot";

    /**
     * Path to the repo-local false-positive suppress file. Empty disables suppression.
     */
    private String suppressFile = ".ai-review-ignore.yml";
}
