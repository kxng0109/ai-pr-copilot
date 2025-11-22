package io.github.kxng0109.aiprcopilot.controller;

import io.github.kxng0109.aiprcopilot.config.api.dto.AnalyzeDiffRequest;
import io.github.kxng0109.aiprcopilot.config.api.dto.AnalyzeDiffResponse;
import io.github.kxng0109.aiprcopilot.service.DiffAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles requests to analyze a code change diff.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DiffAnalysisController {

    private final DiffAnalysisService diffAnalysisService;

    /**
     * Analyzes a code change diff and returns the results.
     *
     * @param request the request containing the diff content, language, style, max summary length, and request ID, must not be null
     * @return the response containing the analysis title, summary, details, risks, suggested tests, touched files, analysis notes, metadata, request ID, and raw model output, never
     *  null
     * @throws io.github.kxng0109.aiprcopilot.error.DiffTooLargeException if the diff content exceeds the maximum allowed size
     */
    @PostMapping("/analyze-diff")
    public ResponseEntity<AnalyzeDiffResponse> analyzeDiff(@Valid @RequestBody AnalyzeDiffRequest request) {
        AnalyzeDiffResponse response = diffAnalysisService.analyzeDiff(request);
        return ResponseEntity.ok(response);
    }
}
