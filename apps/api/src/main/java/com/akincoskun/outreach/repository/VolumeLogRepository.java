package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.VolumeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface VolumeLogRepository extends JpaRepository<VolumeLog, UUID> {

    Optional<VolumeLog> findBySentDate(LocalDate date);

    /**
     * Atomically create today's row if it does not exist. Uses PostgreSQL
     * {@code ON CONFLICT DO NOTHING} so concurrent callers cannot trip the
     * {@code volume_log_sent_date_key} unique constraint (the find-or-create
     * race that broke 2026-06-26). The {@code daily_cap} is only applied on
     * insert; an existing row keeps its already-computed cap.
     */
    @Modifying
    @Query(value = """
        INSERT INTO volume_log (sent_date, sent_count, daily_cap)
        VALUES (:date, 0, :cap)
        ON CONFLICT (sent_date) DO NOTHING
        """, nativeQuery = true)
    void ensureExists(@Param("date") LocalDate date, @Param("cap") int cap);

    /**
     * Atomically increment today's sent count, creating the row on first send.
     * The {@code ON CONFLICT DO UPDATE} makes the increment a single SQL
     * statement, so parallel sends cannot lose updates or duplicate-key.
     * {@code daily_cap} is only used when the row is first inserted.
     */
    @Modifying
    @Query(value = """
        INSERT INTO volume_log (sent_date, sent_count, daily_cap)
        VALUES (:date, 1, :cap)
        ON CONFLICT (sent_date)
        DO UPDATE SET sent_count = volume_log.sent_count + 1, updated_at = NOW()
        """, nativeQuery = true)
    void incrementSentCount(@Param("date") LocalDate date, @Param("cap") int cap);
}
