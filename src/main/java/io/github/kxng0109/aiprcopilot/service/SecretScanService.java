package io.github.kxng0109.aiprcopilot.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * In-process secret/PII pre-scan for diffs before any LLM send.
 *
 * <p>Tiers: {@code BLOCK} on high-confidence anchored provider IDs and PEM blocks,
 * {@code REDACT} on strict generic high-entropy assignments (token replaced, analysis
 * continues), {@code WARN} on PII-shaped content (metric + log only, never blocks).
 * Any internal scan failure fails closed to {@code BLOCK}. Secrets never leave the JVM.
 */
@Service
@Slf4j
public class SecretScanService {

    /** Minimum Shannon entropy (bits/char) for generic high-entropy candidates. */
    static final double GENERIC_ENTROPY_THRESHOLD = 4.5;

    /** Minimum token length considered for generic entropy redaction. */
    static final int GENERIC_MIN_LENGTH = 20;

    private static final String REDACTED = "[REDACTED-SECRET]";

    private record BlockRule(Pattern pattern, String name) {}

    private static final List<BlockRule> BLOCK_RULES = List.of(
            rule("aws-access-key", "AKIA[0-9A-Z]{16}"),
            rule("github-token", "\\bgh[pousr]_[A-Za-z0-9]{36,}\\b"),
            rule("github-pat-classic", "\\bghp_[A-Za-z0-9]{36}\\b"),
            rule("stripe-live", "\\bsk_live_[A-Za-z0-9]{16,}\\b"),
            rule("stripe-restricted", "\\brk_live_[A-Za-z0-9]{16,}\\b"),
            rule("slack-token", "\\bxox[baprs]-[A-Za-z0-9-]{10,}\\b"),
            rule("google-api-key", "\\bAIza[0-9A-Za-z\\-_]{35}\\b"),
            rule("openai-key", "\\bsk-(proj-)?[A-Za-z0-9]{20,}\\b"),
            rule("anthropic-key", "\\bsk-ant-[A-Za-z0-9\\-_]{10,}\\b"),
            rule("private-key-block", "-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            rule("gitlab-token", "\\bglpat-[A-Za-z0-9\\-_]{20,}\\b"),
            rule("npm-token", "\\bnpm_[A-Za-z0-9]{36}\\b"),
            rule("sendgrid-key", "\\bSG\\.[A-Za-z0-9\\-_]{22}\\.[A-Za-z0-9\\-_]{43}\\b"),
            rule("twilio-sid", "\\bSK[0-9a-fA-F]{32}\\b"));

    private static BlockRule rule(String name, String regex) {
        return new BlockRule(Pattern.compile(regex), name);
    }

    private static final Pattern ASSIGNMENT_CANDIDATE = Pattern.compile(
            "(?i)(password|passwd|secret|api[_-]?key|access[_-]?token|auth[_-]?token|private[_-]?key|client[_-]?secret)"
                    + "\\s*[:=]\\s*[\"']?([^\"'\\s]{20,200})[\"']?");

    private static final Pattern BASE64ISH = Pattern.compile("^[A-Za-z0-9+/=_\\-.]{20,200}$");

    private static final List<Pattern> PII_PATTERNS = List.of(
            Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"),
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
            Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b"));

    public enum Verdict {
        CLEAN,
        REDACTED,
        BLOCKED
    }

    public record ScanResult(Verdict verdict, String text, String reason) {}

    /**
     * Scans text and returns a verdict with safe-to-send text.
     *
     * @param text the diff or message text; {@code null} is treated as clean-empty
     * @return scan result, never {@code null}; failures fail closed to BLOCKED
     */
    public ScanResult scan(String text) {
        try {
            if (text == null || text.isEmpty()) {
                return new ScanResult(Verdict.CLEAN, text == null ? "" : text, null);
            }
            for (BlockRule blockRule : BLOCK_RULES) {
                if (blockRule.pattern().matcher(text).find()) {
                    log.warn("Secret scan BLOCKED (rule={})", blockRule.name());
                    return new ScanResult(Verdict.BLOCKED, "",
                            "Blocked: suspected " + blockRule.name() + " in diff. Remove the secret and retry.");
                }
            }
            String redacted = redactGenericHighEntropy(text);
            if (!redacted.equals(text)) {
                log.warn("Secret scan redacted generic high-entropy token(s)");
                return new ScanResult(Verdict.REDACTED, redacted, "Redacted suspected generic secret(s)");
            }
            warnOnPii(text);
            return new ScanResult(Verdict.CLEAN, text, null);
        } catch (RuntimeException e) {
            log.warn("Secret scan failed closed: {}", e.getMessage());
            return new ScanResult(Verdict.BLOCKED, "", "Blocked: secret scan unavailable, failing closed.");
        }
    }

    private String redactGenericHighEntropy(String text) {
        Matcher matcher = ASSIGNMENT_CANDIDATE.matcher(text);
        StringBuilder out = new StringBuilder();
        boolean changed = false;
        while (matcher.find()) {
            String candidate = matcher.group(2);
            if (BASE64ISH.matcher(candidate).matches() && shannonEntropy(candidate) >= GENERIC_ENTROPY_THRESHOLD) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(
                        matcher.group(0).replace(candidate, REDACTED)));
                changed = true;
            }
        }
        if (!changed) {
            return text;
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private void warnOnPii(String text) {
        for (Pattern pii : PII_PATTERNS) {
            if (pii.matcher(text).find()) {
                log.warn("Secret scan: PII-shaped content detected (warn only)");
                return;
            }
        }
    }

    static double shannonEntropy(String value) {
        int[] freq = new int[256];
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (byte b : bytes) {
            freq[b & 0xFF]++;
        }
        double entropy = 0.0;
        for (int f : freq) {
            if (f == 0) {
                continue;
            }
            double p = (double) f / bytes.length;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    /** Returns redaction discoveries for metrics without exposing values. */
    public List<String> describe(String text) {
        List<String> hits = new ArrayList<>();
        if (text == null) {
            return hits;
        }
        for (BlockRule blockRule : BLOCK_RULES) {
            if (blockRule.pattern().matcher(text).find()) {
                hits.add(blockRule.name());
            }
        }
        return hits;
    }
}
