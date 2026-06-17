package com.akincoskun.outreach.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApiKeyAuthFilterTest {

    private static final String KEY = "local-dev-api-key-min-32-chars-please-change";

    private ApiKeyAuthFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter(KEY);
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthHeaderReturns401() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req("/api/v1/companies"), res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Unauthorized");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void wrongTokenReturns401() throws Exception {
        MockHttpServletRequest req = req("/agent/write");
        req.addHeader("Authorization", "Bearer wrong-token");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void correctTokenPassesThroughAndAuthenticates() throws Exception {
        MockHttpServletRequest req = req("/api/v1/companies");
        req.addHeader("Authorization", "Bearer " + KEY);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verify(chain, times(1)).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("api-client");
    }

    @Test
    void publicPathsBypassAuth() throws Exception {
        for (String path : new String[]{"/actuator/health", "/unsubscribe", "/api/v1/track/open", "/swagger-ui/index.html"}) {
            FilterChain localChain = mock(FilterChain.class);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req(path), res, localChain); // no Authorization header
            assertThat(res.getStatus()).as(path).isEqualTo(200);
            verify(localChain, times(1)).doFilter(any(), any());
        }
    }

    private MockHttpServletRequest req(String path) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", path);
        r.setServletPath(path);
        return r;
    }
}
