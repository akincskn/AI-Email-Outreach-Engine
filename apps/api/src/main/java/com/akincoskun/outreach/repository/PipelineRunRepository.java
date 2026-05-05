package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.PipelineRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {

    Page<PipelineRun> findAllByWorkflowNameOrderByStartedAtDesc(String workflowName, Pageable pageable);
}
