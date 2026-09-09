package io.github.kxng0109.aiprcopilot.config;

import io.github.kxng0109.aiprcopilot.error.CustomApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class AppStartupCheck {

    private final MultiAiConfigurationProperties multiAiConfigurationProperties;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${spring.ai.anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${spring.ai.google.genai.project-id:}")
    private String googleGenAiProjectId;

    @Value("${spring.ai.google.genai.location:}")
    private String googleGenAiLocation;

    @Value("${spring.ai.google.genai.api-key:}")
    private String googleGenAiApiKey;

    @Value("${spring.ai.ollama.base-url:}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.model:}")
    private String ollamaChatModel;

    @Value("${app.skip-startup-check:false}")
    private boolean skipStartupCheck;

    /**
     * Validates the AI provider configuration during application startup.
     * <p>
     * Ensures the primary and fallback AI providers (if auto-fallback is enabled) are
     * correctly configured with the required properties such as API keys or project
     * identifiers. Throws an exception if the configuration is invalid.
     * <p>
     * Logs warnings or informational messages for fallback provider settings or
     * potential misconfigurations, such as using the same provider for both primary
     * and fallback.
     *
     * @throws RuntimeException if auto-fallback is enabled but the fallback provider
     *                          is not configured or improperly set
     */
    @EventListener(ApplicationStartedEvent.class)
    public void validateConfiguration() {
        if(skipStartupCheck){
            log.debug("Startup checks skipped");
            return;
        }

        AiProvider primaryProvider = multiAiConfigurationProperties.getProvider();

        validateProvider(primaryProvider);

        if (multiAiConfigurationProperties.isAutoFallback()) {
            AiProvider fallbackProvider = ProviderSupport.requireFallbackProvider(
                    multiAiConfigurationProperties.getFallbackProvider());
            if (fallbackProvider == primaryProvider) {
                log.warn("Fallback provider is the same as your primary provider, is that intentional?");
            }

            log.info("Fallback provider: {}", fallbackProvider);

            validateProvider(fallbackProvider);
        } else{
            log.info("Auto-fallback is not set. No fallback provider is configured.");
        }
    }

    /**
     * Validates the specified {@code AiProvider} to ensure it is correctly configured.
     * <p>
     * Checks the availability of required configuration parameters such as API keys
     * or project identifiers for the given provider and throws an exception if invalid.
     *
     * @param provider the {@code AiProvider} to validate; must not be {@code null}
     * @throws CustomApiException if the required configuration for the given provider is unavailable,
     *                            with an appropriate HTTP status and error message
     */
    private void validateProvider(AiProvider provider) {
        //Maybe also ping the base url's or is that too much????

        switch (provider) {
            case OPENAI -> {
                if (isMissing(openAiApiKey)) {
                    String errorMessage = "Configured failed for provider OPENAI. OPENAI api key is not configured. Set OPENAI_API_KEY. \nCheck .env.example for more info";

                    log.error(errorMessage);
                    throw new CustomApiException(errorMessage,
                                                 HttpStatus.INTERNAL_SERVER_ERROR
                    );
                }

                log.info("Provider '{}' successfully configured.", provider);
            }

            case ANTHROPIC -> {
                if (isMissing(anthropicApiKey)) {
                    String errorMessage = "Configured failed for provider ANTHROPIC. ANTHROPIC api key is not configured. Set ANTHROPIC_API_KEY. \nCheck .env.example for more info";

                    log.error(errorMessage);
                    throw new CustomApiException(errorMessage,
                                                 HttpStatus.INTERNAL_SERVER_ERROR
                    );
                }

                log.info("Provider '{}' successfully configured.", provider);
            }

            case GEMINI -> {
                // Vertex mode (production): project-id + location + ADC.
                // API-key mode is prototyping only and rejected for production diffs.
                if (!isMissing(googleGenAiProjectId) && !isMissing(googleGenAiLocation)) {
                    log.info("Provider '{}' successfully configured (Vertex mode).", provider);
                } else if (!isMissing(googleGenAiApiKey)) {
                    log.warn("Provider '{}' uses Developer API key mode (prototyping only, no residency/ZDR guarantees).", provider);
                } else {
                    String errorMessage = "Configured failed for provider Gemini. Set GOOGLE_GENAI_PROJECT_ID and GOOGLE_GENAI_LOCATION (Vertex mode), or GOOGLE_GENAI_API_KEY for prototyping. \nCheck .env.example for more info";

                    log.error(errorMessage);
                    throw new CustomApiException(errorMessage,
                                                 HttpStatus.INTERNAL_SERVER_ERROR
                    );
                }
            }

            case OLLAMA -> {
                if(isMissing(ollamaBaseUrl) || isMissing(ollamaChatModel)) {
                    String errorMessage = "Configured failed for provider OLLAMA. Make sure OLLAMA_BASE_URL and OLLAMA_MODEL are set. \nCheck .env.example for more info";

                    log.error(errorMessage);
                    throw new CustomApiException(errorMessage,
                                                 HttpStatus.INTERNAL_SERVER_ERROR
                    );
                }
            }
        }
    }

    /**
     * Checks whether the specified string is missing based on a set of predefined conditions.
     * <p>
     * A string is considered unavailable if it is {@code null}, blank, empty after trimming, or matches the value
     * {@code "default-value"}.
     *
     * @param stuff the string to check; may be {@code null}
     * @return {@code true} if the string is unavailable, {@code false} otherwise
     */
    private boolean isMissing(String stuff) {
        return stuff == null || stuff.isBlank() || stuff.equals("default-value");
    }
}
