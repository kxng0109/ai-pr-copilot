package io.github.kxng0109.aiprcopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kxng0109.aiprcopilot.config.api.dto.AiCallMetadata;
import io.github.kxng0109.aiprcopilot.config.api.dto.AnalyzeDiffRequest;
import io.github.kxng0109.aiprcopilot.config.api.dto.AnalyzeDiffResponse;
import io.github.kxng0109.aiprcopilot.controller.DiffAnalysisController;
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
import static org.mockito.Mockito.verify;
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
    public void analyzeDiff_shouldReturn400BadRequest_whenDIffIsBlank() throws Exception {
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
               .andExpect(status().isBadRequest());
    }
}
