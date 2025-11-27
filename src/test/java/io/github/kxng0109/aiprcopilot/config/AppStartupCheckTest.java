package io.github.kxng0109.aiprcopilot.config;

import io.github.kxng0109.aiprcopilot.error.CustomApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AppStartupCheckTest {

    @Mock
    private MultiAiConfigurationProperties multiAiConfig;

    private AppStartupCheck appStartupCheck;

    @BeforeEach
    void setup() {
        appStartupCheck = new AppStartupCheck(multiAiConfig);
    }

    @Test
    void validateConfiguration_shouldPass_whenOpenAiConfiguredCorrectly() {
        when(multiAiConfig.getProvider()).thenReturn(AiProvider.OPENAI);
        when(multiAiConfig.isAutoFallback()).thenReturn(false);

        ReflectionTestUtils.setField(appStartupCheck, "openAiApiKey", "sk-valid-key");

        assertDoesNotThrow(() -> appStartupCheck.validateConfiguration());
    }

    @Test
    void validateConfiguration_shouldFail_whenOpenAiKeyIsMissing() {
        when(multiAiConfig.getProvider()).thenReturn(AiProvider.OPENAI);

        ReflectionTestUtils.setField(appStartupCheck, "openAiApiKey", "");

        CustomApiException exception = assertThrows(
                CustomApiException.class,
                () -> appStartupCheck.validateConfiguration()
        );

        assertTrue(exception.getMessage().contains("OPENAI"));
        assertTrue(exception.getMessage().contains("not configured"));
    }

    @Test
    void validateConfiguration_shouldFail_whenOpenAiKeyIsDefault() {
        when(multiAiConfig.getProvider()).thenReturn(AiProvider.OPENAI);

        ReflectionTestUtils.setField(appStartupCheck, "openAiApiKey", "default-value");

        CustomApiException exception = assertThrows(
                CustomApiException.class,
                () -> appStartupCheck.validateConfiguration()
        );

        assertTrue(exception.getMessage().contains("OPENAI"));
    }

    @Test
    void validateConfiguration_shouldPass_whenAnthropicConfiguredCorrectly() {
        when(multiAiConfig.getProvider()).thenReturn(AiProvider.ANTHROPIC);
        when(multiAiConfig.isAutoFallback()).thenReturn(false);

        ReflectionTestUtils.setField(appStartupCheck, "anthropicApiKey", "sk-ant-valid-key");

        assertDoesNotThrow(() -> appStartupCheck.validateConfiguration());
    }

    @Test
    void validateConfiguration_shouldFail_whenAnthropicKeyIsMissing() {
        when(multiAiConfig.getProvider()).thenReturn(AiProvider.ANTHROPIC);

        ReflectionTestUtils.setField(appStartupCheck, "anthropicApiKey", null);

        CustomApiException exception = assertThrows(
                CustomApiException.class,
                () -> appStartupCheck.validateConfiguration()
        );

        assertTrue(exception.getMessage().contains("ANTHROPIC"));
    }

    @Test
    void validateConfiguration_shouldPass_whenGeminiConfiguredCorrectly() {
        when(multiAiConfig.getProvider()).thenReturn(AiProvider.GEMINI);
        when(multiAiConfig.isAutoFallback()).thenReturn(false);

        ReflectionTestUtils.setField(appStartupCheck, "vertexAiProjectId", "my-gcp-project");

        assertDoesNotThrow(() -> appStartupCheck.validateConfiguration());
    }

    @Test
    void validateConfiguration_shouldFail_whenGeminiProjectIdMissing() {
        when(multiAiConfig.getProvider()).thenReturn(AiProvider.GEMINI);

        ReflectionTestUtils.setField(appStartupCheck, "vertexAiProjectId", "");

        CustomApiException exception = assertThrows(
                CustomApiException.class,
                () -> appStartupCheck.validateConfiguration()
        );

        assertTrue(exception.getMessage().contains("Gemini"));
        assertTrue(exception.getMessage().contains("GEMINI_PROJECT_ID"));
    }

    @Test
    void validateConfiguration_shouldPass_whenFallbackConfiguredCorrectly() {
        when(multiAiConfig.getProvider()).thenReturn(AiProvider.OPENAI);
        when(multiAiConfig.isAutoFallback()).thenReturn(true);
        when(multiAiConfig.getFallbackProvider()).thenReturn(AiProvider.ANTHROPIC);

        ReflectionTestUtils.setField(appStartupCheck, "openAiApiKey", "sk-valid");
        ReflectionTestUtils.setField(appStartupCheck, "anthropicApiKey", "sk-ant-valid");

        assertDoesNotThrow(() -> appStartupCheck.validateConfiguration());
    }

    @Test
    void validateConfiguration_shouldFail_whenFallbackEnabledButNotConfigured() {
        when(multiAiConfig.getProvider()).thenReturn(AiProvider.OPENAI);
        when(multiAiConfig.isAutoFallback()).thenReturn(true);
        when(multiAiConfig.getFallbackProvider()).thenReturn(null);

        ReflectionTestUtils.setField(appStartupCheck, "openAiApiKey", "sk-valid");

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> appStartupCheck.validateConfiguration()
        );

        assertTrue(exception.getMessage().contains("Auto-fallback is enabled"));
        assertTrue(exception.getMessage().contains("fallback provider is not set/configured"));
    }

    @Test
    void validateConfiguration_shouldFail_whenFallbackProviderKeyMissing() {
        when(multiAiConfig.getProvider()).thenReturn(AiProvider.OPENAI);
        when(multiAiConfig.isAutoFallback()).thenReturn(true);
        when(multiAiConfig.getFallbackProvider()).thenReturn(AiProvider.ANTHROPIC);

        ReflectionTestUtils.setField(appStartupCheck, "openAiApiKey", "sk-valid");
        ReflectionTestUtils.setField(appStartupCheck, "anthropicApiKey", "");

        CustomApiException exception = assertThrows(
                CustomApiException.class,
                () -> appStartupCheck.validateConfiguration()
        );

        assertTrue(exception.getMessage().contains("ANTHROPIC"));
    }

    @Test
    void validateConfiguration_shouldPass_whenOllamaSelected() {
        when(multiAiConfig.getProvider()).thenReturn(AiProvider.OLLAMA);
        ReflectionTestUtils.setField(appStartupCheck, "ollamaBaseUrl", "http://localhost:11434");
        ReflectionTestUtils.setField(appStartupCheck, "ollamaChatModel", "qwen3:4b");

        assertDoesNotThrow(() -> appStartupCheck.validateConfiguration());
    }
}