package com.akincoskun.outreach.controller;

import com.akincoskun.outreach.domain.EmailOpen;
import com.akincoskun.outreach.repository.EmailOpenRepository;
import com.akincoskun.outreach.repository.EmailSendRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/track")
@RequiredArgsConstructor
@Slf4j
public class TrackingController {

    private static final byte[] PIXEL_GIF = {
        0x47,0x49,0x46,0x38,0x39,0x61,0x01,0x00,0x01,0x00,(byte)0x80,0x00,0x00,
        (byte)0xff,(byte)0xff,(byte)0xff,0x00,0x00,0x00,0x21,(byte)0xf9,0x04,
        0x01,0x00,0x00,0x00,0x00,0x2c,0x00,0x00,0x00,0x00,0x01,0x00,0x01,0x00,
        0x00,0x02,0x02,0x44,0x01,0x00,0x3b
    };

    private final EmailSendRepository emailSendRepository;
    private final EmailOpenRepository emailOpenRepository;

    @GetMapping("/open")
    @Transactional
    public ResponseEntity<byte[]> trackOpen(
            @RequestParam String t,
            @RequestHeader(value = "User-Agent", defaultValue = "") String userAgent) {

        emailSendRepository.findByTrackingPixelToken(t).ifPresent(send -> {
            if (!emailOpenRepository.existsBySendId(send.getId())) {
                EmailOpen open = EmailOpen.builder()
                    .send(send)
                    .openedAt(Instant.now())
                    .userAgent(userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent)
                    .build();
                emailOpenRepository.save(open);
                log.info("Email opened: sendId={}", send.getId());
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "image/gif");
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        return new ResponseEntity<>(PIXEL_GIF, headers, HttpStatus.OK);
    }
}
