package io.github.kxng0109.aiprcopilot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Pre-shared-key authentication for {@code selfhost} mode.
 *
 * <p>Accepts {@code Authorization: Bearer <key>} or the raw key value in the
 * configured header. Uses constant-time comparison. Missing/invalid keys leave
 * the request unauthenticated so the filter chain returns 401/403.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final String configuredKey;
    private final String headerName;

    public ApiKeyAuthFilter(String configuredKey, String headerName) {
        this.configuredKey = configuredKey == null ? "" : configuredKey;
        this.headerName = headerName == null || headerName.isBlank() ? "Authorization" : headerName;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (configuredKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(headerName);
        String presented = header == null
                ? null
                : (header.regionMatches(true, 0, "Bearer ", 0, 7) ? header.substring(7).trim() : header.trim());

        if (presented != null && constantTimeEquals(presented, configuredKey)) {
            var auth = new UsernamePasswordAuthenticationToken(
                    "selfhost", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ab, bb);
    }
}
