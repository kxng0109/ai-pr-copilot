package io.github.kxng0109.aiprcopilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kxng0109.aiprcopilot.api.dto.AnalyzeDiffResponse;
import io.github.kxng0109.aiprcopilot.config.PrCopilotAnalysisProperties;
import io.github.kxng0109.aiprcopilot.config.PrCopilotLoggingProperties;
import io.github.kxng0109.aiprcopilot.error.ModelOutputParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiffResponseMapperServiceTest {

    @Mock
    private PrCopilotLoggingProperties loggingProperties;

    @Mock
    private PrCopilotAnalysisProperties analysisProperties;

    private ObjectMapper objectMapper;
    private DiffResponseMapperService mapperService;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();

        lenient().when(analysisProperties.isIncludeRawModelOutput()).thenReturn(false);
        lenient().when(loggingProperties.isLogResponses()).thenReturn(false);

        mapperService = new DiffResponseMapperService(
                objectMapper,
                loggingProperties,
                analysisProperties
        );
    }

    @Test
    void mapToAnalyzeDiffResponse_shouldParseValidJsonResponse() {
        String validJson = """
                {
                  "title": "feat: add new feature",
                  "summary": "Added a new feature to the system",
                  "details": "Detailed implementation of the feature",
                  "risks": ["Risk 1", "Risk 2"],
                  "suggestedTests": ["Test 1", "Test 2"],
                  "touchedFiles": ["file1.java", "file2.java"],
                  "analysisNotes": "Some notes"
                }
                """;

        ChatResponse response = createChatResponse(validJson);
        String diff = "diff --git a/file1.java b/file1.java";

        AnalyzeDiffResponse result = mapperService.mapToAnalyzeDiffResponse(
                response, 1000L, diff, "req-1", "openai"
        );

        assertNotNull(result);
        assertEquals("feat: add new feature", result.title());
        assertEquals("Added a new feature to the system", result.summary());
        assertEquals("Detailed implementation of the feature", result.details());
        assertThat(result.risks()).containsExactly("Risk 1", "Risk 2");
        assertThat(result.suggestedTests()).containsExactly("Test 1", "Test 2");
        assertThat(result.touchedFiles()).containsExactly("file1.java", "file2.java");
        assertEquals("Some notes", result.analysisNotes());
        assertEquals("req-1", result.requestId());
        assertEquals("openai", result.metadata().provider());
        assertEquals(1000L, result.metadata().modelLatencyMs());
    }

    @Test
    void mapToAnalyzeDiffResponse_shouldExtractTouchedFiles_whenNotProvidedByModel() {
        String jsonWithoutFiles = """
                {
                  "title": "test",
                  "summary": "summary",
                  "details": "details",
                  "risks": [],
                  "suggestedTests": [],
                  "touchedFiles": [],
                  "analysisNotes": null
                }
                """;

        String diff = """
                diff --git a/src/main/File1.java b/src/main/File1.java
                index abc..def
                --- a/src/main/File1.java
                +++ b/src/main/File1.java
                diff --git a/src/test/File2.java b/src/test/File2.java
                """;

        ChatResponse response = createChatResponse(jsonWithoutFiles);

        AnalyzeDiffResponse result = mapperService.mapToAnalyzeDiffResponse(
                response, 500L, diff, "req-2", "anthropic"
        );

        assertThat(result.touchedFiles()).containsExactly(
                "src/main/File1.java",
                "src/test/File2.java"
        );
    }

    @Test
    void mapToAnalyzeDiffResponse_shouldSanitizeJsonWithMarkdownFences() {
        String jsonWithFences = """
                ```json
                {
                  "title": "test",
                  "summary": "summary",
                  "details": "details",
                  "risks": [],
                  "suggestedTests": [],
                  "touchedFiles": [],
                  "analysisNotes": null
                }
                ```
                """;

        ChatResponse response = createChatResponse(jsonWithFences);

        AnalyzeDiffResponse result = mapperService.mapToAnalyzeDiffResponse(
                response, 100L, "diff", "req-3", "gemini"
        );

        assertNotNull(result);
        assertEquals("test", result.title());
    }

    @Test
    void mapToAnalyzeDiffResponse_shouldExtractJsonFromSurroundingText() {
        String responseWithExtra = """
                Sure, here's the analysis:
                
                {
                  "title": "test",
                  "summary": "summary",
                  "details": "details",
                  "risks": [],
                  "suggestedTests": [],
                  "touchedFiles": [],
                  "analysisNotes": null
                }
                
                Hope this helps!
                """;

        ChatResponse response = createChatResponse(responseWithExtra);

        AnalyzeDiffResponse result = mapperService.mapToAnalyzeDiffResponse(
                response, 100L, "diff", "req-4", "openai"
        );

        assertNotNull(result);
        assertEquals("test", result.title());
    }

    @Test
    void mapToAnalyzeDiffResponse_shouldIncludeRawOutput_whenConfigured() {
        when(analysisProperties.isIncludeRawModelOutput()).thenReturn(true);

        String json = """
                {"title":"test","summary":"s","details":"d","risks":[],"suggestedTests":[],"touchedFiles":[],"analysisNotes":null}
                """;

        ChatResponse response = createChatResponse(json);

        AnalyzeDiffResponse result = mapperService.mapToAnalyzeDiffResponse(
                response, 100L, "diff", "req-5", "openai"
        );

        assertNotNull(result.rawModelOutput());
        assertThat(result.rawModelOutput()).contains("test");
    }

    @Test
    void mapToAnalyzeDiffResponse_shouldThrowException_whenJsonIsInvalid() {
        String invalidJson = "not valid json at all";
        ChatResponse response = createChatResponse(invalidJson);

        assertThrows(ModelOutputParseException.class, () ->
                mapperService.mapToAnalyzeDiffResponse(
                        response, 100L, "diff", "req-6", "openai"
                )
        );
    }

    @Test
    void mapToAnalyzeDiffResponse_shouldThrowException_whenRequiredFieldsMissing() {
        String incompleteJson = """
                {
                  "title": "test"
                }
                """;

        ChatResponse response = createChatResponse(incompleteJson);

        ModelOutputParseException exception = assertThrows(
                ModelOutputParseException.class,
                () -> mapperService.mapToAnalyzeDiffResponse(
                        response, 100L, "diff", "req-7", "openai"
                )
        );

        assertThat(exception.getMessage()).contains("missing required fields");
    }

    @Test
    void mapToAnalyzeDiffResponse_shouldHandleNullTokenUsage() {
        String json = """
                {"title":"test","summary":"s","details":"d","risks":[],"suggestedTests":[],"touchedFiles":[],"analysisNotes":null}
                """;

        ChatResponse response = createChatResponseWithNullUsage(json);

        AnalyzeDiffResponse result = mapperService.mapToAnalyzeDiffResponse(
                response, 100L, "diff", "req-8", "ollama"
        );

        assertNotNull(result);
        assertNull(result.metadata().tokensUsed());
    }

    private ChatResponse createChatResponse(String content) {
        Generation generation = new Generation(new AssistantMessage(content));

        Usage usage = new Usage() {
            @Override
            public Integer getPromptTokens() {
                return 10;
            }

            @Override
            public Integer getCompletionTokens() {
                return 20;
            }

            @Override
            public Integer getTotalTokens() {
                return 30;
            }

            @Override
            public Object getNativeUsage() {
                return null;
            }
        };

        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                                                            .model("test-model")
                                                            .usage(usage)
                                                            .build();

        return ChatResponse.builder()
                           .generations(List.of(generation))
                           .metadata(metadata)
                           .build();
    }

    private ChatResponse createChatResponseWithNullUsage(String content) {
        Generation generation = new Generation(new AssistantMessage(content));

        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                                                            .model("ollama-model")
                                                            .usage(null)
                                                            .build();

        return ChatResponse.builder()
                           .generations(List.of(generation))
                           .metadata(metadata)
                           .build();
    }
}