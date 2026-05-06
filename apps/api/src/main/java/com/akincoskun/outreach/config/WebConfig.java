package com.akincoskun.outreach.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    @Value("${app.rate-limit.requests-per-minute:100}")
    private int requestsPerMinute;

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter() {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(requestsPerMinute));
        registration.addUrlPatterns("/api/*", "/agent/*", "/unsubscribe");
        registration.setOrder(1);
        return registration;
    }
}
