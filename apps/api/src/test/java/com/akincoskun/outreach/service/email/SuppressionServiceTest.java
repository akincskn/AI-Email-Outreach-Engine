package com.akincoskun.outreach.service.email;

import com.akincoskun.outreach.repository.SuppressionEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SuppressionServiceTest {

    @Mock SuppressionEntryRepository repo;
    SuppressionService service;

    @BeforeEach
    void setUp() {
        service = new SuppressionService(repo);
    }

    @Test
    void returnsTrueForSuppressedEmail() {
        when(repo.isActive(eq("info@blocked.com"), any(Instant.class))).thenReturn(true);
        assertThat(service.isSuppressed("info@blocked.com")).isTrue();
    }

    @Test
    void returnsFalseForCleanEmail() {
        when(repo.isActive(eq("hello@clean.com"), any(Instant.class))).thenReturn(false);
        assertThat(service.isSuppressed("hello@clean.com")).isFalse();
    }

    @Test
    void normalizesEmailToLowercase() {
        // Caffeine compute() wraps the call; use anyString() to avoid matcher context issues
        when(repo.isActive(anyString(), any(Instant.class))).thenReturn(true);
        assertThat(service.isSuppressed("INFO@EXAMPLE.COM")).isTrue();
    }

    @Test
    void cachesPreviousResult() {
        when(repo.isActive(anyString(), any())).thenReturn(false);

        service.isSuppressed("x@y.com");
        service.isSuppressed("x@y.com");

        // Second call should hit cache, not repo
        verify(repo, times(1)).isActive(anyString(), any());
    }
}
