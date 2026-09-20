"use client";

import { useState } from "react";
import { Archive, CheckCheck, Mail, RefreshCw } from "lucide-react";
import { del, get, patch, queryString } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { ContactMessage, PageResult } from "@/lib/types";
import { fmtDate, timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { StatusBadge, statusTone } from "@/components/status-badge";

export default function MessagesPage() {
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState<ContactMessage | null>(null);
  const [detail, setDetail] = useState<ContactMessage | null>(null);

  const path = `/admin/messages${queryString({ status: status === "all" ? undefined : status, page, page_size: 20 })}`;
  const { data, loading, error, reload } = useFetch<PageResult<ContactMessage>>(path);

  async function open(m: ContactMessage) {
    setSelected(m);
    const d = await get<{ message: ContactMessage }>(`/admin/messages/${m._id}`);
    setDetail(d.message);
    if (d.message.status === "read" && m.status === "new") reload();
  }

  async function setState(m: ContactMessage, next: string) {
    const ok = await runMutation(() => patch(`/admin/messages/${m._id}`, { status: next }), { success: `Message ${next}` });
    if (ok) {
      setDetail((d) => (d ? { ...d, status: next as ContactMessage["status"] } : d));
      reload();
    }
  }

  async function remove(m: ContactMessage) {
    const ok = await runMutation(async () => {
      await del(`/admin/messages/${m._id}`);
      return true;
    }, { success: "Message deleted" });
    if (ok) {
      setSelected(null);
      reload();
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="@container/main px-4 lg:px-6">
        <PageHeader
          title="Messages"
          description="Contact form submissions from the public site"
          actions={
            <div className="flex items-center gap-2">
              <Select
                value={status}
                onValueChange={(v) => {
                  setStatus(v || "all");
                  setPage(1);
                }}
              >
                <SelectTrigger className="w-36 cursor-pointer" aria-label="Filter by status">
                  <SelectValue placeholder="Status" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all" className="cursor-pointer">All</SelectItem>
                  <SelectItem value="new" className="cursor-pointer">New</SelectItem>
                  <SelectItem value="read" className="cursor-pointer">Read</SelectItem>
                  <SelectItem value="resolved" className="cursor-pointer">Resolved</SelectItem>
                  <SelectItem value="archived" className="cursor-pointer">Archived</SelectItem>
                </SelectContent>
              </Select>
              <Button variant="outline" size="icon" aria-label="Refresh" onClick={reload} className="cursor-pointer">
                <RefreshCw className="size-4" />
              </Button>
            </div>
          }
        />
      </div>

      <div className="@container/main px-4 lg:px-6 space-y-4">
        {error && <PageError message={error.message} onRetry={reload} />}

        {loading && !data && (
          <div className="rounded-md border p-4 space-y-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-14 rounded-md" />
            ))}
          </div>
        )}

        {data && data.items.length === 0 && (
          <EmptyState icon={Mail} title="No messages" description="Contact-form submissions will land here as soon as they are sent." />
        )}

        {data && data.items.length > 0 && (
          <div className="space-y-4">
            <div className="rounded-md border">
              <Table aria-label="Messages">
                <TableHeader>
                  <TableRow>
                    <TableHead>From</TableHead>
                    <TableHead>Subject</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Received</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((m) => (
                    <TableRow key={m._id} id={m._id} className="cursor-pointer" onClick={() => open(m)}>
                      <TableCell>
                        <div className="flex flex-col">
                          <span className="block max-w-[180px] truncate font-medium text-sm">{m.name || "Anonymous"}</span>
                          <span className="block max-w-48 truncate text-xs text-muted-foreground">{m.email}</span>
                        </div>
                      </TableCell>
                      <TableCell className="max-w-72">
                        <span className="block truncate text-sm">{m.subject || "(no subject)"}</span>
                      </TableCell>
                      <TableCell>
                        <StatusBadge tone={statusTone[m.status] ?? "neutral"} label={m.status} className="capitalize" />
                      </TableCell>
                      <TableCell className="text-right text-xs text-muted-foreground whitespace-nowrap">{timeAgo(m.created_at)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            <Pager page={data.page} pageSize={data.page_size} total={data.total} onPage={setPage} />
          </div>
        )}
      </div>

      <Dialog
        open={!!selected}
        onOpenChange={(v) => {
          if (!v) {
            setSelected(null);
            setDetail(null);
          }
        }}
      >
        <DialogContent className="sm:max-w-xl rounded-xl">
          <DialogHeader>
            <DialogTitle>{detail?.subject || "Message"}</DialogTitle>
            <DialogDescription>
              {detail?.name || "Anonymous"} · {detail?.email} · {fmtDate(detail?.created_at)}
            </DialogDescription>
          </DialogHeader>
          <div className="max-h-72 overflow-y-auto whitespace-pre-wrap rounded-md border bg-muted/40 p-4 text-sm">
            {detail?.message || "No body."}
          </div>
          <DialogFooter className="flex justify-end gap-2 sm:justify-end">
            <Button variant="outline" size="sm" onClick={() => selected && remove(selected)} className="cursor-pointer">
              <Archive className="size-3.5" /> Delete
            </Button>
            {detail?.status === "resolved" ? (
              <Button size="sm" onClick={() => selected && setState(selected, "archived")} className="cursor-pointer">
                <CheckCheck className="size-3.5" /> Archive
              </Button>
            ) : (
              <Button size="sm" onClick={() => selected && setState(selected, "resolved")} className="cursor-pointer">
                <CheckCheck className="size-3.5" /> Mark resolved
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
