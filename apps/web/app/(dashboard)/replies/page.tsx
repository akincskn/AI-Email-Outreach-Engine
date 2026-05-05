import { api } from "@/lib/api";
import type { EmailReply, Page } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import Link from "next/link";
import { formatDistanceToNow } from "@/lib/date";

export default async function RepliesPage() {
  let replies: EmailReply[] = [];
  let total = 0;
  try {
    const res = await api.get<Page<EmailReply>>("/api/v1/replies/unhandled?size=50");
    replies = res.content;
    total = res.totalElements;
  } catch {
    /* API not reachable */
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Unhandled Replies</h1>
        <span className="text-sm text-muted-foreground">{total} unhandled</span>
      </div>

      {replies.length === 0 && (
        <Card className="p-8 text-center text-muted-foreground">No unhandled replies</Card>
      )}

      <div className="space-y-2">
        {replies.map((r) => (
          <Link key={r.id} href={`/replies/${r.id}`}>
            <Card className="p-3 hover:bg-muted/50 transition-colors cursor-pointer flex items-center justify-between gap-4">
              <div className="min-w-0">
                <p className="font-medium text-sm">{r.companyName ?? r.fromEmail}</p>
                <p className="text-xs text-muted-foreground">{r.fromEmail}</p>
                {r.subject && <p className="text-xs mt-0.5 truncate">{r.subject}</p>}
              </div>
              <div className="flex flex-col items-end gap-1 shrink-0">
                <Badge variant="secondary" className="text-xs">New</Badge>
                <span className="text-xs text-muted-foreground">{formatDistanceToNow(r.receivedAt)}</span>
              </div>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
