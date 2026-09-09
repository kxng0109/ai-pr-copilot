package io.github.kxng0109.aiprcopilot.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.kxng0109.aiprcopilot.api.dto.AnalyzeDiffResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Diff-hash response cache (content-addressed, bounded, TTL).
 */
@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    private final PrCopilotAnalysisProperties analysisProperties;

    @Bean
    public Cache<String, AnalyzeDiffResponse> analysisCache() {
        return Caffeine.newBuilder()
                .maximumSize(Math.max(1, analysisProperties.getCacheMaxSize()))
                .expireAfterWrite(analysisProperties.getCacheTtl())
                .recordStats()
                .build();
    }
}
