"use client";

import { useState } from "react";
import Link from "next/link";
import { Flag, RefreshCw, ShieldCheck, X } from "lucide-react";
import { post, queryString } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { PageResult, Report } from "@/lib/types";
import { timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
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
import { StatusBadge, statusTone } from "@/components/status-badge";

export default function ReportsPage() {
  const [status, setStatus] = useState("all");
  const [type, setType] = useState("all");
  const [page, setPage] = useState(1);
  const [acting, setActing] = useState<Report | null>(null);
  const [resolution, setResolution] = useState("");

  const path = `/admin/reports${queryString({
    status: status === "all" ? undefined : status,
    target_type: type === "all" ? undefined : type,
    page,
    page_size: 20,
  })}`;
  const { data, loading, error, reload } = useFetch<PageResult<Report>>(path);

  async function resolve(r: Report, action: "resolve" | "dismiss") {
    if (!r) return;
    const ok = await runMutation(
      () => post(`/admin/reports/${r._id}/${action}`, { note: resolution.trim() }),
      { success: `Report ${action}d` },
    );
    if (ok) {
      setActing(null);
      setResolution("");
      reload();
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="@container/main px-4 lg:px-6">
        <PageHeader
          title="Reports"
          description="Content and user reports submitted through the app"
          actions={
            <Button variant="outline" size="icon" aria-label="Refresh" onClick={reload} className="cursor-pointer">
              <RefreshCw className="size-4" />
            </Button>
          }
        />
      </div>

      <div className="@container/main px-4 lg:px-6 space-y-4">
        <div className="flex flex-wrap gap-2">
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
              <SelectItem value="all" className="cursor-pointer">All statuses</SelectItem>
              <SelectItem value="pending" className="cursor-pointer">Pending</SelectItem>
              <SelectItem value="resolved" className="cursor-pointer">Resolved</SelectItem>
              <SelectItem value="dismissed" className="cursor-pointer">Dismissed</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={type}
            onValueChange={(v) => {
              setType(v || "all");
              setPage(1);
            }}
          >
            <SelectTrigger className="w-36 cursor-pointer" aria-label="Filter by target">
              <SelectValue placeholder="Target" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all" className="cursor-pointer">All targets</SelectItem>
              <SelectItem value="listing" className="cursor-pointer">Listing</SelectItem>
              <SelectItem value="user" className="cursor-pointer">User</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {error && <PageError message={error.message} onRetry={reload} />}

        {loading && !data && (
          <div className="rounded-md border p-4 space-y-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-14 rounded-md" />
            ))}
          </div>
        )}

        {data && data.items.length === 0 && (
          <EmptyState icon={Flag} title="No reports" description="Nothing matches this filter. Reported listings and users will show here." />
        )}

        {data && data.items.length > 0 && (
          <div className="space-y-4">
            <div className="rounded-md border">
              <Table aria-label="Reports">
                <TableHeader>
                  <TableRow>
                    <TableHead>Target</TableHead>
                    <TableHead>Reason</TableHead>
                    <TableHead>Reporter</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Reported</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((r) => (
                    <TableRow key={r._id} id={r._id}>
                      <TableCell className="max-w-52">
                        {r.target_type === "listing" && r.target?.id ? (
                          <Link href={`/listings/${r.target.id}`} className="block">
                            <span className="block truncate font-medium text-sm hover:text-primary">{r.target.title || "Untitled listing"}</span>
                            <span className="text-xs text-muted-foreground">listing · {r.target.status}</span>
                          </Link>
                        ) : r.target_type === "user" && r.target?.id ? (
                          <Link href={`/users/${r.target.id}`} className="block">
                            <span className="block truncate font-medium text-sm hover:text-primary">{r.target.name || "User"}</span>
                            <span className="text-xs text-muted-foreground">user · {r.target.role}</span>
                          </Link>
                        ) : (
                          <span className="text-xs text-muted-foreground">
                            {r.target_id} ({r.target_type})
                          </span>
                        )}
                      </TableCell>
                      <TableCell className="max-w-56">
                        <span className="block truncate text-sm">{r.reason}</span>
                        {r.description && <span className="block truncate text-xs text-muted-foreground">{r.description}</span>}
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground">{r.reporter?.name || r.reporter?.email || "—"}</TableCell>
                      <TableCell>
                        <StatusBadge tone={statusTone[r.status] ?? "neutral"} label={r.status} className="capitalize" />
                      </TableCell>
                      <TableCell className="text-right text-xs text-muted-foreground whitespace-nowrap">{timeAgo(r.created_at)}</TableCell>
                      <TableCell>
                        {r.status === "pending" ? (
                          <div className="flex justify-end gap-1.5">
                            <Button
                              size="sm"
                              onClick={() => {
                                setActing(r);
                                setResolution("");
                              }}
                              className="cursor-pointer"
                            >
                              <ShieldCheck className="size-3.5" /> Resolve
                            </Button>
                            <Button variant="ghost" size="sm" onClick={() => resolve(r, "dismiss")} className="cursor-pointer">
                              <X className="size-3.5" /> Dismiss
                            </Button>
                          </div>
                        ) : (
                          <span className="block text-right text-xs text-muted-foreground">{r.resolved_at ? timeAgo(r.resolved_at) : "—"}</span>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            <Pager page={data.page} pageSize={data.page_size} total={data.total} onPage={setPage} />
          </div>
        )}
      </div>

      <Dialog open={!!acting} onOpenChange={(v) => !v && setActing(null)}>
        <DialogContent className="sm:max-w-[440px] rounded-xl">
          <DialogHeader>
            <DialogTitle>Resolve report</DialogTitle>
            <DialogDescription>Record how the review was actioned for the audit trail.</DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5">
            <Label htmlFor="note">Resolution note (optional)</Label>
            <Input
              id="note"
              value={resolution}
              onChange={(e) => setResolution(e.target.value)}
              placeholder="e.g. verified with seller — listing approved"
            />
          </div>
          <Button className="mt-2 w-full cursor-pointer" onClick={() => acting && resolve(acting, "resolve")}>
            Mark resolved
          </Button>
        </DialogContent>
      </Dialog>
    </div>
  );
}
