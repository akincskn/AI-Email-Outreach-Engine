import { api } from "@/lib/api";
import type { EmailSend, Page } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import Link from "next/link";
import { formatDistanceToNow } from "@/lib/date";
import { Eye, MessageSquare } from "lucide-react";

const STATUS_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  SENT: "default",
  BOUNCED: "destructive",
  FAILED: "destructive",
  SUPPRESSED: "outline",
  SENDING: "secondary",
};

export default async function SentPage({
  searchParams,
}: {
  searchParams: { status?: string };
}) {
  let sends: EmailSend[] = [];
  let total = 0;
  try {
    const params = new URLSearchParams({ size: "30" });
    if (searchParams.status) params.set("status", searchParams.status);
    const res = await api.get<Page<EmailSend>>(`/api/v1/sends?${params}`);
    sends = res.content;
    total = res.totalElements;
  } catch {
    /* API not reachable */
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Sent Emails</h1>
        <span className="text-sm text-muted-foreground">{total} total</span>
      </div>

      <div className="flex gap-2 flex-wrap text-xs">
        {["", "SENT", "BOUNCED", "FAILED"].map((s) => (
          <Link
            key={s}
            href={s ? `/sent?status=${s}` : "/sent"}
            className={`px-2 py-1 rounded border ${(!searchParams.status && !s) || searchParams.status === s ? "bg-primary text-primary-foreground border-primary" : "border-border hover:bg-muted"}`}
          >
            {s || "All"}
          </Link>
        ))}
      </div>

      {sends.length === 0 && (
        <Card className="p-8 text-center text-muted-foreground">No emails sent yet</Card>
      )}

      <div className="space-y-2">
        {sends.map((s) => (
          <Link key={s.id} href={`/sent/${s.id}`}>
            <Card className="p-3 hover:bg-muted/50 transition-colors cursor-pointer flex items-center justify-between gap-4">
              <div className="min-w-0">
                <p className="font-medium text-sm">{s.companyName}</p>
                <p className="text-xs text-muted-foreground">{s.toEmail}</p>
                <p className="text-xs mt-0.5 truncate">{s.subject}</p>
              </div>
              <div className="flex flex-col items-end gap-1 shrink-0">
                <Badge variant={STATUS_VARIANT[s.status] ?? "outline"} className="text-xs">
                  {s.status}
                </Badge>
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  {s.hasOpen && <Eye className="h-3 w-3" />}
                  {s.hasReply && <MessageSquare className="h-3 w-3" />}
                  {s.sentAt && <span>{formatDistanceToNow(s.sentAt)}</span>}
                </div>
              </div>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
