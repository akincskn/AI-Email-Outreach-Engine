package com.akincoskun.outreach.controller;

import com.akincoskun.outreach.dto.AnalyticsSummaryResponse;
import com.akincoskun.outreach.repository.EmailOpenRepository;
import com.akincoskun.outreach.repository.EmailReplyRepository;
import com.akincoskun.outreach.repository.EmailSendRepository;
import com.akincoskun.outreach.repository.EmailDraftRepository;
import com.akincoskun.outreach.domain.DraftStatus;
import com.akincoskun.outreach.service.email.VolumeLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final EmailSendRepository emailSendRepository;
    private final EmailOpenRepository emailOpenRepository;
    private final EmailReplyRepository emailReplyRepository;
    private final EmailDraftRepository emailDraftRepository;
    private final VolumeLimiterService volumeLimiterService;

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryResponse> summary() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant endOfDay = startOfDay.plus(1, ChronoUnit.DAYS);

        long sentToday     = emailSendRepository.countSentBetween(startOfDay, endOfDay);
        long bouncedToday  = emailSendRepository.countBouncedSince(startOfDay);
        long openedToday   = emailOpenRepository.countByOpenedAtBetween(startOfDay, endOfDay);
        long repliedToday  = emailReplyRepository.countByHandledFalse();
        long pendingDrafts = emailDraftRepository.countByStatus(DraftStatus.PENDING);
        long unhandledReplies = emailReplyRepository.countByHandledFalse();

        return ResponseEntity.ok(new AnalyticsSummaryResponse(
            sentToday,
            openedToday,
            repliedToday,
            bouncedToday,
            pendingDrafts,
            unhandledReplies,
            volumeLimiterService.getDailyCap(),
            volumeLimiterService.getSentCountToday()
        ));
    }
}
