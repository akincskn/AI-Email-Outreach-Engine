package com.akincoskun.outreach.service;

import com.akincoskun.outreach.dto.RunAllResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncPipelineRunnerTest {

    @Mock PipelineOrchestratorService orchestrator;
    @Mock PipelineJobService jobService;

    private AsyncPipelineRunner runner() {
        return new AsyncPipelineRunner(orchestrator, jobService);
    }

    @Test
    void runAll_marksRunningThenCompleted() {
        UUID jobId = UUID.randomUUID();
        RunAllResult result = new RunAllResult(5, 40, 12, 1, 0, 1000L, List.of());
        when(orchestrator.runAllActive(any())).thenReturn(result);

        runner().runAll(jobId);

        verify(jobService).markRunning(eq(jobId), any());
        verify(jobService).markCompleted(jobId, result);
        verify(jobService, never()).markFailed(any(), any());
    }

    @Test
    void runAll_orchestratorThrows_marksFailed() {
        UUID jobId = UUID.randomUUID();
        when(orchestrator.runAllActive(any())).thenThrow(new RuntimeException("boom"));

        runner().runAll(jobId);

        verify(jobService).markFailed(jobId, "boom");
        verify(jobService, never()).markCompleted(any(), any());
    }
}
