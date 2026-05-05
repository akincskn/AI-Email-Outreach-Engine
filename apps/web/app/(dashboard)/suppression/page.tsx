"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { SuppressionEntry, Page } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { toast } from "sonner";
import { formatDate } from "@/lib/date";

export default function SuppressionPage() {
  const [entries, setEntries] = useState<SuppressionEntry[]>([]);
  const [email, setEmail] = useState("");
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(false);

  async function load() {
    try {
      const res = await api.get<Page<SuppressionEntry>>("/api/v1/suppression?size=100");
      setEntries(res.content);
    } catch {
      /* silent */
    }
  }

  useEffect(() => { load(); }, []);

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      await api.post("/api/v1/suppression", { email, reason });
      toast.success(`${email} added to suppression list`);
      setEmail("");
      setReason("");
      load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to add");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold">Suppression List</h1>

      <Card className="max-w-md">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm">Add Email</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleAdd} className="space-y-3">
            <div className="space-y-1">
              <Label className="text-xs">Email</Label>
              <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Reason</Label>
              <Input value={reason} onChange={(e) => setReason(e.target.value)} required />
            </div>
            <Button type="submit" size="sm" disabled={loading}>
              {loading ? "Adding…" : "Add to Suppression"}
            </Button>
          </form>
        </CardContent>
      </Card>

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Email</TableHead>
              <TableHead>Reason</TableHead>
              <TableHead>Suppressed At</TableHead>
              <TableHead>Expires</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {entries.length === 0 && (
              <TableRow>
                <TableCell colSpan={4} className="text-center text-muted-foreground text-sm py-6">
                  No suppressed emails
                </TableCell>
              </TableRow>
            )}
            {entries.map((e) => (
              <TableRow key={e.id}>
                <TableCell className="font-mono text-xs">{e.email}</TableCell>
                <TableCell className="text-xs">{e.reason}</TableCell>
                <TableCell className="text-xs">{formatDate(e.suppressedAt)}</TableCell>
                <TableCell className="text-xs text-muted-foreground">{e.expiresAt ? formatDate(e.expiresAt) : "Never"}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
