package io.github.kxng0109.aiprcopilot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretScanServiceTest {

    private final SecretScanService scanService = new SecretScanService();

    @Test
    void scan_shouldBlockAwsKey() {
        SecretScanService.ScanResult result =
                scanService.scan("diff --git\n+key = AKIAIOSFODNN7EXAMPLE\n");

        assertThat(result.verdict()).isEqualTo(SecretScanService.Verdict.BLOCKED);
        assertThat(result.reason()).contains("aws-access-key");
        assertThat(result.text()).isEmpty();
    }

    @Test
    void scan_shouldBlockGithubToken() {
        String token = "ghp_" + "a".repeat(36);
        SecretScanService.ScanResult result = scanService.scan("+token=" + token);

        assertThat(result.verdict()).isEqualTo(SecretScanService.Verdict.BLOCKED);
    }

    @Test
    void scan_shouldBlockPrivateKeyBlock() {
        SecretScanService.ScanResult result =
                scanService.scan("+-----BEGIN RSA PRIVATE KEY-----\n+MIIB");

        assertThat(result.verdict()).isEqualTo(SecretScanService.Verdict.BLOCKED);
    }

    @Test
    void scan_shouldRedactGenericHighEntropyAssignment() {
        String secret = "x".repeat(10) + "Q7f9K2mZvX4pL8sN6qW3eR5tY1uI0oP";
        SecretScanService.ScanResult result =
                scanService.scan("diff\n+api_key = \"" + secret + "\"\n");

        assertThat(result.verdict()).isEqualTo(SecretScanService.Verdict.REDACTED);
        assertThat(result.text()).contains("[REDACTED-SECRET]");
        assertThat(result.text()).doesNotContain(secret);
    }

    @Test
    void scan_shouldPassCleanDiff() {
        SecretScanService.ScanResult result =
                scanService.scan("diff --git a/Foo.java b/Foo.java\n+public void ok() {}");

        assertThat(result.verdict()).isEqualTo(SecretScanService.Verdict.CLEAN);
        assertThat(result.text()).contains("public void ok()");
    }

    @Test
    void scan_shouldPassLowEntropyAssignment() {
        SecretScanService.ScanResult result =
                scanService.scan("+password = \"changeme\"\n");

        assertThat(result.verdict()).isEqualTo(SecretScanService.Verdict.CLEAN);
    }

    @Test
    void scan_shouldHandleNullAndEmpty() {
        assertThat(scanService.scan(null).verdict()).isEqualTo(SecretScanService.Verdict.CLEAN);
        assertThat(scanService.scan("").verdict()).isEqualTo(SecretScanService.Verdict.CLEAN);
    }

    @Test
    void shannonEntropy_shouldScoreUniformLowAndRandomHigh() {
        assertThat(SecretScanService.shannonEntropy("aaaaaaaaaaaaaaaa")).isLessThan(1.0);
        assertThat(SecretScanService.shannonEntropy("Q7f9K2mZvX4pL8sN6qW3eR5tY1uI0oP")).isGreaterThan(4.0);
    }

    @Test
    void describe_shouldNameMatchedRulesWithoutValues() {
        String text = "AKIAIOSFODNN7EXAMPLE and ghp_" + "b".repeat(36);

        assertThat(scanService.describe(text)).contains("aws-access-key", "github-token");
        assertThat(scanService.describe(text).toString()).doesNotContain("AKIAIOSFODNN7EXAMPLE");
    }
}
