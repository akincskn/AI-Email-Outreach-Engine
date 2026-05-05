"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { DiscoveryFilter } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { toast } from "sonner";

export default function SettingsPage() {
  const [filters, setFilters] = useState<DiscoveryFilter[]>([]);
  const [form, setForm] = useState({ name: "", industry: "", countryCode: "", city: "", active: true });

  async function load() {
    try {
      const res = await api.get<DiscoveryFilter[]>("/api/v1/discovery-filters");
      setFilters(res);
    } catch {
      /* silent */
    }
  }

  useEffect(() => { load(); }, []);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    try {
      await api.post("/api/v1/discovery-filters", { ...form, keywords: [] });
      toast.success("Filter created");
      setForm({ name: "", industry: "", countryCode: "", city: "", active: true });
      load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to create filter");
    }
  }

  async function toggleActive(f: DiscoveryFilter) {
    try {
      await api.put(`/api/v1/discovery-filters/${f.id}`, { ...f, active: !f.active, keywords: f.keywords ?? [] });
      load();
    } catch {
      toast.error("Failed to update filter");
    }
  }

  async function deleteFilter(id: string) {
    try {
      await api.delete(`/api/v1/discovery-filters/${id}`);
      toast.success("Filter deleted");
      load();
    } catch {
      toast.error("Failed to delete filter");
    }
  }

  return (
    <div className="space-y-6 max-w-2xl">
      <h1 className="text-xl font-semibold">Settings</h1>

      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-sm">Discovery Filters</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <form onSubmit={handleCreate} className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label className="text-xs">Name *</Label>
              <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Industry</Label>
              <Input value={form.industry} onChange={(e) => setForm({ ...form, industry: e.target.value })} />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Country Code (TR / US)</Label>
              <Input value={form.countryCode} onChange={(e) => setForm({ ...form, countryCode: e.target.value.toUpperCase() })} maxLength={2} />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">City</Label>
              <Input value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} />
            </div>
            <Button type="submit" size="sm" className="col-span-2">Create Filter</Button>
          </form>

          <div className="space-y-2">
            {filters.length === 0 && <p className="text-sm text-muted-foreground">No filters yet</p>}
            {filters.map((f) => (
              <div key={f.id} className="flex items-center justify-between p-2 rounded border gap-4">
                <div className="min-w-0">
                  <p className="text-sm font-medium">{f.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {[f.industry, f.countryCode, f.city].filter(Boolean).join(" · ")}
                  </p>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <Badge
                    variant={f.active ? "default" : "outline"}
                    className="cursor-pointer"
                    onClick={() => toggleActive(f)}
                  >
                    {f.active ? "Active" : "Inactive"}
                  </Badge>
                  <Button variant="ghost" size="sm" onClick={() => deleteFilter(f.id)} className="text-destructive text-xs">
                    Delete
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
