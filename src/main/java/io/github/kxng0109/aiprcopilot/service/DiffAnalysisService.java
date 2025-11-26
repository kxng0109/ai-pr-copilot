package io.github.kxng0109.aiprcopilot.service;

import io.github.kxng0109.aiprcopilot.config.MultiAiConfigurationProperties;
import io.github.kxng0109.aiprcopilot.config.PrCopilotAnalysisProperties;
import io.github.kxng0109.aiprcopilot.config.PrCopilotLoggingProperties;
import io.github.kxng0109.aiprcopilot.config.api.dto.AnalyzeDiffRequest;
import io.github.kxng0109.aiprcopilot.config.api.dto.AnalyzeDiffResponse;
import io.github.kxng0109.aiprcopilot.error.DiffTooLargeException;
import io.github.kxng0109.aiprcopilot.error.ModelOutputParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiffAnalysisService {

    private final PrCopilotAnalysisProperties analysisProperties;
    private final ChatClient primaryChatClient;
    private final ChatOptions primaryChatOptions;
    private final PrCopilotLoggingProperties loggingProperties;
    private final MultiAiConfigurationProperties multiAiConfigurationProperties;
    private final PromptBuilderService promptBuilderService;
    private final AiChatService aiChatService;
    private final DiffResponseMapperService diffResponseMapperService;

    @Qualifier("fallbackChatClient")
    @Nullable
    private final ChatClient fallbackChatClient;

    @Qualifier("fallbackChatOptions")
    @Nullable
    private final ChatOptions fallbackChatOptions;

    /**
     * Analyzes a code diff and generates a structured response with analysis details.
     *
     * @param request the {@code AnalyzeDiffRequest} containing the diff content and associated parameters, must not be {@code null}
     * @return the {@code AnalyzeDiffResponse} containing the analysis result, never {@code null}
     * @throws DiffTooLargeException     if the diff exceeds the maximum allowed size
     * @throws IllegalArgumentException  if the request is {@code null} or contains invalid parameters
     * @throws ModelOutputParseException if the AI model output could not be parsed
     * @throws RuntimeException          if an internal error occurs and both primary and fallback providers fail
     */
    public AnalyzeDiffResponse analyzeDiff(AnalyzeDiffRequest request) {
        String diff = request.diff();
        log.debug("Diff received: {}", diff);
        int maxDiffChars = analysisProperties.getMaxDiffChars();
        log.debug("Max diff chars set to: {}", maxDiffChars);
        if (diff.length() > maxDiffChars) {
            throw new DiffTooLargeException(
                    String.format("Diff exceeded maximum allowed size of %d characters",
                                  maxDiffChars
                    )
            );
        }

        String language = useDefaultIfBlank(request.language(), analysisProperties.getDefaultLanguage());
        String style = useDefaultIfBlank(request.style(), analysisProperties.getDefaultStyle());
        Integer maxSummaryLength = request.maxSummaryLength();

        Prompt prompt = promptBuilderService.buildDiffAnalysisPrompt(
                language,
                style,
                diff,
                maxSummaryLength,
                request.requestId()
        );

        if (loggingProperties.isLogPrompts()) log.info(prompt.toString());

        try {
            log.debug("Attempting to use primary provider: {}", multiAiConfigurationProperties.getProvider());

            return callAiAndBuildResponse(
                    request,
                    diff,
                    prompt,
                    primaryChatClient,
                    primaryChatOptions,
                    multiAiConfigurationProperties.getProvider().getValue()
            );
        } catch (ModelOutputParseException e) {
            log.warn("Model output could not be parsed for requestId '{}': {}", request.requestId(), e.getMessage());
            throw e;
        } catch (Exception primaryException) {
            log.error("Unexpected error in diff analysis for requestId '{}' while using primary provider: {}. {}",
                      request.requestId(), multiAiConfigurationProperties.getProvider(), primaryException.getMessage(),
                      primaryException
            );

            if (multiAiConfigurationProperties.isAutoFallback() && fallbackChatClient != null) {
                try {
                    log.debug("Attempting to use fallback chat client: {}",
                              multiAiConfigurationProperties.getFallbackProvider()
                    );

                    return callAiAndBuildResponse(
                            request,
                            diff,
                            prompt,
                            fallbackChatClient,
                            fallbackChatOptions,
                            multiAiConfigurationProperties.getFallbackProvider().getValue()
                    );
                } catch (Exception fallBackException) {
                    log.error(
                            "Unexpected error in diff analysis for requestId '{}' while using fallback provider: {}",
                            request.requestId(),
                            multiAiConfigurationProperties.getFallbackProvider(),
                            fallBackException
                    );

                    throw new RuntimeException(
                            String.format(
                                    "Could not process diff analysis due to internal error. Primary: %s. Fallback: %s",
                                    primaryException.getMessage(),
                                    fallBackException.getMessage()
                            ),
                            fallBackException
                    );
                }
            }

            log.debug("No fallback available. Auto-fallback is disabled or no fallback client is configured.");
            throw new RuntimeException("Could not process diff analysis due to internal error.", primaryException);
        }
    }

    /**
     * Invokes an AI model to analyze a code diff and constructs a response containing the analysis results.
     *
     * @param request             the request containing metadata and context for the analysis, must not be {@code null}
     * @param diff                the code diff to be analyzed, must not be {@code null} or empty
     * @param prompt              the AI model prompt used for guiding the analysis, must not be {@code null} or blank
     * @param chatClient  the fallback chat client to use for the AI call, must not be {@code null}
     * @param chatOptions the options to configure the fallback chat client, must not be {@code null}
     * @return the response containing the AI analysis results, never {@code null}
     * @throws IllegalArgumentException if any required parameter is {@code null} or invalid
     */
    private AnalyzeDiffResponse callAiAndBuildResponse(
            AnalyzeDiffRequest request,
            String diff,
            Prompt prompt,
            ChatClient chatClient,
            ChatOptions chatOptions,
            String providerName
    ) {
        long start = System.currentTimeMillis();
        ChatResponse aiResponse = aiChatService.callAiModel(
                prompt,
                chatClient,
                chatOptions
        );
        long end = System.currentTimeMillis();
        long latencyMs = end - start;

        return diffResponseMapperService.mapToAnalyzeDiffResponse(
                aiResponse,
                latencyMs,
                diff,
                request.requestId(),
                providerName
        );
    }

    /**
     * Returns {@code givenValue} if it is not {@code null}, blank, or empty; otherwise, returns {@code defaultValue}.
     *
     * @param givenValue   the value to use if it is not blank or empty; may be {@code null}
     * @param defaultValue the default value to return if {@code givenValue} is blank, empty, or {@code null}
     * @return {@code givenValue} if non-blank; otherwise, {@code defaultValue}
     */
    private String useDefaultIfBlank(String givenValue, String defaultValue) {
        return (givenValue == null || givenValue.trim().isEmpty() || givenValue.isBlank()) ? defaultValue : givenValue;
    }
}
