package io.github.kxng0109.aiprcopilot.service;

import io.github.kxng0109.aiprcopilot.api.dto.AnalyzeDiffRequest;
import io.github.kxng0109.aiprcopilot.api.dto.AnalyzeDiffResponse;
import io.github.kxng0109.aiprcopilot.config.AiProvider;
import io.github.kxng0109.aiprcopilot.config.MultiAiConfigurationProperties;
import io.github.kxng0109.aiprcopilot.config.PrCopilotAnalysisProperties;
import io.github.kxng0109.aiprcopilot.config.PrCopilotLoggingProperties;
import io.github.kxng0109.aiprcopilot.error.DiffTooLargeException;
import io.github.kxng0109.aiprcopilot.error.ModelOutputParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DiffAnalysisServiceTest {

    @Mock
    private PrCopilotAnalysisProperties analysisProperties;

    @Mock
    private MultiAiConfigurationProperties multiAiConfigurationProperties;

    @Mock
    private PrCopilotLoggingProperties loggingProperties;

    @Mock
    private ChatClient primaryChatClient;

    @Mock
    private ChatOptions primaryChatOptions;

    @Mock
    private PromptBuilderService promptBuilderService;

    @Mock
    private AiChatService aiChatService;

    @Mock
    private DiffResponseMapperService diffResponseMapperService;

    @InjectMocks
    private DiffAnalysisService diffAnalysisService;

    @BeforeEach
    public void setup() {
        lenient().when(analysisProperties.getDefaultLanguage()).thenReturn("en");
        lenient().when(analysisProperties.getMaxDiffChars()).thenReturn(50000);
        lenient().when(analysisProperties.isIncludeRawModelOutput()).thenReturn(false);
        lenient().when(analysisProperties.getDefaultStyle()).thenReturn("conventional-commits");

        lenient().when(multiAiConfigurationProperties.getProvider()).thenReturn(AiProvider.OPENAI);
        lenient().when(multiAiConfigurationProperties.isAutoFallback()).thenReturn(false);

        diffAnalysisService = new DiffAnalysisService(
                analysisProperties,
                primaryChatClient,
                primaryChatOptions,
                loggingProperties,
                multiAiConfigurationProperties,
                promptBuilderService,
                aiChatService,
                diffResponseMapperService,
                null,
                null
        );
    }

    @Test
    public void analyzeDiff_shouldUseDefaults_whenLanguageAndStyleAreNull() {
        String diff = "a diff sha";
        AnalyzeDiffRequest request = AnalyzeDiffRequest.builder()
                                                       .diff(diff)
                                                       .requestId("req-1")
                                                       .build();

        Prompt mockPrompt = mock(Prompt.class);
        when(promptBuilderService.buildDiffAnalysisPrompt(
                eq("en"),
                eq("conventional-commits"),
                eq(diff),
                isNull(),
                eq("req-1")
        )).thenReturn(mockPrompt);

        ChatResponse mockChatResponse = mockChatResponse();
        when(aiChatService.callAiModel(
                mockPrompt,
                primaryChatClient,
                primaryChatOptions
        )).thenReturn(mockChatResponse);

        AnalyzeDiffResponse expectedResponse = AnalyzeDiffResponse.builder()
                                                                  .title("test title")
                                                                  .summary("test summary")
                                                                  .requestId("req-1")
                                                                  .build();

        when(diffResponseMapperService.mapToAnalyzeDiffResponse(
                eq(mockChatResponse),
                anyLong(),
                eq(diff),
                eq("req-1"),
                eq("openai")
        )).thenReturn(expectedResponse);

        AnalyzeDiffResponse response = diffAnalysisService.analyzeDiff(request);

        assertNotNull(response);
        assertEquals(expectedResponse.title(), response.title());
        assertEquals(expectedResponse.summary(), response.summary());
        assertEquals(expectedResponse.requestId(), response.requestId());

        verify(promptBuilderService).buildDiffAnalysisPrompt(
                "en",
                "conventional-commits",
                diff,
                null,
                "req-1"
        );
        verify(aiChatService).callAiModel(mockPrompt, primaryChatClient, primaryChatOptions);
        verify(diffResponseMapperService).mapToAnalyzeDiffResponse(
                eq(mockChatResponse),
                anyLong(),
                eq(diff),
                eq("req-1"),
                eq("openai")
        );
    }

    @Test
    void analyzeDiff_shouldUseProvidedLanguageAndStyle_whenSpecified() {
        String diff = "diff content";
        AnalyzeDiffRequest request = AnalyzeDiffRequest.builder()
                                                       .diff(diff)
                                                       .language("fr")
                                                       .style("gitlab")
                                                       .maxSummaryLength(200)
                                                       .requestId("req-2")
                                                       .build();

        Prompt mockPrompt = mock(Prompt.class);
        when(promptBuilderService.buildDiffAnalysisPrompt(
                eq("fr"),
                eq("gitlab"),
                eq(diff),
                eq(200),
                eq("req-2")
        )).thenReturn(mockPrompt);

        ChatResponse mockChatResponse = mockChatResponse();
        when(aiChatService.callAiModel(mockPrompt, primaryChatClient, primaryChatOptions))
                .thenReturn(mockChatResponse);

        AnalyzeDiffResponse expectedResponse = AnalyzeDiffResponse.builder()
                                                                  .title("titre de test")
                                                                  .build();

        when(diffResponseMapperService.mapToAnalyzeDiffResponse(
                any(), anyLong(), any(), any(), any()
        )).thenReturn(expectedResponse);

        AnalyzeDiffResponse response = diffAnalysisService.analyzeDiff(request);

        assertNotNull(response);
        verify(promptBuilderService).buildDiffAnalysisPrompt("fr", "gitlab", diff, 200, "req-2");
    }


    @Test
    public void analyzeDiff_shouldThrowDoffTooLargeException_whenDiffExceedsMaxChars() {
        String largeDiff = "x".repeat(analysisProperties.getMaxDiffChars() + 1);
        AnalyzeDiffRequest request = AnalyzeDiffRequest.builder()
                                                       .diff(largeDiff)
                                                       .requestId("req-1")
                                                       .build();

        assertThrows(DiffTooLargeException.class, () -> diffAnalysisService.analyzeDiff(request));

        verify(promptBuilderService, never()).buildDiffAnalysisPrompt(any(), any(), any(), any(), any());
        verify(aiChatService, never()).callAiModel(any(), any(), any());
        verify(diffResponseMapperService, never()).mapToAnalyzeDiffResponse(any(), anyLong(), any(), any(), any());
    }

    @Test
    void analyzeDiff_shouldRethrowModelOutputParseException_whenParsingFails() {
        AnalyzeDiffRequest request = AnalyzeDiffRequest.builder()
                                                       .diff("diff")
                                                       .requestId("req-1")
                                                       .build();

        Prompt mockPrompt = mock(Prompt.class);
        when(promptBuilderService.buildDiffAnalysisPrompt(any(), any(), any(), any(), any()))
                .thenReturn(mockPrompt);

        when(aiChatService.callAiModel(any(), any(), any()))
                .thenReturn(mockChatResponse());

        when(diffResponseMapperService.mapToAnalyzeDiffResponse(any(), anyLong(), any(), any(), any()))
                .thenThrow(new ModelOutputParseException("Invalid JSON"));

        ModelOutputParseException exception = assertThrows(
                ModelOutputParseException.class,
                () -> diffAnalysisService.analyzeDiff(request)
        );

        assertEquals("Invalid JSON", exception.getMessage());
    }

    @Test
    void analyzeDiff_shouldUseFallback_whenPrimaryFailsAndAutoFallbackEnabled() {
        when(multiAiConfigurationProperties.isAutoFallback()).thenReturn(true);
        when(multiAiConfigurationProperties.getFallbackProvider()).thenReturn(AiProvider.ANTHROPIC);

        ChatClient fallbackChatClient = mock(ChatClient.class);
        ChatOptions fallbackChatOptions = mock(ChatOptions.class);

        diffAnalysisService = new DiffAnalysisService(
                analysisProperties,
                primaryChatClient,
                primaryChatOptions,
                loggingProperties,
                multiAiConfigurationProperties,
                promptBuilderService,
                aiChatService,
                diffResponseMapperService,
                fallbackChatClient,
                fallbackChatOptions
        );

        AnalyzeDiffRequest request = AnalyzeDiffRequest.builder()
                                                       .diff("diff")
                                                       .requestId("req-1")
                                                       .build();

        Prompt mockPrompt = mock(Prompt.class);
        when(promptBuilderService.buildDiffAnalysisPrompt(any(), any(), any(), any(), any()))
                .thenReturn(mockPrompt);

        when(aiChatService.callAiModel(mockPrompt, primaryChatClient, primaryChatOptions))
                .thenThrow(new RuntimeException("Primary failed"));

        ChatResponse fallbackResponse = mockChatResponse();
        when(aiChatService.callAiModel(mockPrompt, fallbackChatClient, fallbackChatOptions))
                .thenReturn(fallbackResponse);

        AnalyzeDiffResponse expectedResponse = AnalyzeDiffResponse.builder()
                                                                  .title("fallback response")
                                                                  .build();

        when(diffResponseMapperService.mapToAnalyzeDiffResponse(
                eq(fallbackResponse), anyLong(), any(), any(), eq("anthropic")
        )).thenReturn(expectedResponse);

        AnalyzeDiffResponse response = diffAnalysisService.analyzeDiff(request);

        assertNotNull(response);
        assertEquals("fallback response", response.title());

        verify(aiChatService).callAiModel(mockPrompt, primaryChatClient, primaryChatOptions);
        verify(aiChatService).callAiModel(mockPrompt, fallbackChatClient, fallbackChatOptions);
    }

    @Test
    void analyzeDiff_shouldThrowException_whenBothPrimaryAndFallbackFail() {
        when(multiAiConfigurationProperties.isAutoFallback()).thenReturn(true);
        when(multiAiConfigurationProperties.getFallbackProvider()).thenReturn(AiProvider.ANTHROPIC);

        ChatClient fallbackChatClient = mock(ChatClient.class);
        ChatOptions fallbackChatOptions = mock(ChatOptions.class);

        diffAnalysisService = new DiffAnalysisService(
                analysisProperties,
                primaryChatClient,
                primaryChatOptions,
                loggingProperties,
                multiAiConfigurationProperties,
                promptBuilderService,
                aiChatService,
                diffResponseMapperService,
                fallbackChatClient,
                fallbackChatOptions
        );

        AnalyzeDiffRequest request = AnalyzeDiffRequest.builder()
                                                       .diff("diff")
                                                       .requestId("req-1")
                                                       .build();

        Prompt mockPrompt = mock(Prompt.class);
        when(promptBuilderService.buildDiffAnalysisPrompt(any(), any(), any(), any(), any()))
                .thenReturn(mockPrompt);

        when(aiChatService.callAiModel(mockPrompt, primaryChatClient, primaryChatOptions))
                .thenThrow(new RuntimeException("Primary failed"));

        when(aiChatService.callAiModel(mockPrompt, fallbackChatClient, fallbackChatOptions))
                .thenThrow(new RuntimeException("Fallback also failed"));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> diffAnalysisService.analyzeDiff(request)
        );

        assertThat(exception.getMessage()).contains("Primary failed");
        assertThat(exception.getMessage()).contains("Fallback also failed");
    }

    @Test
    void analyzeDiff_shouldLogPrompt_whenLoggingEnabled() {
        when(loggingProperties.isLogPrompts()).thenReturn(true);

        AnalyzeDiffRequest request = AnalyzeDiffRequest.builder()
                                                       .diff("diff")
                                                       .requestId("req-1")
                                                       .build();

        Prompt mockPrompt = mock(Prompt.class);
        when(mockPrompt.toString()).thenReturn("Mock Prompt Content");
        when(promptBuilderService.buildDiffAnalysisPrompt(any(), any(), any(), any(), any()))
                .thenReturn(mockPrompt);

        when(aiChatService.callAiModel(any(), any(), any()))
                .thenReturn(mockChatResponse());

        when(diffResponseMapperService.mapToAnalyzeDiffResponse(any(), anyLong(), any(), any(), any()))
                .thenReturn(AnalyzeDiffResponse.builder().title("test").build());

        diffAnalysisService.analyzeDiff(request);

        verify(loggingProperties).isLogPrompts();
    }

    private ChatResponse mockChatResponse() {
        Generation generation = new Generation(
                new AssistantMessage("Some details or message")
        );

        Usage usage = new Usage() {
            @Override
            public Integer getPromptTokens() {
                return 0;
            }

            @Override
            public Integer getCompletionTokens() {
                return 0;
            }

            @Override
            public Object getNativeUsage() {
                return null;
            }

            @Override
            public Integer getTotalTokens() {
                return 120;
            }
        };

        ChatResponseMetadata chatResponseMetadata = ChatResponseMetadata.builder()
                                                                        .model("gpt-4o")
                                                                        .usage(usage)
                                                                        .build();

        return ChatResponse.builder()
                           .generations(List.of(generation))
                           .metadata(chatResponseMetadata)
                           .build();
    }
}
