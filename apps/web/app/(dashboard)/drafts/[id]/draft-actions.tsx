"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { api } from "@/lib/api";
import { toast } from "sonner";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";

interface Props {
  draftId: string;
  defaultSubject: string;
  defaultBodyHtml: string;
}

export function DraftActions({ draftId, defaultSubject, defaultBodyHtml }: Props) {
  const router = useRouter();
  const [editSubject, setEditSubject] = useState(defaultSubject);
  const [editBody, setEditBody] = useState(defaultBodyHtml);
  const [rejectReason, setRejectReason] = useState("");
  const [loading, setLoading] = useState<"approve" | "reject" | null>(null);
  const [rejectOpen, setRejectOpen] = useState(false);

  async function handleApprove() {
    setLoading("approve");
    try {
      await api.put(`/api/v1/drafts/${draftId}/approve`, {
        editedSubject: editSubject !== defaultSubject ? editSubject : null,
        editedBodyHtml: editBody !== defaultBodyHtml ? editBody : null,
      });
      toast.success("Draft approved & sent!");
      router.push("/sent");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Failed to approve");
    } finally {
      setLoading(null);
    }
  }

  async function handleReject() {
    if (!rejectReason.trim()) {
      toast.error("Please provide a rejection reason");
      return;
    }
    setLoading("reject");
    try {
      await api.put(`/api/v1/drafts/${draftId}/reject`, { reason: rejectReason });
      toast.success("Draft rejected");
      router.push("/drafts");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Failed to reject");
    } finally {
      setLoading(null);
      setRejectOpen(false);
    }
  }

  return (
    <div className="space-y-4">
      <h2 className="text-sm font-medium">Edit &amp; Approve</h2>

      <div className="space-y-2">
        <Label className="text-xs">Subject (editable)</Label>
        <Input value={editSubject} onChange={(e) => setEditSubject(e.target.value)} />
      </div>

      <div className="space-y-2">
        <Label className="text-xs">Body HTML (editable)</Label>
        <Textarea
          value={editBody}
          onChange={(e) => setEditBody(e.target.value)}
          rows={10}
          className="font-mono text-xs"
        />
      </div>

      <div className="flex gap-2">
        <Button
          onClick={handleApprove}
          disabled={loading !== null}
          className="flex-1"
        >
          {loading === "approve" ? "Sending…" : "Approve & Send"}
        </Button>

        <Button variant="destructive" disabled={loading !== null} onClick={() => setRejectOpen(true)}>Reject</Button>
        <Dialog open={rejectOpen} onOpenChange={setRejectOpen}>
          <>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Reject Draft</DialogTitle>
            </DialogHeader>
            <div className="space-y-2 mt-2">
              <Label>Reason</Label>
              <Textarea
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                placeholder="Why is this draft being rejected?"
                rows={3}
              />
            </div>
            <Button
              variant="destructive"
              onClick={handleReject}
              disabled={loading !== null}
              className="w-full mt-2"
            >
              {loading === "reject" ? "Rejecting…" : "Confirm Reject"}
            </Button>
          </DialogContent>
          </>
        </Dialog>
      </div>
    </div>
  );
}
