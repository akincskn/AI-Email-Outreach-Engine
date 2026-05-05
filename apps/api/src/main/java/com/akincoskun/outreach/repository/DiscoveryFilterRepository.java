package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.DiscoveryFilter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DiscoveryFilterRepository extends JpaRepository<DiscoveryFilter, UUID> {

    List<DiscoveryFilter> findAllByActiveTrue();
}
