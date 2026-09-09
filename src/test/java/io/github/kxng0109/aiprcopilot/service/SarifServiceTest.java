package io.github.kxng0109.aiprcopilot.service;

import io.github.kxng0109.aiprcopilot.api.dto.AnalyzeDiffResponse;
import io.github.kxng0109.aiprcopilot.api.dto.RiskItem;
import io.github.kxng0109.aiprcopilot.config.PrCopilotAnalysisProperties;
import io.github.kxng0109.aiprcopilot.config.PrCopilotSarifProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SarifServiceTest {

    private PrCopilotSarifProperties sarifProperties;
    private PrCopilotAnalysisProperties analysisProperties;
    private SarifService sarifService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        sarifProperties = mock(PrCopilotSarifProperties.class);
        analysisProperties = mock(PrCopilotAnalysisProperties.class);
        lenient().when(sarifProperties.getCategory()).thenReturn("ai-pr-copilot");
        lenient().when(sarifProperties.getSuppressFile()).thenReturn("");
        lenient().when(analysisProperties.getMinLevel()).thenReturn("note");
        sarifService = new SarifService(sarifProperties, analysisProperties);
    }

    private AnalyzeDiffResponse response(RiskItem... risks) {
        return AnalyzeDiffResponse.builder()
                                  .title("t")
                                  .summary("s")
                                  .details("d")
                                  .risks(List.of(risks))
                                  .riskScore(0)
                                  .touchedFiles(List.of("src/Main.java"))
                                  .requestId("req-1")
                                  .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void toSarif_shouldEmitValidDocument() {
        Map<String, Object> sarif = sarifService.toSarif(
                response(new RiskItem("error", "SQL injection"), new RiskItem("note", "typo")));

        assertThat(sarif.get("version")).isEqualTo("2.1.0");
        assertThat(sarif.get("$schema").toString()).contains("sarif-2.1.0.json");
        List<Map<String, Object>> runs = (List<Map<String, Object>>) sarif.get("runs");
        assertThat(runs).hasSize(1);
        Map<String, Object> run = runs.get(0);
        assertThat(((Map<String, Object>) run.get("automationDetails")).get("id")).isEqualTo("ai-pr-copilot");
        List<Map<String, Object>> results = (List<Map<String, Object>>) run.get("results");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).get("level")).isEqualTo("error");
        assertThat(results.get(0).get("ruleId").toString()).startsWith("APR-");
        assertThat(((Map<String, Object>) results.get(0).get("partialFingerprints")))
                .containsKey("primaryLocationLineHash");
        Map<String, Object> tool = (Map<String, Object>) run.get("tool");
        Map<String, Object> driver = (Map<String, Object>) tool.get("driver");
        assertThat(driver.get("name")).isEqualTo("ai-pr-copilot");
        assertThat((List<Map<String, Object>>) driver.get("rules")).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void toSarif_shouldFilterBelowMinLevel() {
        when(analysisProperties.getMinLevel()).thenReturn("warning");

        Map<String, Object> sarif = sarifService.toSarif(
                response(new RiskItem("error", "boom"), new RiskItem("note", "typo")));

        List<Map<String, Object>> runs = (List<Map<String, Object>>) sarif.get("runs");
        List<Map<String, Object>> results = (List<Map<String, Object>>) runs.get(0).get("results");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("level")).isEqualTo("error");
    }

    @Test
    @SuppressWarnings("unchecked")
    void toSarif_shouldSuppressMatchingRuleId() throws Exception {
        Map<String, Object> probe = sarifService.toSarif(response(new RiskItem("warning", "slow query")));
        List<Map<String, Object>> runs = (List<Map<String, Object>>) probe.get("runs");
        List<Map<String, Object>> results = (List<Map<String, Object>>) runs.get(0).get("results");
        String ruleId = results.get(0).get("ruleId").toString();

        Path suppress = tempDir.resolve("ignore.yml");
        Files.writeString(suppress, "- ruleId: \"" + ruleId + "\"\n  reason: \"accepted\"\n");
        when(sarifProperties.getSuppressFile()).thenReturn(suppress.toString());

        Map<String, Object> sarif = sarifService.toSarif(response(new RiskItem("warning", "slow query")));
        List<Map<String, Object>> runs2 = (List<Map<String, Object>>) sarif.get("runs");
        assertThat((List<Map<String, Object>>) runs2.get(0).get("results")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void toSarif_shouldIgnoreExpiredSuppressions() throws Exception {
        Path suppress = tempDir.resolve("ignore.yml");
        Files.writeString(suppress, "- ruleId: \"APR-00000000\"\n  expires: \"2000-01-01\"\n");
        when(sarifProperties.getSuppressFile()).thenReturn(suppress.toString());

        Map<String, Object> sarif = sarifService.toSarif(response(new RiskItem("warning", "slow query")));
        List<Map<String, Object>> runs = (List<Map<String, Object>>) sarif.get("runs");
        assertThat((List<Map<String, Object>>) runs.get(0).get("results")).hasSize(1);
    }

    @Test
    void ruleId_shouldBeStableForSameRisk() {
        RiskItem risk = new RiskItem("error", "SQL injection");
        assertThat(SarifService.ruleId(risk)).isEqualTo(SarifService.ruleId(risk));
    }
}
