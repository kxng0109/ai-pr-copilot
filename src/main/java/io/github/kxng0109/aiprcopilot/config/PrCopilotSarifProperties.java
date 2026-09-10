package io.github.kxng0109.aiprcopilot.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * SARIF output settings.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "prcopilot.sarif")
public class PrCopilotSarifProperties {

	/**
	 * Target consumer: {@code GITHUB} (Code Scanning) or {@code SONAR} (bound case-insensitively).
	 */
	@NotNull(message = "SARIF consumer must not be null")
	private SarifConsumer consumer = SarifConsumer.GITHUB;

	/**
	 * SARIF run category ({@code runAutomationDetails.id}).
	 */
	@NotBlank(message = "SARIF category can not be blank")
	private String category = "ai-pr-copilot";

	/**
	 * Path to the repo-local false-positive suppress file. Empty disables suppression.
	 */
	private String suppressFile = ".ai-review-ignore.yml";
}
