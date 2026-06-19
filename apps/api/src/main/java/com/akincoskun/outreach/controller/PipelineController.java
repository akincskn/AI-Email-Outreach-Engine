package com.akincoskun.outreach.controller;

import com.akincoskun.outreach.dto.PipelineRunResult;
import com.akincoskun.outreach.service.PipelineOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Görev 10 — dashboard pipeline trigger. Replaces the N8N workflow: Akın hits
 * "Run Pipeline" on a discovery filter and the backend runs the full pipeline
 * synchronously, returning a summary.
 */
@RestController
@RequestMapping("/api/v1/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineOrchestratorService pipelineOrchestratorService;

    @PostMapping("/run/{filterId}")
    public ResponseEntity<PipelineRunResult> runPipeline(@PathVariable UUID filterId) {
        return ResponseEntity.ok(pipelineOrchestratorService.runForFilter(filterId));
    }
}
