"use client";

import { useEffect, useRef, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { toast } from "sonner";
import { Loader2, Play, Rocket } from "lucide-react";
import type { DiscoveryFilter } from "@/lib/types";
import {
  runPipeline,
  startRunAllJob,
  getJobStatus,
  type PipelineRunResult,
  type RunAllResult,
} from "./actions";

// Poll the async "Run All" job every 5s. A run takes 5-13 min, so a slower
// cadence keeps server-action traffic low without making progress feel stale.
const POLL_INTERVAL_MS = 5000;

export function PipelineRunner({ filters }: { filters: DiscoveryFilter[] }) {
  const router = useRouter();
  const [isPending, startTransition] = useTransition();
  const [runningId, setRunningId] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<PipelineRunResult | null>(null);

  // Async "Run All" job state.
  const [jobId, setJobId] = useState<string | null>(null);
  const [progress, setProgress] = useState<string | null>(null);
  const [runAllResult, setRunAllResult] = useState<RunAllResult | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Drive polling off jobId: whenever a job is active, poll until it reaches a
  // terminal state, then tear the interval down.
  useEffect(() => {
    if (!jobId) return;

    function stop() {
      if (pollRef.current) {
        clearInterval(pollRef.current);
        pollRef.current = null;
      }
    }

    async function poll() {
      try {
        const job = await getJobStatus(jobId!);
        setProgress(job.progressMessage);

        if (job.status === "COMPLETED" && job.result) {
          stop();
          setRunAllResult(job.result);
          setJobId(null);
          setProgress(null);
          toast.success(
            `${job.result.totalFilters} filtre çalıştı, ${job.result.totalDrafts} taslak oluşturuldu`,
            {
              description:
                job.result.totalDrafts > 0
                  ? "Taslakları incelemek için tıklayın"
                  : undefined,
              action:
                job.result.totalDrafts > 0
                  ? { label: "Taslaklar", onClick: () => router.push("/drafts") }
                  : undefined,
            }
          );
        } else if (job.status === "FAILED") {
          stop();
          setJobId(null);
          setProgress(null);
          toast.error(job.error ?? "Pipeline başarısız oldu");
        }
      } catch (e) {
        stop();
        setJobId(null);
        setProgress(null);
        toast.error(e instanceof Error ? e.message : "Job durumu alınamadı");
      }
    }

    poll(); // immediate first check, then interval
    pollRef.current = setInterval(poll, POLL_INTERVAL_MS);
    return stop;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobId]);

  function handleRun(filterId: string) {
    setRunningId(filterId);
    setLastResult(null);
    setRunAllResult(null);
    startTransition(async () => {
      try {
        const result = await runPipeline(filterId);
        setLastResult(result);
        toast.success(
          `${result.newCompanies} yeni şirket bulundu, ${result.draftsCreated} taslak oluşturuldu`,
          {
            description:
              result.draftsCreated > 0 ? "Taslakları incelemek için tıklayın" : undefined,
            action:
              result.draftsCreated > 0
                ? { label: "Taslaklar", onClick: () => router.push("/drafts") }
                : undefined,
          }
        );
      } catch (e) {
        toast.error(e instanceof Error ? e.message : "Pipeline çalıştırılamadı");
      } finally {
        setRunningId(null);
      }
    });
  }

  async function handleRunAll() {
    setLastResult(null);
    setRunAllResult(null);
    setProgress("Başlatılıyor…");
    try {
      const id = await startRunAllJob();
      setJobId(id); // kicks off the polling effect
    } catch (e) {
      setProgress(null);
      toast.error(e instanceof Error ? e.message : "Pipeline başlatılamadı");
    }
  }

  const runningAll = jobId !== null;

  return (
    <div className="space-y-4">
      <Card className="p-4 flex items-center justify-between gap-4">
        <div className="min-w-0">
          <p className="font-medium text-sm">Tüm aktif filtreleri çalıştır</p>
          <p className="text-xs text-muted-foreground">
            {filters.length} aktif filtre sırayla çalışır. Her filtre günlük kotasıyla
            sınırlıdır. Arka planda çalışır, <strong>5-15 dakika</strong> sürebilir —
            sayfada kalmanıza gerek yok.
          </p>
        </div>
        <Button
          size="lg"
          onClick={handleRunAll}
          disabled={isPending || runningAll}
          className="shrink-0"
        >
          {runningAll ? (
            <>
              <Loader2 className="h-4 w-4 animate-spin" />
              Çalışıyor…
            </>
          ) : (
            <>
              <Rocket className="h-4 w-4" />
              Run All Active Filters
            </>
          )}
        </Button>
      </Card>

      {runningAll && (
        <Card className="p-6 space-y-3 border-primary/40 bg-primary/5">
          <div className="flex items-center gap-3">
            <Loader2 className="h-6 w-6 animate-spin text-primary" />
            <div className="min-w-0">
              <p className="font-semibold text-base">Pipeline çalışıyor…</p>
              <p className="text-sm text-muted-foreground">
                {progress ?? "Başlatılıyor…"}
              </p>
            </div>
          </div>
          <p className="text-xs text-muted-foreground">
            Bu işlem 5-15 dakika sürebilir. İlerleme her 5 saniyede güncellenir.
            Sekmeyi kapatabilirsiniz; iş arka planda devam eder ve sonradan
            “Son işler” listesinde görünür.
          </p>
        </Card>
      )}

      {runAllResult && (
        <Card className="p-4 space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-medium">Toplu çalıştırma sonucu</h2>
            <span className="text-xs text-muted-foreground">
              {(runAllResult.durationMs / 1000).toFixed(1)} sn
            </span>
          </div>
          <div className="grid grid-cols-2 gap-2 text-xs sm:grid-cols-5">
            <Stat label="Filtre" value={runAllResult.totalFilters} />
            <Stat label="Bulundu" value={runAllResult.totalDiscovered} />
            <Stat label="Taslak" value={runAllResult.totalDrafts} highlight />
            <Stat label="Kota doldu" value={runAllResult.totalQuotaReached} />
            <Stat label="Hata" value={runAllResult.totalErrors} warn={runAllResult.totalErrors > 0} />
          </div>
          <div className="space-y-1">
            {runAllResult.perFilter.map((r) => (
              <div
                key={r.filterId}
                className="flex items-center justify-between rounded-md border px-3 py-2 text-xs"
              >
                <span className="font-medium truncate">{r.filterName}</span>
                <span className="text-muted-foreground shrink-0">
                  {r.error
                    ? `Hata: ${r.error}`
                    : r.quotaReached
                      ? "Günlük kota dolu"
                      : `${r.draftsCreated} taslak · ${r.newCompanies} yeni şirket`}
                </span>
              </div>
            ))}
          </div>
          {runAllResult.totalDrafts > 0 && (
            <Button variant="outline" size="sm" onClick={() => router.push("/drafts")}>
              Taslakları İncele
            </Button>
          )}
        </Card>
      )}

      <div className="space-y-2">
        {filters.map((f) => {
          const running = isPending && runningId === f.id;
          return (
            <Card key={f.id} className="p-4 flex items-center justify-between gap-4">
              <div className="min-w-0">
                <p className="font-medium text-sm">{f.name}</p>
                <p className="text-xs text-muted-foreground">
                  {[f.industry, f.city, f.countryCode].filter(Boolean).join(" · ") || "—"}
                </p>
              </div>
              <Button
                size="sm"
                onClick={() => handleRun(f.id)}
                disabled={isPending || runningAll}
                className="shrink-0"
              >
                {running ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Pipeline çalışıyor…
                  </>
                ) : (
                  <>
                    <Play className="h-4 w-4" />
                    Çalıştır
                  </>
                )}
              </Button>
            </Card>
          );
        })}
      </div>

      {lastResult && (
        <Card className="p-4 space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-medium">
              Son çalıştırma: {lastResult.filterName}
            </h2>
            <span className="text-xs text-muted-foreground">
              {(lastResult.durationMs / 1000).toFixed(1)} sn
            </span>
          </div>
          <div className="grid grid-cols-2 gap-2 text-xs sm:grid-cols-4">
            <Stat label="Bulundu" value={lastResult.discovered} />
            <Stat label="Yeni şirket" value={lastResult.newCompanies} />
            <Stat label="Taslak" value={lastResult.draftsCreated} highlight />
            <Stat label="Hata" value={lastResult.errors} warn={lastResult.errors > 0} />
            <Stat label="Web sitesi yok" value={lastResult.skippedNoWebsite} />
            <Stat label="Zaten var" value={lastResult.alreadyExists} />
            <Stat label="E-posta yok" value={lastResult.skippedNoEmail} />
            <Stat label="Hedef değil" value={lastResult.skippedNotTarget} />
            <Stat label="Eşleşme yok" value={lastResult.skippedNoMatch} />
          </div>
          {lastResult.draftsCreated > 0 && (
            <Button variant="outline" size="sm" onClick={() => router.push("/drafts")}>
              Taslakları İncele
            </Button>
          )}
        </Card>
      )}
    </div>
  );
}

function Stat({
  label,
  value,
  highlight,
  warn,
}: {
  label: string;
  value: number;
  highlight?: boolean;
  warn?: boolean;
}) {
  return (
    <div className="rounded-md border p-2">
      <p className="text-muted-foreground">{label}</p>
      <p className="text-lg font-semibold">
        {warn && value > 0 ? (
          <Badge variant="destructive">{value}</Badge>
        ) : highlight && value > 0 ? (
          <Badge>{value}</Badge>
        ) : (
          value
        )}
      </p>
    </div>
  );
}
