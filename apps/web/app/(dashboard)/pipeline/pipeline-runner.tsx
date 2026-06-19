"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { toast } from "sonner";
import { Loader2, Play } from "lucide-react";
import type { DiscoveryFilter } from "@/lib/types";
import { runPipeline, type PipelineRunResult } from "./actions";

export function PipelineRunner({ filters }: { filters: DiscoveryFilter[] }) {
  const router = useRouter();
  const [isPending, startTransition] = useTransition();
  const [runningId, setRunningId] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<PipelineRunResult | null>(null);

  function handleRun(filterId: string) {
    setRunningId(filterId);
    setLastResult(null);
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

  return (
    <div className="space-y-4">
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
                disabled={isPending}
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
