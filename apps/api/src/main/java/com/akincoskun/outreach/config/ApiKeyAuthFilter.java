package com.akincoskun.outreach.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Görev 8.1 — Bearer token auth for the API/agent surface. A request must carry
 * "Authorization: Bearer &lt;api-key&gt;" matching {@code app.security.api-key}.
 * Public endpoints (health, unsubscribe, tracking pixel, swagger) are skipped via
 * {@link #shouldNotFilter} so they stay reachable without a key.
 *
 * <p>Not a Spring bean on purpose: it is wired into the Spring Security chain by
 * {@link SecurityConfig}, which avoids Boot auto-registering it twice.</p>
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String[] PUBLIC_PREFIXES = {
        "/actuator", "/swagger-ui", "/api-docs", "/unsubscribe", "/api/v1/track"
    };

    private final byte[] expectedKey;

    public ApiKeyAuthFilter(String apiKey) {
        this.expectedKey = apiKey == null ? new byte[0] : apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer "))
            ? header.substring(7).strip()
            : null;

        if (token == null || !constantTimeEquals(token.getBytes(StandardCharsets.UTF_8), expectedKey)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
            "api-client", null, List.of(new SimpleGrantedAuthority("ROLE_API")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    /** Length-aware constant-time comparison (MessageDigest.isEqual) to blunt timing attacks. */
    private boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }
}
