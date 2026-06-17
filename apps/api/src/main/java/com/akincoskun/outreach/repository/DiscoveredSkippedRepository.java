package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.DiscoveredSkipped;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DiscoveredSkippedRepository extends JpaRepository<DiscoveredSkipped, UUID> {

    boolean existsByOsmId(String osmId);
}
