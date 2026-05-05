package com.akincoskun.outreach.service.email;

import com.akincoskun.outreach.domain.VolumeLog;
import com.akincoskun.outreach.repository.VolumeLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VolumeLimiterServiceTest {

    @Mock VolumeLogRepository repo;
    @InjectMocks VolumeLimiterService service;

    private void setCreatedAt(String date) {
        ReflectionTestUtils.setField(service, "accountCreatedAt", date);
    }

    private VolumeLog logWith(int sent, int cap) {
        VolumeLog v = new VolumeLog();
        ReflectionTestUtils.setField(v, "sentDate", LocalDate.now());
        ReflectionTestUtils.setField(v, "sentCount", sent);
        ReflectionTestUtils.setField(v, "dailyCap", cap);
        return v;
    }

    @Test
    void computesCapForWeek1() {
        setCreatedAt(LocalDate.now().minusWeeks(1).toString());
        assertThat(service.computeCap()).isEqualTo(0);
    }

    @Test
    void computesCapForWeek3() {
        setCreatedAt(LocalDate.now().minusWeeks(3).toString());
        assertThat(service.computeCap()).isEqualTo(5);
    }

    @Test
    void computesCapForWeek7() {
        setCreatedAt(LocalDate.now().minusWeeks(7).toString());
        assertThat(service.computeCap()).isEqualTo(20);
    }

    @Test
    void computesCapForWeek8Plus() {
        setCreatedAt(LocalDate.now().minusWeeks(10).toString());
        assertThat(service.computeCap()).isEqualTo(50);
    }

    @Test
    void cannotSendWhenCapZero() {
        setCreatedAt(LocalDate.now().minusWeeks(1).toString());
        when(repo.findBySentDate(any())).thenReturn(Optional.of(logWith(0, 0)));
        assertThat(service.canSendNow()).isFalse();
    }

    @Test
    void canSendWhenBelowCap() {
        setCreatedAt(LocalDate.now().minusWeeks(4).toString());
        when(repo.findBySentDate(any())).thenReturn(Optional.of(logWith(3, 10)));
        assertThat(service.canSendNow()).isTrue();
    }

    @Test
    void cannotSendWhenAtCap() {
        setCreatedAt(LocalDate.now().minusWeeks(4).toString());
        when(repo.findBySentDate(any())).thenReturn(Optional.of(logWith(10, 10)));
        assertThat(service.canSendNow()).isFalse();
    }
}
