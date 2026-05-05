package com.akincoskun.outreach.service.email;

import com.akincoskun.outreach.domain.VolumeLog;
import com.akincoskun.outreach.repository.VolumeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class VolumeLimiterService {

    private final VolumeLogRepository volumeLogRepository;

    @Value("${app.gmail.account-created-at}")
    private String accountCreatedAt;

    public boolean canSendNow() {
        VolumeLog today = getOrCreateToday();
        int cap = today.getDailyCap();
        if (cap == 0) {
            log.warn("Volume cap is 0 — account still in warming phase");
            return false;
        }
        return today.getSentCount() < cap;
    }

    @Transactional
    public void recordSend() {
        VolumeLog today = getOrCreateToday();
        today.setSentCount(today.getSentCount() + 1);
        volumeLogRepository.save(today);
    }

    public int getDailyCap() {
        return getOrCreateToday().getDailyCap();
    }

    public int getSentCountToday() {
        return getOrCreateToday().getSentCount();
    }

    private VolumeLog getOrCreateToday() {
        LocalDate today = LocalDate.now();
        return volumeLogRepository.findBySentDate(today)
            .orElseGet(() -> {
                int cap = computeCap();
                VolumeLog log = VolumeLog.builder()
                    .sentDate(today)
                    .sentCount(0)
                    .dailyCap(cap)
                    .build();
                return volumeLogRepository.save(log);
            });
    }

    int computeCap() {
        if (accountCreatedAt == null || accountCreatedAt.isBlank()
                || accountCreatedAt.startsWith("YOUR_")) {
            return 0;
        }
        try {
            LocalDate created = LocalDate.parse(accountCreatedAt);
            long weeks = ChronoUnit.WEEKS.between(created, LocalDate.now());

            if (weeks < 2)  return 0;   // warming
            if (weeks < 4)  return 5;
            if (weeks < 6)  return 10;
            if (weeks < 8)  return 20;
            return 50;
        } catch (Exception e) {
            log.error("Invalid GMAIL_ACCOUNT_CREATED_AT: {}", accountCreatedAt);
            return 0;
        }
    }
}
