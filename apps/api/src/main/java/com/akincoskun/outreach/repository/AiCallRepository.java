package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.AiCall;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface AiCallRepository extends JpaRepository<AiCall, UUID> {

    Page<AiCall> findAllByAgentNameOrderByCreatedAtDesc(String agentName, Pageable pageable);

    @Query("SELECT COALESCE(SUM(a.inputTokens + a.outputTokens), 0) FROM AiCall a WHERE a.success = true")
    long sumTotalTokensSuccessful();
}
