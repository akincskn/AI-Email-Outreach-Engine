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

export async function runPipeline(filterId: string): Promise<PipelineRunResult> {
  // Synchronous: the backend runs the whole discovery→write pipeline and only
  // responds when done (can take 30-90s). Görev 10.2 (async) is the fallback if
  // this ever hits a proxy/timeout limit in production.
  const result = await api.post<PipelineRunResult>(
    `/api/v1/pipeline/run/${filterId}`,
    {}
  );
  revalidatePath("/drafts");
  revalidatePath("/companies");
  return result;
}

export async function runAllPipelines(): Promise<RunAllResult> {
  // Runs every active filter back-to-back on the backend (each quota-capped).
  // Can take several minutes; the daily quota keeps the total draft count bounded.
  const result = await api.post<RunAllResult>("/api/v1/pipeline/run-all", {});
  revalidatePath("/drafts");
  revalidatePath("/companies");
  return result;
}
