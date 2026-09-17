"use client";

import { useState } from "react";
import { Bell, Megaphone, RefreshCw } from "lucide-react";
import { post } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { NotificationItem, PageResult } from "@/lib/types";
import { fmtDate, timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { StatusBadge } from "@/components/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";

const AUDIENCES = ["all", "sellers", "buyers", "admins", "inspectors"];

export default function NotificationsPage() {
  const [page, setPage] = useState(1);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ title: "", body: "", audience: "all" });
  const [saving, setSaving] = useState(false);

  const { data, loading, error, reload } = useFetch<PageResult<NotificationItem>>(`/admin/notifications?page=${page}&page_size=20`);

  async function send(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    const ok = await runMutation(
      () => post("/admin/notifications", form),
      { success: `Notification queued for ${form.audience}` },
    );
    setSaving(false);
    if (ok) {
      setOpen(false);
      setForm({ title: "", body: "", audience: "all" });
      reload();
    }
  }

  return (
    <div>
      <PageHeader
        title="Notifications"
        description="In-app announcements broadcast to user segments"
        actions={
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
              <Button><Megaphone className="size-4" /> Broadcast</Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>New announcement</DialogTitle>
                <DialogDescription>Recorded as an in-app notification for the target audience.</DialogDescription>
              </DialogHeader>
              <form onSubmit={send} className="space-y-4">
                <div className="space-y-1.5">
                  <Label htmlFor="n-title">Title</Label>
                  <Input id="n-title" required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="n-body">Body</Label>
                  <Textarea id="n-body" rows={3} value={form.body} onChange={(e) => setForm({ ...form, body: e.target.value })} />
                </div>
                <div className="space-y-1.5">
                  <Label>Audience</Label>
                  <Select value={form.audience} onValueChange={(v) => setForm({ ...form, audience: v })}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {AUDIENCES.map((a) => <SelectItem key={a} value={a}>{a}</SelectItem>)}
                    </SelectContent>
                  </Select>
                </div>
                <DialogFooter>
                  <Button type="submit" className="w-full" disabled={saving}>{saving ? "Sending…" : "Send announcement"}</Button>
                </DialogFooter>
              </form>
            </DialogContent>
          </Dialog>
        }
      />

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <Card className="p-4"><div className="space-y-3">{Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-14" />)}</div></Card>
      )}

      {data && data.items.length === 0 && (
        <EmptyState icon={Bell} title="No notifications" description="Announcements to buyers and sellers will appear here." />
      )}

      {data && data.items.length > 0 && (
        <>
          <div className="overflow-hidden rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Title</TableHead>
                  <TableHead>Audience</TableHead>
                  <TableHead>Delivery</TableHead>
                  <TableHead className="text-right">Recipients</TableHead>
                  <TableHead className="text-right">Sent</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((n) => (
                  <TableRow key={n._id}>
                    <TableCell>
                      <p className="font-medium">{n.title}</p>
                      {n.body && <p className="max-w-96 truncate text-xs text-muted-foreground">{n.body}</p>}
                    </TableCell>
                    <TableCell><Badge variant="secondary">{n.audience}</Badge></TableCell>
                    <TableCell><StatusBadge tone="info" label={n.delivery || "in_app"} /></TableCell>
                    <TableCell className="text-right tabular-nums">{n.recipient_count ?? 0}</TableCell>
                    <TableCell className="text-right text-xs text-muted-foreground">{n.sent_at ? timeAgo(n.sent_at) : fmtDate(n.created_at)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Pager page={data.page} pageSize={data.page_size} total={data.total} onPage={setPage} />
        </>
      )}
    </div>
  );
}