package com.akincoskun.outreach.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(3); // 3 req/min for fast tests
        chain = mock(FilterChain.class);
    }

    @Test
    void requestsWithinCapPassThrough() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = requestFrom("10.0.0.1", "/api/v1/drafts");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isNotEqualTo(429);
        }
        verify(chain, times(3)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requestsBeyondCapReturn429() throws Exception {
        for (int i = 0; i < 3; i++) {
            filter.doFilter(requestFrom("10.0.0.2", "/api/v1/drafts"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse over = new MockHttpServletResponse();
        filter.doFilter(requestFrom("10.0.0.2", "/api/v1/drafts"), over, chain);

        assertThat(over.getStatus()).isEqualTo(429);
        assertThat(over.getContentAsString()).contains("Rate limit exceeded");
    }

    @Test
    void differentIpsHaveIndependentBuckets() throws Exception {
        for (int i = 0; i < 3; i++) {
            filter.doFilter(requestFrom("10.0.0.3", "/api/v1/drafts"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse freshIp = new MockHttpServletResponse();
        filter.doFilter(requestFrom("10.0.0.4", "/api/v1/drafts"), freshIp, chain);

        assertThat(freshIp.getStatus()).isNotEqualTo(429);
    }

    @Test
    void actuatorPathIsExcluded() throws Exception {
        for (int i = 0; i < 100; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(requestFrom("10.0.0.5", "/actuator/health"), res, chain);
            assertThat(res.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void xForwardedForHeaderUsedAsClientIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = requestFrom("proxy-ip", "/api/v1/companies");
            req.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.6");
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }
        MockHttpServletRequest req = requestFrom("proxy-ip", "/api/v1/companies");
        req.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.6");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
    }

    private MockHttpServletRequest requestFrom(String remoteAddr, String path) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        req.setServletPath(path);
        req.setRemoteAddr(remoteAddr);
        return req;
    }
}
