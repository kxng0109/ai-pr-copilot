package io.github.kxng0109.aiprcopilot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Authentication mode for the service.
 *
 * <ul>
 *   <li>{@code prod} — OIDC resource server required (fail-closed, no default issuer).</li>
 *   <li>{@code selfhost} — single-user API key, no external IdP.</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "prcopilot.auth")
public class PrCopilotAuthProperties {

    /**
     * Auth mode: {@code prod} or {@code selfhost}.
     */
    private String mode = "selfhost";

    /**
     * Pre-shared API key for {@code selfhost} mode. Empty means API endpoints stay locked.
     */
    private String apiKey = "";

    /**
     * Header carrying the key (default {@code Authorization}, {@code Bearer <key>} accepted).
     */
    private String apiKeyHeader = "Authorization";
}
