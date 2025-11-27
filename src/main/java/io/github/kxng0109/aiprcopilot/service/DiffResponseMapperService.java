package io.github.kxng0109.aiprcopilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kxng0109.aiprcopilot.config.PrCopilotAnalysisProperties;
import io.github.kxng0109.aiprcopilot.config.PrCopilotLoggingProperties;
import io.github.kxng0109.aiprcopilot.api.dto.AiCallMetadata;
import io.github.kxng0109.aiprcopilot.api.dto.AnalyzeDiffResponse;
import io.github.kxng0109.aiprcopilot.api.dto.ModelAnalyzeDiffResult;
import io.github.kxng0109.aiprcopilot.error.ModelOutputParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for analyzing and building structured responses based on code diffs
 * and AI-generated outputs.
 * <p>
 * Provides functionality to map AI responses to domain-specific objects,
 * sanitize model outputs, and extract metadata or file information from
 * diffs in unified diff format.
 */
@Service
@Slf4j
@RequiredArgsConstructor
class DiffResponseMapperService {

    private static final Pattern DIFF_GIT_LINE_PATTERN = Pattern.compile("^diff --git a/(.+?) b/(.+?)$");

    private final ObjectMapper objectMapper;
    private final PrCopilotLoggingProperties loggingProperties;
    private final PrCopilotAnalysisProperties analysisProperties;

    /**
     * Maps the AI model's raw {@code ChatResponse} output and related metadata to an {@code AnalyzeDiffResponse}.
     *
     * @param response     the AI model's raw response, must not be {@code null}
     * @param responseTime the time taken by the model to respond, in milliseconds, must be non-negative
     * @param diff         the code diff content used for analysis, must not be {@code null} or empty
     * @param requestId    the unique request identifier, must not be {@code null} or empty
     * @param provider     the name of the AI provider, must not be {@code null} or empty
     * @return a fully populated {@code AnalyzeDiffResponse}, never {@code null}
     * @throws ModelOutputParseException if the AI model's output is invalid or missing required fields
     * @throws RuntimeException          if an unexpected error occurs during mapping
     */
    public AnalyzeDiffResponse mapToAnalyzeDiffResponse(
            ChatResponse response,
            long responseTime,
            String diff,
            String requestId,
            String provider
    ) {
        String modelOutput = extractModelOutputText(response);
        log.debug("AI model raw output: {}", modelOutput);
        String cleanedModelOutput = sanitizeModelOutput(modelOutput);
        log.debug("AI model cleaned output: {}", cleanedModelOutput);

        try {
            ModelAnalyzeDiffResult aiResult = objectMapper.readValue(cleanedModelOutput, ModelAnalyzeDiffResult.class);
            log.debug("AI model analysis result: {}", aiResult);

            if (aiResult == null) {
                throw new ModelOutputParseException("Parsed model output is null. Expected non-null, valid JSON DTO.");
            }

            if (aiResult.title() == null || aiResult.summary() == null || aiResult.details() == null
                    || aiResult.risks() == null || aiResult.suggestedTests() == null) {
                throw new ModelOutputParseException(
                        "Parsed model output is missing required fields. Output: " + cleanedModelOutput);
            }

            if (loggingProperties.isLogResponses()) log.info(aiResult.toString());

            String model = response.getMetadata().getModel();
            Integer tokensUsed = (response.getMetadata().getUsage() != null && response.getMetadata().getUsage().getTotalTokens() != null)
                    ? response.getMetadata().getUsage().getTotalTokens()
                    : null;

            List<String> touchedFiles = (aiResult.touchedFiles() == null || aiResult.touchedFiles().isEmpty())
                    ? extractTouchedFilesFromDiff(diff)
                    : aiResult.touchedFiles();

            AiCallMetadata metadata = AiCallMetadata.builder()
                                                    .modelName(model)
                                                    .provider(provider)
                                                    .tokensUsed(tokensUsed)
                                                    .modelLatencyMs(responseTime)
                                                    .build();

            return AnalyzeDiffResponse.builder()
                                      .title(aiResult.title())
                                      .risks(aiResult.risks())
                                      .summary(aiResult.summary())
                                      .suggestedTests(aiResult.suggestedTests())
                                      .details(aiResult.details())
                                      .analysisNotes(aiResult.analysisNotes())
                                      .touchedFiles(touchedFiles)
                                      .metadata(metadata)
                                      .rawModelOutput(
                                              analysisProperties.isIncludeRawModelOutput()
                                                      ? modelOutput
                                                      : null
                                      )
                                      .requestId(requestId)
                                      .build();

        } catch (JsonProcessingException e) {
            log.warn("JSON parsing failed for model output: {}", e.getOriginalMessage());
            throw new ModelOutputParseException("Model returned invalid JSON output. " +
                                                        "Error details: " + e.getOriginalMessage());
        } catch (ModelOutputParseException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException("Unexpected error mapping AI output", e);
        }
    }

    private String extractModelOutputText(ChatResponse response) {
        String aiRawResponse = null;

        try {
            aiRawResponse = response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("Could not extract text from ChatResponse result/output.", e);
            throw new ModelOutputParseException("Could not extract text from AI model response.");
        }

        if (aiRawResponse == null || aiRawResponse.isBlank()) {
            throw new ModelOutputParseException("AI model returned empty output; cannot parse.");
        }

        return aiRawResponse;
    }

    /**
     * Sanitizes the output of a model by removing extra formatting or wrapping elements.
     *
     * @param value the raw output string, may be {@code null} or blank
     * @return the sanitized string, never {@code null}. Returns an empty string if the input was {@code null} or blank.
     */
    private String sanitizeModelOutput(String value) {
        if (value == null || value.isBlank()) return "";
        value = value.replaceFirst("(?s)^```(?:json)?\\s*\\n?", "");
        value = value.replaceFirst("(?s)\\n?```$", "");
        value = value.trim();

        if (!value.startsWith("{")) {
            int start = value.indexOf("{");
            int end = value.lastIndexOf("}");
            if (start != -1 && end != -1 && end > start) {
                value = value.substring(start, end + 1);
            }
        }

        return value;
    }

    /**
     * Extracts the set of file paths touched by a diff.
     *
     * @param diff the diff content in unified diff format, may be {@code null} or blank
     * @return an unmodifiable list of unique file paths, never {@code null}
     */
    private List<String> extractTouchedFilesFromDiff(String diff) {
        if (diff == null || diff.trim().isEmpty() || diff.isBlank()) return List.of();

        LinkedHashSet<String> files = new LinkedHashSet<>();

        String[] lines = diff.split("\\R");
        for (String line : lines) {
            Matcher matcher = DIFF_GIT_LINE_PATTERN.matcher(line);
            if (matcher.matches()) {
                String newPath = matcher.group(2);
                files.add(newPath);
            }
        }

        return List.copyOf(files);
    }
}
