package com.akincoskun.outreach.service;

import com.akincoskun.outreach.domain.JobStatus;
import com.akincoskun.outreach.domain.JobType;
import com.akincoskun.outreach.domain.PipelineJob;
import com.akincoskun.outreach.dto.JobStatusResponse;
import com.akincoskun.outreach.dto.RunAllResult;
import com.akincoskun.outreach.exception.ResourceNotFoundException;
import com.akincoskun.outreach.repository.PipelineJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineJobServiceTest {

    @Mock PipelineJobRepository jobRepository;
    @Captor ArgumentCaptor<PipelineJob> jobCaptor;

    private PipelineJobService service() {
        return new PipelineJobService(jobRepository);
    }

    @Test
    void createJob_insertsPendingJobAndReturnsId() {
        when(jobRepository.save(any(PipelineJob.class))).thenAnswer(i -> i.getArgument(0));

        UUID id = service().createJob(JobType.RUN_ALL);

        assertThat(id).isNotNull();
        org.mockito.Mockito.verify(jobRepository).save(jobCaptor.capture());
        PipelineJob saved = jobCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(saved.getJobType()).isEqualTo(JobType.RUN_ALL);
        assertThat(saved.getStartedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getId()).isEqualTo(id);
    }

    @Test
    void markRunning_flipsStatusAndSetsMessage() {
        PipelineJob job = pending();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        service().markRunning(job.getId(), "Başlatılıyor…");

        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getProgressMessage()).isEqualTo("Başlatılıyor…");
    }

    @Test
    void updateProgress_setsProgressMessage() {
        PipelineJob job = pending();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        service().updateProgress(job.getId(), "Pipeline 3/5: TR Marketing");

        assertThat(job.getProgressMessage()).isEqualTo("Pipeline 3/5: TR Marketing");
    }

    @Test
    void markCompleted_storesResultAndCompletedAt() {
        PipelineJob job = pending();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        RunAllResult result = new RunAllResult(5, 40, 12, 1, 0, 1000L, List.of());

        service().markCompleted(job.getId(), result);

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getResultJson()).isSameAs(result);
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void markFailed_storesErrorAndCompletedAt() {
        PipelineJob job = pending();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        service().markFailed(job.getId(), "OSM down");

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("OSM down");
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void getJob_mapsToResponse() {
        PipelineJob job = pending();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        JobStatusResponse response = service().getJob(job.getId());

        assertThat(response.id()).isEqualTo(job.getId());
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.jobType()).isEqualTo("RUN_ALL");
    }

    @Test
    void getJob_unknownId_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getJob(id))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    private PipelineJob pending() {
        return PipelineJob.builder()
            .id(UUID.randomUUID())
            .jobType(JobType.RUN_ALL)
            .status(JobStatus.PENDING)
            .startedAt(java.time.Instant.now())
            .createdAt(java.time.Instant.now())
            .build();
    }
}
