package com.akincoskun.outreach.service.email;

import com.akincoskun.outreach.domain.EmailSend;
import com.akincoskun.outreach.domain.SuppressionEntry;
import com.akincoskun.outreach.repository.SuppressionEntryRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SuppressionService {

    private final SuppressionEntryRepository suppressionRepo;

    // Cache: email → suppressed(true/false). 5 min TTL.
    private final Cache<String, Boolean> cache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build();

    public SuppressionService(SuppressionEntryRepository suppressionRepo) {
        this.suppressionRepo = suppressionRepo;
    }

    public boolean isSuppressed(String email) {
        return cache.get(email.toLowerCase(), k ->
            suppressionRepo.isActive(k, Instant.now())
        );
    }

    @Transactional
    public SuppressionEntry suppress(String email, String reason, EmailSend sourceSend) {
        String normalized = email.toLowerCase();
        cache.invalidate(normalized);

        return suppressionRepo.findByEmail(normalized)
            .orElseGet(() -> {
                SuppressionEntry entry = SuppressionEntry.builder()
                    .email(normalized)
                    .reason(reason)
                    .sourceSend(sourceSend)
                    .suppressedAt(Instant.now())
                    .build();
                log.info("Suppressing email={} reason={}", normalized, reason);
                return suppressionRepo.save(entry);
            });
    }

    @Transactional
    public void unsuppress(String email) {
        String normalized = email.toLowerCase();
        cache.invalidate(normalized);
        suppressionRepo.findByEmail(normalized).ifPresent(suppressionRepo::delete);
        log.info("Unsuppressed email={}", normalized);
    }
}
