"use server";

import { api } from "@/lib/api";
import { revalidatePath } from "next/cache";

export interface PipelineRunResult {
  filterId: string;
  filterName: string;
  discovered: number;
  skippedNoWebsite: number;
  alreadyExists: number;
  newCompanies: number;
  skippedNoEmail: number;
  skippedNotTarget: number;
  skippedNoMatch: number;
  draftsCreated: number;
  errors: number;
  durationMs: number;
  quotaReached: boolean;
  error: string | null;
}

export interface RunAllResult {
  totalFilters: number;
  totalDiscovered: number;
  totalDrafts: number;
  totalQuotaReached: number;
  totalErrors: number;
  durationMs: number;
  perFilter: PipelineRunResult[];
}

export type JobStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";

export interface JobStatusResponse {
  id: string;
  jobType: string;
  status: JobStatus;
  startedAt: string;
  completedAt: string | null;
  progressMessage: string | null;
  result: RunAllResult | null;
  error: string | null;
}

export async function runPipeline(filterId: string): Promise<PipelineRunResult> {
  // Synchronous: a single filter runs in 30-90s, comfortably under the fetch
  // timeout. The slow path is "Run All" (5-13 min) → that goes async below.
  const result = await api.post<PipelineRunResult>(
    `/api/v1/pipeline/run/${filterId}`,
    {}
  );
  revalidatePath("/drafts");
  revalidatePath("/companies");
  return result;
}

/**
 * Görev 10.2 — starts a "Run All" as a background job and returns its id
 * immediately (202). A "Run All" takes 5-13 minutes, far over Next.js's 60s
 * server-action fetch timeout, so the client polls {@link getJobStatus} instead
 * of blocking on one long request.
 */
export async function startRunAllJob(): Promise<string> {
  const { jobId } = await api.post<{ jobId: string }>(
    "/api/v1/pipeline/run-all-async",
    {}
  );
  return jobId;
}

/** Görev 10.2 — poll one job's status/progress/result for the runner UI. */
export async function getJobStatus(jobId: string): Promise<JobStatusResponse> {
  const job = await api.get<JobStatusResponse>(`/api/v1/pipeline/jobs/${jobId}`);
  // Refresh draft/company lists once the run finishes so the dashboard reflects
  // whatever the background job produced.
  if (job.status === "COMPLETED") {
    revalidatePath("/drafts");
    revalidatePath("/companies");
  }
  return job;
}
