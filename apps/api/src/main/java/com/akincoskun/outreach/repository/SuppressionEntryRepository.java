package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.SuppressionEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SuppressionEntryRepository extends JpaRepository<SuppressionEntry, UUID> {

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM SuppressionEntry s " +
           "WHERE s.email = :email AND (s.expiresAt IS NULL OR s.expiresAt > :now)")
    boolean isActive(String email, Instant now);

    Optional<SuppressionEntry> findByEmail(String email);

    Page<SuppressionEntry> findAllByOrderBySuppressedAtDesc(Pageable pageable);
}
