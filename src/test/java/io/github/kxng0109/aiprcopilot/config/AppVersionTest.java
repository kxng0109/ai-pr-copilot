package io.github.kxng0109.aiprcopilot.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the single-source versioning rule: {@link AppInfo#VERSION} must match
 * {@code pom.xml} {@code project/version}. Bump both together on release.
 */
class AppVersionTest {

    @Test
    void appInfoVersion_shouldMatchPomVersion() throws Exception {
        Path pom = Path.of("pom.xml");
        String content = Files.readString(pom);
        Matcher matcher = Pattern.compile(
                "<artifactId>ai-pr-copilot</artifactId>\\s*<version>([^<]+)</version>").matcher(content);

        assertThat(matcher.find()).isTrue();
        assertThat(AppInfo.VERSION).isEqualTo(matcher.group(1).trim());
        assertThat(AppInfo.NAME).isEqualTo("ai-pr-copilot");
    }
}
