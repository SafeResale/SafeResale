"use client";

import { useState } from "react";
import { Bell, Loader2, Megaphone } from "lucide-react";
import { post } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { NotificationItem, PageResult } from "@/lib/types";
import { fmtDate, timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { StatusBadge } from "@/components/status-badge";

const AUDIENCES = ["all", "sellers", "buyers", "admins", "inspectors"];

export default function NotificationsPage() {
  const [page, setPage] = useState(1);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ title: "", body: "", audience: "all" });
  const [saving, setSaving] = useState(false);

  const { data, loading, error, reload } = useFetch<PageResult<NotificationItem>>(
    `/admin/notifications?page=${page}&page_size=20`,
  );

  async function send(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    const ok = await runMutation(() => post("/admin/notifications", form), {
      success: `Notification queued for ${form.audience}`,
    });
    setSaving(false);
    if (ok) {
      setOpen(false);
      setForm({ title: "", body: "", audience: "all" });
      reload();
    }
  }

  return (
    <div className="flex flex-col gap-4 min-w-0">
      <div className="@container/main px-4 lg:px-6">
        <PageHeader
          title="Notifications"
          description="In-app announcements broadcast to user segments"
          actions={
            <>
              <Button className="bg-accent text-accent-foreground hover:bg-accent/90" onClick={() => setOpen(true)}>
                <Megaphone className="size-4" /> Broadcast
              </Button>
              <Dialog open={open} onOpenChange={setOpen}>
                <DialogContent className="sm:max-w-md">
                  <DialogHeader>
                    <DialogTitle>New announcement</DialogTitle>
                    <DialogDescription>
                      Recorded as an in-app notification for the target audience.
                    </DialogDescription>
                  </DialogHeader>
                  <form onSubmit={send} className="space-y-4">
                    <div className="space-y-1.5">
                      <Label htmlFor="n-title">Title</Label>
                      <Input
                        id="n-title"
                        required
                        value={form.title}
                        onChange={(e) => setForm({ ...form, title: e.target.value })}
                      />
                    </div>
                    <div className="space-y-1.5">
                      <Label htmlFor="n-body">Body</Label>
                      <Textarea
                        id="n-body"
                        rows={3}
                        value={form.body}
                        onChange={(e) => setForm({ ...form, body: e.target.value })}
                      />
                    </div>
                    <div className="space-y-1.5">
                      <Label>Audience</Label>
                      <Select
                        value={form.audience}
                        onValueChange={(v) => setForm({ ...form, audience: v || "all" })}
                      >
                        <SelectTrigger aria-label="Audience">
                          <SelectValue placeholder="Select audience" />
                        </SelectTrigger>
                        <SelectContent>
                          {AUDIENCES.map((a) => (
                            <SelectItem key={a} value={a}>
                              <span className="capitalize">{a}</span>
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <Button
                      type="submit"
                      disabled={saving}
                      className="w-full bg-accent text-accent-foreground hover:bg-accent/90"
                    >
                      {saving && <Loader2 className="size-4 animate-spin" />}
                      {saving ? "Sending…" : "Send announcement"}
                    </Button>
                  </form>
                </DialogContent>
              </Dialog>
            </>
          }
        />
      </div>

      <div className="@container/main px-4 lg:px-6 min-w-0 space-y-4">
        {error && <PageError message={error.message} onRetry={reload} />}

        {loading && !data && (
          <div className="overflow-hidden rounded-xl border shadow-sm p-4 space-y-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-14 rounded-md" />
            ))}
          </div>
        )}

      {data && data.items.length === 0 && (
        <EmptyState icon={Bell} title="No notifications" description="Announcements to buyers and sellers will appear here." />
      )}

      {data && data.items.length > 0 && (
        <>
          <div className="overflow-hidden rounded-lg border">
            <div className="overflow-x-auto">
              <Table aria-label="Notifications" className="min-w-[640px]">
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
                      <TableRow key={n._id} id={n._id}>
                        <TableCell>
                          <p className="font-medium">{n.title}</p>
                          {n.body && <p className="max-w-96 truncate text-xs text-muted-foreground">{n.body}</p>}
                        </TableCell>
                        <TableCell>
                          <StatusBadge tone="neutral" label={n.audience} className="capitalize" />
                        </TableCell>
                        <TableCell>
                          <StatusBadge tone="lime" label={n.delivery || "in_app"} />
                        </TableCell>
                        <TableCell className="text-right tabular-nums">{n.recipient_count ?? 0}</TableCell>
                        <TableCell className="text-right text-xs text-muted-foreground">
                          {n.sent_at ? timeAgo(n.sent_at) : fmtDate(n.created_at)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </div>
          <Pager page={data.page} pageSize={data.page_size} total={data.total} onPage={setPage} />
        </>
      )}
      </div>
    </div>
  );
}