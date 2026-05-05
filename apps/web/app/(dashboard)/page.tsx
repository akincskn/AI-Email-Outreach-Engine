import { api } from "@/lib/api";
import type { AnalyticsSummary, EmailReply } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import Link from "next/link";
import { Send, Eye, MessageSquare, AlertTriangle, FileText } from "lucide-react";

async function getStats() {
  const [summary, replies] = await Promise.allSettled([
    api.get<AnalyticsSummary>("/api/v1/analytics/summary"),
    api.get<{ content: EmailReply[] }>("/api/v1/replies/unhandled?size=3"),
  ]);
  return {
    summary: summary.status === "fulfilled" ? summary.value : null,
    replies: replies.status === "fulfilled" ? replies.value.content : [],
  };
}

export default async function DashboardHome() {
  const { summary, replies } = await getStats();

  const statCards = [
    { label: "Sent Today", value: summary?.sentToday ?? 0, icon: Send, href: "/sent" },
    { label: "Opened Today", value: summary?.openedToday ?? 0, icon: Eye, href: "/sent" },
    { label: "Replied Today", value: summary?.repliedToday ?? 0, icon: MessageSquare, href: "/replies" },
    { label: "Bounced Today", value: summary?.bouncedToday ?? 0, icon: AlertTriangle, href: "/sent?status=BOUNCED", warn: (summary?.bouncedToday ?? 0) > 0 },
  ];

  const cap = summary?.dailyCap ?? 0;
  const used = summary?.volumeUsedToday ?? 0;
  const capPct = cap > 0 ? Math.round((used / cap) * 100) : 0;

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold">Dashboard</h1>

      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        {statCards.map(({ label, value, icon: Icon, href, warn }) => (
          <Link key={label} href={href}>
            <Card className={warn && value > 0 ? "border-destructive" : ""}>
              <CardHeader className="pb-2 pt-4 px-4">
                <div className="flex items-center justify-between">
                  <span className="text-xs text-muted-foreground">{label}</span>
                  <Icon className="h-3.5 w-3.5 text-muted-foreground" />
                </div>
              </CardHeader>
              <CardContent className="px-4 pb-4">
                <span className="text-2xl font-bold">{value}</span>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <Card>
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <CardTitle className="text-sm font-medium">Volume Today</CardTitle>
              <span className="text-xs text-muted-foreground">{used}/{cap}</span>
            </div>
          </CardHeader>
          <CardContent>
            <div className="w-full bg-muted rounded-full h-2">
              <div
                className="bg-primary h-2 rounded-full transition-all"
                style={{ width: `${Math.min(capPct, 100)}%` }}
              />
            </div>
            <p className="text-xs text-muted-foreground mt-1">{capPct}% of daily cap used</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <CardTitle className="text-sm font-medium">Pending Approvals</CardTitle>
              <Link href="/drafts" className="text-xs text-primary hover:underline">View all</Link>
            </div>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2">
              <FileText className="h-8 w-8 text-muted-foreground" />
              <div>
                <span className="text-2xl font-bold">{summary?.pendingDrafts ?? 0}</span>
                <p className="text-xs text-muted-foreground">drafts awaiting your approval</p>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {replies.length > 0 && (
        <Card>
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <CardTitle className="text-sm font-medium">Recent Replies</CardTitle>
              <Link href="/replies" className="text-xs text-primary hover:underline">View all</Link>
            </div>
          </CardHeader>
          <CardContent className="space-y-2">
            {replies.map((r) => (
              <Link key={r.id} href={`/replies/${r.id}`} className="flex items-center justify-between p-2 rounded-md hover:bg-muted text-sm">
                <div>
                  <span className="font-medium">{r.companyName ?? r.fromEmail}</span>
                  <p className="text-xs text-muted-foreground truncate max-w-xs">{r.subject}</p>
                </div>
                <Badge variant="outline" className="text-xs shrink-0">New</Badge>
              </Link>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
