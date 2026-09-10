package io.github.kxng0109.aiprcopilot.cli;

import io.github.kxng0109.aiprcopilot.api.dto.AiCallMetadata;
import io.github.kxng0109.aiprcopilot.api.dto.AnalyzeDiffResponse;
import io.github.kxng0109.aiprcopilot.service.DiffAnalysisService;
import io.github.kxng0109.aiprcopilot.service.GitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;
import tools.jackson.databind.json.JsonMapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AnalyzeCommandTest {
	@Mock
	private GitService gitService;

	@Mock
	private DiffAnalysisService diffAnalysisService;

	private AnalyzeCommand analyzeCommand;
	private CommandLine commandLine;
	private StringWriter stdout;
	private StringWriter stderr;

	@BeforeEach
	void setUp() {
		analyzeCommand = new AnalyzeCommand(gitService, diffAnalysisService, JsonMapper.builder().build());
		commandLine = new CommandLine(analyzeCommand);

		stdout = new StringWriter();
		stderr = new StringWriter();
		commandLine.setOut(new PrintWriter(stdout));
		commandLine.setErr(new PrintWriter(stderr));
	}

	@Test
	void help_shouldDisplayUsageInfo() {
		int exitCode = commandLine.execute("--help");

		assertEquals(0, exitCode);
		String output = stdout.toString();
		assertTrue(output.contains("analyze"));
		assertTrue(output.contains("--base"));
		assertTrue(output.contains("--staged"));
	}

	@Test
	void execute_notInGitRepo_shouldReturnError() {
		when(gitService.isGitRepository()).thenReturn(false);

		int exitCode = commandLine.execute();

		assertEquals(1, exitCode);
		assertTrue(stderr.toString().contains("Not a git repository"));
	}

	@Test
	void execute_noChanges_shouldReturnZeroWithMessage() {
		when(gitService.isGitRepository()).thenReturn(true);
		when(gitService.hasStagedChanges()).thenReturn(false);
		when(gitService.branchExists("main")).thenReturn(false);
		when(gitService.branchExists("master")).thenReturn(false);
		when(gitService.branchExists("develop")).thenReturn(false);

		int exitCode = commandLine.execute();

		assertEquals(0, exitCode);
		assertTrue(stdout.toString().contains("No changes detected"));
	}

	@Test
	void execute_withStagedChanges_shouldAnalyze() {
		when(gitService.isGitRepository()).thenReturn(true);
		when(gitService.getStagedDiff()).thenReturn("diff --git a/file.txt");
		when(diffAnalysisService.analyzeDiff(any())).thenReturn(mockResponse());

		int exitCode = commandLine.execute("--staged");

		assertEquals(0, exitCode);
		verify(gitService).getStagedDiff();
		verify(diffAnalysisService).analyzeDiff(any());
	}

	@Test
	void execute_withBaseBranch_shouldCompareAgainstBranch() {
		when(gitService.isGitRepository()).thenReturn(true);
		when(gitService.branchExists("main")).thenReturn(true);
		when(gitService.getDiffAgainstBranch("main")).thenReturn("diff content");
		when(diffAnalysisService.analyzeDiff(any())).thenReturn(mockResponse());

		int exitCode = commandLine.execute("--base", "main");

		assertEquals(0, exitCode);
		verify(gitService).getDiffAgainstBranch("main");
	}

	@Test
	void execute_withNonExistentBranch_shouldReturnError() {
		when(gitService.isGitRepository()).thenReturn(true);
		when(gitService.branchExists("non-existent")).thenReturn(false);

		int exitCode = commandLine.execute("--base", "non-existent");

		assertEquals(1, exitCode);
		assertTrue(stderr.toString().contains("does not exist"));
	}

	@Test
	void execute_withUncommittedFlag_shouldGetUncommittedDiff() {
		when(gitService.isGitRepository()).thenReturn(true);
		when(gitService.getUncommittedDiff()).thenReturn("uncommitted diff");
		when(diffAnalysisService.analyzeDiff(any())).thenReturn(mockResponse());

		int exitCode = commandLine.execute("--uncommitted");

		assertEquals(0, exitCode);
		verify(gitService).getUncommittedDiff();
	}

	@Test
	void execute_withJsonFormat_shouldOutputJson() {
		when(gitService.isGitRepository()).thenReturn(true);
		when(gitService.hasStagedChanges()).thenReturn(true);
		when(gitService.getStagedDiff()).thenReturn("diff");
		when(diffAnalysisService.analyzeDiff(any())).thenReturn(mockResponse());

		int exitCode = commandLine.execute("--format", "json");

		assertEquals(0, exitCode);
		String output = stdout.toString();
		assertTrue(output.contains("\"title\""));
		assertTrue(output.contains("\"summary\""));
	}

	@Test
	void execute_withCompactFormat_shouldOutputCompact() {
		when(gitService.isGitRepository()).thenReturn(true);
		when(gitService.hasStagedChanges()).thenReturn(true);
		when(gitService.getStagedDiff()).thenReturn("diff");
		when(diffAnalysisService.analyzeDiff(any())).thenReturn(mockResponse());

		int exitCode = commandLine.execute("--format", "compact");

		assertEquals(0, exitCode);
		String output = stdout.toString();
		assertTrue(output.contains("TITLE: "));
		assertTrue(output.contains("SUMMARY:"));
	}

	@Test
	void execute_withQuietFlag_shouldSuppressProgressMessages() {
		when(gitService.isGitRepository()).thenReturn(true);
		when(gitService.hasStagedChanges()).thenReturn(true);
		when(gitService.getStagedDiff()).thenReturn("diff");
		when(diffAnalysisService.analyzeDiff(any())).thenReturn(mockResponse());

		int exitCode = commandLine.execute("--quiet", "--format", "json");

		assertEquals(0, exitCode);
		String output = stdout.toString();
		// Should NOT contain progress messages
		assertFalse(output.contains("Analyzing"));
	}

	@Test
	void execute_autoDetect_shouldTryMainFirst() {
		when(gitService.isGitRepository()).thenReturn(true);
		when(gitService.hasStagedChanges()).thenReturn(false);
		when(gitService.branchExists("main")).thenReturn(true);
		when(gitService.getDiffAgainstBranch("main")).thenReturn("diff from main");
		when(diffAnalysisService.analyzeDiff(any())).thenReturn(mockResponse());

		int exitCode = commandLine.execute();

		assertEquals(0, exitCode);
		verify(gitService).getDiffAgainstBranch("main");
	}

	@Test
	void execute_autoDetect_shouldFallbackToMaster() {
		when(gitService.isGitRepository()).thenReturn(true);
		when(gitService.hasStagedChanges()).thenReturn(false);
		when(gitService.branchExists("main")).thenReturn(false);
		when(gitService.branchExists("master")).thenReturn(true);
		when(gitService.getDiffAgainstBranch("master")).thenReturn("diff from master");
		when(diffAnalysisService.analyzeDiff(any())).thenReturn(mockResponse());

		int exitCode = commandLine.execute();

		assertEquals(0, exitCode);
		verify(gitService).getDiffAgainstBranch("master");
	}

	private AnalyzeDiffResponse mockResponse() {
		return AnalyzeDiffResponse.builder()
		                          .title("feat:  add new feature")
		                          .summary("Added a new feature to the application")
		                          .details("Detailed description of changes")
		                          .risks(List.of(
				                          new io.github.kxng0109.aiprcopilot.api.dto.RiskItem("warning", "Risk 1"),
				                          new io.github.kxng0109.aiprcopilot.api.dto.RiskItem("note", "Risk 2")
		                          ))
		                          .riskScore(12)
		                          .suggestedTests(List.of("Test 1", "Test 2"))
		                          .touchedFiles(List.of("src/main/File.java"))
		                          .analysisNotes("Some notes")
		                          .metadata(AiCallMetadata.builder()
		                                                  .provider("openai")
		                                                  .modelName("gpt-4")
		                                                  .modelLatencyMs(1000)
		                                                  .tokensUsed(500)
		                                                  .build())
		                          .requestId("test-request-id")
		                          .build();
	}
}
