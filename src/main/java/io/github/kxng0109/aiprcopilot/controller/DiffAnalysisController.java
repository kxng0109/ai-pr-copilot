package io.github.kxng0109.aiprcopilot.controller;

import io.github.kxng0109.aiprcopilot.api.dto.AnalyzeDiffRequest;
import io.github.kxng0109.aiprcopilot.api.dto.AnalyzeDiffResponse;
import io.github.kxng0109.aiprcopilot.service.DiffAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
@Tag(name = "Diff Analysis", description = "Endpoints for analyzing Git diffs")
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

    @Operation(
            summary = "Analyze a Git diff using AI code review",
            description = "Accepts a unified Git diff and returns an AI-generated structured analysis, risk list, suggested tests, and more."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully analyzed"),
            @ApiResponse(responseCode = "400", description = "Validation error (e.g., blank diff)"),
            @ApiResponse(responseCode = "413", description = "Diff too large"),
            @ApiResponse(responseCode = "422", description = "AI model returned invalid output"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping(value = "/analyze-diff", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalyzeDiffResponse> analyzeDiff(@Valid @RequestBody AnalyzeDiffRequest request) {
        AnalyzeDiffResponse response = diffAnalysisService.analyzeDiff(request);
        return ResponseEntity.ok(response);
    }
}
