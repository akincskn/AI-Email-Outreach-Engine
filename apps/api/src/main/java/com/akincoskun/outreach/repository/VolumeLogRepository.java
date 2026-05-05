package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.VolumeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface VolumeLogRepository extends JpaRepository<VolumeLog, UUID> {

    Optional<VolumeLog> findBySentDate(LocalDate date);
}
