package io.github.kxng0109.aiprcopilot.service;

import io.github.kxng0109.aiprcopilot.config.AiGenerationProperties;
import io.github.kxng0109.aiprcopilot.config.PrCopilotAnalysisProperties;
import io.github.kxng0109.aiprcopilot.config.api.dto.AnalyzeDiffRequest;
import io.github.kxng0109.aiprcopilot.config.api.dto.AnalyzeDiffResponse;
import io.github.kxng0109.aiprcopilot.error.DiffTooLargeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private PrCopilotAnalysisProperties prCopilotAnalysisProperties;

    @Mock
    private AiGenerationProperties aiGenerationProperties;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatOptions chatOptions;

    @InjectMocks
    private DiffAnalysisService diffAnalysisService;

    @BeforeEach
    public void setup() {
        prCopilotAnalysisProperties = new PrCopilotAnalysisProperties();
        prCopilotAnalysisProperties.setDefaultLanguage("en");
        prCopilotAnalysisProperties.setMaxDiffChars(50000);
        prCopilotAnalysisProperties.setIncludeRawModelOutput(false);
        prCopilotAnalysisProperties.setDefaultStyle("conventional-commits");

        aiGenerationProperties = new AiGenerationProperties();
        aiGenerationProperties.setMaxTokens(1024);
        aiGenerationProperties.setTemperature(0.1);
        aiGenerationProperties.setTimeoutMillis(30000L);

        diffAnalysisService = new DiffAnalysisService(prCopilotAnalysisProperties, chatClient, chatOptions);
    }

    @Test
    public void analyzeDiff_shouldUseDefaults_whenLanguageAndStyleAreNull() {
        AnalyzeDiffRequest request = AnalyzeDiffRequest.builder()
                                                       .diff("a diff sha")
                                                       .requestId("req-1")
                                                       .build();
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

        ChatResponse chatResponse = ChatResponse.builder()
                                                .generations(List.of(generation))
                                                .metadata(chatResponseMetadata)
                                                .build();

        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);

        AnalyzeDiffResponse response = diffAnalysisService.analyzeDiff(request);

        assertNotNull(response);
        assertEquals(chatResponseMetadata.getModel(), response.metadata().modelName());
        assertEquals(chatResponseMetadata.getUsage().getTotalTokens(), response.metadata().tokensUsed());

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        Prompt capturedPrompt = promptCaptor.getValue();

        assertThat(capturedPrompt.getInstructions().toString()).contains("language: en");
        assertThat(capturedPrompt.getInstructions().toString()).contains("style: conventional-commits");
    }

    @Test
    public void analyzeDiff_shouldThrowDoffTooLargeException_whenDiffExceedsMaxChars() {
        String largeDiff = "x".repeat(prCopilotAnalysisProperties.getMaxDiffChars() + 1);
        AnalyzeDiffRequest request = AnalyzeDiffRequest.builder()
                                                       .diff(largeDiff)
                                                       .requestId("req-1")
                                                       .build();

        assertThrows(DiffTooLargeException.class, () -> diffAnalysisService.analyzeDiff(request));
    }
}
