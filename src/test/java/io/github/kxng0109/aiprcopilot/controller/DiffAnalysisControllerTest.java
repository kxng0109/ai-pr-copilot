package io.github.kxng0109.aiprcopilot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kxng0109.aiprcopilot.api.dto.AiCallMetadata;
import io.github.kxng0109.aiprcopilot.api.dto.AnalyzeDiffRequest;
import io.github.kxng0109.aiprcopilot.api.dto.AnalyzeDiffResponse;
import io.github.kxng0109.aiprcopilot.error.DiffTooLargeException;
import io.github.kxng0109.aiprcopilot.service.DiffAnalysisService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DiffAnalysisController.class)
public class DiffAnalysisControllerTest {
    @MockitoBean
    private DiffAnalysisService diffAnalysisService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void analyzeDiff_shouldReturn200Ok_whenRequestIsValid() throws Exception {
        AnalyzeDiffRequest request = AnalyzeDiffRequest.builder()
                                                       .requestId("req-1")
                                                       .diff("diff-1")
                                                       .language("en")
                                                       .maxSummaryLength(1024)
                                                       .style("conventional-commit")
                                                       .build();

        AiCallMetadata metadata = AiCallMetadata.builder()
                                                .modelLatencyMs(100)
                                                .modelName("gpt-4o")
                                                .tokensUsed(100)
                                                .build();

        AnalyzeDiffResponse response = AnalyzeDiffResponse.builder()
                                                          .requestId("req-1")
                                                          .metadata(metadata)
                                                          .analysisNotes(null)
                                                          .details("Some details")
                                                          .rawModelOutput(null)
                                                          .risks(List.of())
                                                          .suggestedTests(List.of())
                                                          .touchedFiles(List.of("afile.txt"))
                                                          .summary("Some summary")
                                                          .title("Some title")
                                                          .build();

        Mockito.when(diffAnalysisService.analyzeDiff(request))
               .thenReturn(response);

        mockMvc.perform(post("/api/v1/analyze-diff")
                                .content(objectMapper.writeValueAsString(request))
                                .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$.title").value(response.title()));

        verify(diffAnalysisService).analyzeDiff(any(AnalyzeDiffRequest.class));
    }

    @Test
    public void analyzeDiff_shouldThrow400BadRequest_whenDIffIsBlank() throws Exception {
        AnalyzeDiffRequest invalidRequest = AnalyzeDiffRequest.builder()
                                                              .diff("  ")
                                                              .language("en")
                                                              .style("conventional-commits")
                                                              .maxSummaryLength(300)
                                                              .requestId("req-2")
                                                              .build();

        mockMvc.perform(post("/api/v1/analyze-diff")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.statusCode").value(400))
               .andExpect(jsonPath("$.message").value("{diff=Diff must not be blank}"))
               .andExpect(jsonPath("$.path").value("/api/v1/analyze-diff"));

        verify(diffAnalysisService, never()).analyzeDiff(any(AnalyzeDiffRequest.class));
    }

    @Test
    void analyzeDiff_shouldReturn400BadRequest_whenDiffIsMissing() throws Exception {
        String invalidJson = """
            {
              "language": "en",
              "style": "conventional-commits"
            }
            """;

        mockMvc.perform(post("/api/v1/analyze-diff")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidJson))
               .andExpect(status().isBadRequest());

        verify(diffAnalysisService, never()).analyzeDiff(any(AnalyzeDiffRequest.class));
    }

    @Test
    void analyzeDiff_shouldReturn400MethodArgumentNotValidException_whenRequestBodyIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/analyze-diff")
                                .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.statusCode").value(400))
               .andExpect(jsonPath("$.message").value("Request body is missing. JSON object required."));

        verify(diffAnalysisService, never()).analyzeDiff(any(AnalyzeDiffRequest.class));
    }

    @Test
    void analyzeDiff_shouldReturn405MethodNotAllowed_whenUsingWrongHttpMethod() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/analyze-diff"))
               .andExpect(status().isMethodNotAllowed())
               .andExpect(jsonPath("$.statusCode").value(405));

        verify(diffAnalysisService, never()).analyzeDiff(any(AnalyzeDiffRequest.class));
    }

    @Test
    public void analyzeDiff_shouldThrow413DiffTooLargeException_whenDiffIsTooLarge() throws Exception {
        int maxDiffChars = 1024;
        String diff = "x".repeat(maxDiffChars + 1);

        AnalyzeDiffRequest request = AnalyzeDiffRequest.builder()
                                                       .diff(diff)
                                                       .maxSummaryLength(1024)
                                                       .requestId("req-1")
                                                       .build();

        when(diffAnalysisService.analyzeDiff(any(AnalyzeDiffRequest.class)))
                .thenThrow(new DiffTooLargeException());

        mockMvc.perform(post("/api/v1/analyze-diff")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isPayloadTooLarge())
               .andExpect(jsonPath("$.statusCode").value(413))
               .andExpect(jsonPath("$.message").value("Diff exceeded maximum allowed size"))
               .andExpect(jsonPath("$.path").value("/api/v1/analyze-diff"));

        verify(diffAnalysisService).analyzeDiff(any(AnalyzeDiffRequest.class));
    }

    @Test
    public void shouldThrow404NoResourceFoundException_whenEndpointDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/does-not-exist")
                                .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.statusCode").value(404))
               .andExpect(jsonPath("$.path").value("/api/v1/does-not-exist"));

        verify(diffAnalysisService, never()).analyzeDiff(any(AnalyzeDiffRequest.class));
    }

    @Test
    public void analyzeDiff_shouldThrow500RuntimeException_whenErrorIsGeneric() throws Exception {
        AnalyzeDiffRequest request = AnalyzeDiffRequest.builder()
                                                       .diff("x")
                                                       .maxSummaryLength(1024)
                                                       .requestId("req-1")
                                                       .build();

        when(diffAnalysisService.analyzeDiff(any(AnalyzeDiffRequest.class)))
                .thenThrow(new RuntimeException("boommmmmm!!!"));

        mockMvc.perform(post("/api/v1/analyze-diff")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isInternalServerError())
               .andExpect(jsonPath("$.statusCode").value(500))
               .andExpect(jsonPath("$.message").value("boommmmmm!!!"))
               .andExpect(jsonPath("$.path").value("/api/v1/analyze-diff"));

        verify(diffAnalysisService).analyzeDiff(any(AnalyzeDiffRequest.class));
    }
}
