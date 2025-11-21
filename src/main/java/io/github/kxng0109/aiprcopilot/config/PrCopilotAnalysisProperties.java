package io.github.kxng0109.aiprcopilot.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "prcopilot.analysis")
public class PrCopilotAnalysisProperties {

    @Min(1)
    private int maxDiffChars;

    @NotBlank
    private String defaultLanguage;

    @NotBlank
    private String defaultStyle;

    private boolean includeRawModelOutput;
}
