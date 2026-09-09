package io.github.kxng0109.aiprcopilot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Deny-by-default security.
 *
 * <ul>
 *   <li>Public: {@code /actuator/health}, {@code /actuator/info}, {@code /api-docs/**},
 *       {@code /swagger-ui/**}.</li>
 *   <li>{@code prod} mode: {@code /api/v1/**} requires OIDC JWT; startup fails closed
 *       when no issuer is configured.</li>
 *   <li>{@code selfhost} mode: {@code /api/v1/**} requires the pre-shared API key;
 *       empty key keeps endpoints locked (401).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@Slf4j
@RequiredArgsConstructor
public class SecurityConfig {

    private final PrCopilotAuthProperties authProperties;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String mode = authProperties.getMode() == null ? "selfhost" : authProperties.getMode().trim();

        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.csrf(AbstractHttpConfigurer::disable);
        http.cors(Customizer.withDefaults());

        http.authorizeHttpRequests(auth -> auth.requestMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/api-docs/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                .permitAll()
                .requestMatchers("/api/v1/**")
                .authenticated()
                .anyRequest()
                .denyAll());

        if ("prod".equalsIgnoreCase(mode)) {
            if (issuerUri == null || issuerUri.isBlank()) {
                throw new IllegalStateException(
                        "prcopilot.auth.mode=prod requires spring.security.oauth2.resourceserver.jwt.issuer-uri");
            }
            http.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
            log.info("Auth mode=prod (OIDC JWT required for /api/v1/**)");
        } else {
            if (authProperties.getApiKey() == null || authProperties.getApiKey().isBlank()) {
                log.warn("Auth mode=selfhost with empty PRCOPILOT_API_KEY: /api/v1/** stays locked (401)");
            }
            http.addFilterBefore(
                    new ApiKeyAuthFilter(authProperties.getApiKey(), authProperties.getApiKeyHeader()),
                    UsernamePasswordAuthenticationFilter.class);
            log.info("Auth mode=selfhost (pre-shared API key for /api/v1/**)");
        }

        return http.build();
    }
}
