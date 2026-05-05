package com.akincoskun.outreach.mapper;

import com.akincoskun.outreach.domain.EmailSend;
import com.akincoskun.outreach.dto.EmailSendResponse;
import com.akincoskun.outreach.repository.EmailOpenRepository;
import com.akincoskun.outreach.repository.EmailReplyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailSendMapper {

    private final EmailOpenRepository emailOpenRepository;
    private final EmailReplyRepository emailReplyRepository;

    public EmailSendResponse toResponse(EmailSend s) {
        return new EmailSendResponse(
            s.getId(),
            s.getDraft() != null ? s.getDraft().getId() : null,
            s.getCompany() != null ? s.getCompany().getId() : null,
            s.getCompany() != null ? s.getCompany().getName() : null,
            s.getToEmail(),
            s.getSubject(),
            s.getStatus().name(),
            s.getRetryCount(),
            s.getQueuedAt(),
            s.getSentAt(),
            s.getFailedAt(),
            emailOpenRepository.existsBySendId(s.getId()),
            !emailReplyRepository.findAllBySendId(s.getId()).isEmpty()
        );
    }
}
