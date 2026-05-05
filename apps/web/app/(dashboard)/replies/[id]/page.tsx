"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { api } from "@/lib/api";
import type { EmailReply } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { toast } from "sonner";
import { formatDate } from "@/lib/date";

export default function ReplyDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [reply, setReply] = useState<EmailReply | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    api.get<EmailReply>(`/api/v1/replies/${id}`).then(setReply).catch(() => {});
  }, [id]);

  async function markHandled() {
    setLoading(true);
    try {
      await api.put(`/api/v1/replies/${id}/mark-handled`, {});
      toast.success("Marked as handled");
      router.push("/replies");
    } catch {
      toast.error("Failed to mark as handled");
    } finally {
      setLoading(false);
    }
  }

  if (!reply) return <div className="text-muted-foreground text-sm p-4">Loading…</div>;

  return (
    <div className="space-y-4 max-w-2xl">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">{reply.companyName ?? reply.fromEmail}</h1>
          <p className="text-sm text-muted-foreground">{reply.fromEmail}</p>
        </div>
        <Badge variant={reply.handled ? "outline" : "secondary"}>
          {reply.handled ? "Handled" : "New"}
        </Badge>
      </div>

      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-sm">{reply.subject ?? "(no subject)"}</CardTitle>
          <p className="text-xs text-muted-foreground">{formatDate(reply.receivedAt)}</p>
        </CardHeader>
        <CardContent>
          <pre className="text-sm whitespace-pre-wrap font-sans">{reply.bodyText ?? "(no body)"}</pre>
        </CardContent>
      </Card>

      {!reply.handled && (
        <Button onClick={markHandled} disabled={loading}>
          {loading ? "Marking…" : "Mark as Handled"}
        </Button>
      )}
    </div>
  );
}
