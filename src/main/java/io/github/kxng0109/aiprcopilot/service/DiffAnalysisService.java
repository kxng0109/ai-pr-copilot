package io.github.kxng0109.aiprcopilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kxng0109.aiprcopilot.config.PrCopilotAnalysisProperties;
import io.github.kxng0109.aiprcopilot.config.PrcopilotLoggingProperties;
import io.github.kxng0109.aiprcopilot.config.api.dto.AiCallMetadata;
import io.github.kxng0109.aiprcopilot.config.api.dto.AnalyzeDiffRequest;
import io.github.kxng0109.aiprcopilot.config.api.dto.AnalyzeDiffResponse;
import io.github.kxng0109.aiprcopilot.config.api.dto.ModelAnalyzeDiffResult;
import io.github.kxng0109.aiprcopilot.error.DiffTooLargeException;
import io.github.kxng0109.aiprcopilot.error.ModelOutputParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiffAnalysisService {

    private static final Pattern DIFF_GIT_LINE_PATTERN = Pattern.compile("^diff --git a/(.+?) b/(.+?)$");
    private final String SYSTEM_PROMPT = """
            You are a Principal Code Auditor and Security Analyst known for strict, pessimistic code reviews.
            Your goal is to find bugs, security vulnerabilities, and logic errors.
            You will receive a unified Git diff and some hints:
            - language: {language}
            - style: {style}
            
            ### INSTRUCTIONS
            1. **Analyze the Diff**: Read the diff line-by-line. Trace data flow for every variable.
            2. **Verify Context**: Do NOT assume functions or variables exist outside the scope of this diff. If a function is called but not defined in the diff, assume it is a "Risk" of unknown behavior.
            3. **Zero Tolerance**: If a line of code is ambiguous, mark it as a risk. Do not "guess" the intent.
            4. **Security First**: Look explicitly for injection attacks, memory leaks, race conditions, and unhandled exceptions.
            
            ### OUTPUT FORMAT
            You must output a single, valid JSON object.
            Do not send an invalid JSON for any reason.
            The JSON must strictly adhere to this schema:
            
            \\{
              "title": "Short, technical title of the change",
              "summary": "A neutral, objective summary of what changed (max 2 sentences).",
              "details": "A detailed technical breakdown of the implementation. Mention specific logic changes.",
              "risks": [
                "String array of specific risks. Be pedantic. If no risks, return an empty array."
              ],
              "suggestedTests": [
                "String array of specific unit or integration test cases required to verify this change."
              ],
              "touchedFiles": [
                "List of filenames modified in the diff."
              ],
              "analysisNotes": "Internal reasoning or caveats about the analysis. If none, set to null.",
            \\}
            
            ### CONSTRAINTS
            - **NO HALLUCINATIONS**: Do not reference libraries or imports not visible in the diff.
            - **NO EXPLANATORY TEXT**: Output ONLY the JSON object. No markdown fences (```json), no conversational filler.
            - **STRICT JSON**: Ensure all keys are present. Ensure the JSON is parsable.
            """;
    private final PrCopilotAnalysisProperties analysisProperties;
    private final ChatClient chatClient;
    private final ChatOptions chatOptions;
    private final ObjectMapper objectMapper;
    private final PrcopilotLoggingProperties loggingProperties;

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

        Prompt prompt = buildPrompt(language, style, diff, maxSummaryLength, request.requestId());

        if (loggingProperties.isLogPrompts()) log.info(prompt.toString());

        long start = System.currentTimeMillis();
        ChatResponse aiResponse = callAiModel(prompt);
        long end = System.currentTimeMillis();
        long latencyMs = end - start;

        try {
            return mapToAnalyzeDiffResponse(aiResponse, latencyMs, diff, request.requestId());
        } catch (ModelOutputParseException e) {
            log.warn("Model output could not be parsed for requestId '{}': {}", request.requestId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in diff analysis for requestId '{}'", request.requestId(), e);
            throw new RuntimeException("Could not process diff analysis due to internal error.", e);
        }
    }

    private String useDefaultIfBlank(String givenValue, String defaultValue) {
        return (givenValue == null || givenValue.trim().isEmpty() || givenValue.isBlank()) ? defaultValue : givenValue;
    }

    private Prompt buildPrompt(
            String language,
            String style,
            String diff,
            Integer maxSummaryLength,
            String requestId
    ) {
        SystemPromptTemplate promptTemplate = new SystemPromptTemplate(SYSTEM_PROMPT);
        Message systemMessage = promptTemplate.createMessage(
                Map.of("language", language, "style", style)
        );

        StringBuilder userContent = new StringBuilder();
        userContent.append("Please analyze this Git diff with strict adherence to instructions.\n");
        userContent.append("language: ").append(language).append("\n");
        userContent.append("style: ").append(style).append("\n");
        if (maxSummaryLength != null) {
            userContent.append("maxSummaryLength: ").append(maxSummaryLength).append("\n");
        }
        if (requestId != null && !requestId.isBlank()) {
            userContent.append("requestId: ").append(requestId).append("\n");
        }
        userContent.append("Diff: ```").append(diff).append("\n```");

        UserMessage userMessage = new UserMessage(userContent.toString());

        return new Prompt(
                List.of(systemMessage, userMessage)
        );
    }

    private ChatResponse callAiModel(Prompt prompt) {
        return chatClient.prompt(prompt)
                         .options(chatOptions)
                         .call()
                         .chatResponse();
    }

    private AnalyzeDiffResponse mapToAnalyzeDiffResponse(
            ChatResponse response,
            long responseTime,
            String diff,
            String requestId
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
            int tokensUsed = response.getMetadata().getUsage().getTotalTokens();

            List<String> touchedFiles = (aiResult.touchedFiles() == null || aiResult.touchedFiles().isEmpty())
                    ? extractTouchedFilesFromDiff(diff)
                    : aiResult.touchedFiles();

            AiCallMetadata metadata = AiCallMetadata.builder()
                                                    .modelName(model)
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
        } catch (Exception e) {
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

    private String sanitizeModelOutput(String value) {
        if (value == null || value.isBlank()) return "";
        value = value.replaceFirst("(?s)^```(?:json)?\\s*\\n?", "");
        value = value.replaceFirst("(?s)\\n?```$", "");
        return value.trim();
    }

    /**
     * Extracts the list of files touched from the provided Git diff.
     *
     * @param diff the unified Git diff content, may be {@code null} or blank
     * @return an immutable list of file paths extracted from the diff in the order they appear, never {@code null}; returns an empty list if {@code diff} is {@code null} or blank
     *
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
