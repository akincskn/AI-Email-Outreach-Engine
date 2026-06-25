package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.PipelineJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PipelineJobRepository extends JpaRepository<PipelineJob, UUID> {

    /** Most recent jobs first, for the dashboard history list (Görev 10.2). */
    List<PipelineJob> findTop20ByOrderByCreatedAtDesc();
}
