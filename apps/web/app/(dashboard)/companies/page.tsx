import { api } from "@/lib/api";
import type { Company, Page } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import Link from "next/link";

const STATUS_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  NEW: "secondary",
  EMAILS_EXTRACTED: "outline",
  ANALYZED: "default",
  BLACKLISTED: "destructive",
  SENT: "default",
};

export default async function CompaniesPage({
  searchParams,
}: {
  searchParams: { status?: string; page?: string };
}) {
  let companies: Company[] = [];
  let total = 0;
  try {
    const params = new URLSearchParams({ size: "30", page: searchParams.page ?? "0" });
    if (searchParams.status) params.set("status", searchParams.status);
    const res = await api.get<Page<Company>>(`/api/v1/companies?${params}`);
    companies = res.content;
    total = res.totalElements;
  } catch {
    /* API not reachable */
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Companies</h1>
        <span className="text-sm text-muted-foreground">{total} total</span>
      </div>

      <div className="flex gap-2 flex-wrap text-xs">
        {["", "NEW", "ANALYZED", "BLACKLISTED"].map((s) => (
          <Link
            key={s}
            href={s ? `/companies?status=${s}` : "/companies"}
            className={`px-2 py-1 rounded border ${(!searchParams.status && !s) || searchParams.status === s ? "bg-primary text-primary-foreground border-primary" : "border-border hover:bg-muted"}`}
          >
            {s || "All"}
          </Link>
        ))}
      </div>

      {companies.length === 0 && (
        <Card className="p-8 text-center text-muted-foreground">No companies found</Card>
      )}

      <div className="space-y-2">
        {companies.map((c) => (
          <Link key={c.id} href={`/companies/${c.id}`}>
            <Card className="p-3 hover:bg-muted/50 transition-colors cursor-pointer flex items-center justify-between gap-4">
              <div className="min-w-0">
                <p className="font-medium text-sm">{c.name}</p>
                <p className="text-xs text-muted-foreground">{c.domain} · {c.countryCode ?? "?"} {c.city ? `· ${c.city}` : ""}</p>
              </div>
              <Badge variant={STATUS_VARIANT[c.status] ?? "outline"} className="shrink-0 text-xs">
                {c.status}
              </Badge>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
