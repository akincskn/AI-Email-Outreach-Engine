import { api } from "@/lib/api";
import type { EmailDraft, Page } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import Link from "next/link";
import { formatDistanceToNow } from "@/lib/date";

export default async function DraftsPage() {
  let drafts: EmailDraft[] = [];
  try {
    const page = await api.get<Page<EmailDraft>>("/api/v1/drafts/pending?size=50");
    drafts = page.content;
  } catch {
    /* API not reachable in dev */
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Pending Drafts</h1>
        <span className="text-sm text-muted-foreground">{drafts.length} pending</span>
      </div>

      {drafts.length === 0 && (
        <Card className="p-8 text-center text-muted-foreground">No drafts pending approval</Card>
      )}

      <div className="space-y-2">
        {drafts.map((d) => (
          <Link key={d.id} href={`/drafts/${d.id}`}>
            <Card className="p-4 hover:bg-muted/50 transition-colors cursor-pointer">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <p className="font-medium text-sm truncate">{d.companyName}</p>
                  <p className="text-xs text-muted-foreground">{d.toEmail}</p>
                  <p className="text-sm mt-1 truncate">{d.subject}</p>
                </div>
                <div className="flex flex-col items-end gap-1 shrink-0">
                  <Badge variant={d.warnings.length > 0 ? "destructive" : "secondary"}>
                    {d.warnings.length > 0 ? "Has warnings" : d.language.toUpperCase()}
                  </Badge>
                  <span className="text-xs text-muted-foreground">{formatDistanceToNow(d.createdAt)}</span>
                </div>
              </div>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
