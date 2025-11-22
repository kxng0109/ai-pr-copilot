package io.github.kxng0109.aiprcopilot.service;

import io.github.kxng0109.aiprcopilot.config.PrCopilotAnalysisProperties;
import io.github.kxng0109.aiprcopilot.config.api.dto.AiCallMetadata;
import io.github.kxng0109.aiprcopilot.config.api.dto.AnalyzeDiffRequest;
import io.github.kxng0109.aiprcopilot.config.api.dto.AnalyzeDiffResponse;
import io.github.kxng0109.aiprcopilot.error.DiffTooLargeException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DiffAnalysisService {

    private final String SYSTEM_PROMPT = """
            You are a senior software engineer and expert code reviewer.
            You will receive a unified Git diff and some hints:
            
            - language: {language}
            - style: {style}
            
            Your job is to analyze the diff and produce a JSON object with these fields:
            - title (string)
            - summary (string)
            - details (string)
            - risks (array of strings)
            - suggestedTests (array of strings)
            - touchedFiles (array of strings)
            - analysisNotes (string or null)
            - metadata (object: modelName, modelLatencyMs, tokensUsed)
            - requestId (string or null)
            - rawModelOutput (string or null)
            
            The JSON must be valid and contain only these fields.
            Be concise and do not invent information not suggested by the diff.
            """;

    private final PrCopilotAnalysisProperties analysisProperties;
    private final ChatClient chatClient;
    private final ChatOptions chatOptions;

    public AnalyzeDiffResponse analyzeDiff(AnalyzeDiffRequest request) {
        String diff = request.diff();
        if (diff.length() > analysisProperties.getMaxDiffChars()) {
            throw new DiffTooLargeException(
                    String.format("Diff exceeded maximum allowed size of %d characters",
                                  analysisProperties.getMaxDiffChars()
                    )
            );
        }

        String language = useDefaultIfBlank(request.language(), analysisProperties.getDefaultLanguage());
        String style = useDefaultIfBlank(request.style(), analysisProperties.getDefaultStyle());
        Integer maxSummaryLength = request.maxSummaryLength();

        Prompt prompt = buildPrompt(language, style, diff, maxSummaryLength, request.requestId());

        long start = System.currentTimeMillis();
        ChatResponse aiResponse = callAiModel(prompt);
        long end = System.currentTimeMillis();

        long latencyMs = end - start;

        return mapToAnalyzeDiffResponse(aiResponse, latencyMs);
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
        userContent.append("Analyze the following Git diff.\n");
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
        Prompt promptWithOptions = new Prompt(prompt.getInstructions(), chatOptions);
        return chatClient.prompt(promptWithOptions)
                         .call()
                         .chatResponse();
    }

    private String useDefaultIfBlank(String givenValue, String defaultValue) {
        return (givenValue == null || givenValue.trim().isEmpty()) ? defaultValue : givenValue;
    }

    private AnalyzeDiffResponse mapToAnalyzeDiffResponse(ChatResponse response, long responseTime) {
        String model = response.getMetadata().getModel();
        int tokensUsed = response.getMetadata().getUsage().getTotalTokens();
        AiCallMetadata metadata = AiCallMetadata.builder()
                                                .modelName(model)
                                                .tokensUsed(tokensUsed)
                                                .modelLatencyMs(responseTime)
                                                .build();

        return AnalyzeDiffResponse.builder()
                                  .metadata(metadata)
                                  .build();
    }
}
