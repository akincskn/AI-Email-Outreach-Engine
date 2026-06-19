import { api } from "@/lib/api";
import type { DiscoveryFilter } from "@/lib/types";
import { Card } from "@/components/ui/card";
import { PipelineRunner } from "./pipeline-runner";

export default async function PipelinePage() {
  let filters: DiscoveryFilter[] = [];
  try {
    filters = await api.get<DiscoveryFilter[]>("/api/v1/discovery-filters/active");
  } catch {
    /* API not reachable */
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold">Pipeline</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Bir filtre seçip çalıştırın: şirketleri bulur, jenerik e-postaları çıkarır,
          AI ile analiz eder, ürün eşler ve onay bekleyen taslaklar oluşturur.
        </p>
      </div>

      {filters.length === 0 ? (
        <Card className="p-8 text-center text-muted-foreground">
          Aktif keşif filtresi yok. Önce bir filtre oluşturun.
        </Card>
      ) : (
        <PipelineRunner filters={filters} />
      )}
    </div>
  );
}
