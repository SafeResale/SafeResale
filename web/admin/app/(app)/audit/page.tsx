"use client";

import { useState } from "react";
import { Download, RefreshCw, ScrollText } from "lucide-react";
import { API, ApiError, authHeaders } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { AuditEntry, PageResult } from "@/lib/types";
import { timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from "@/components/ui/table";

export default function AuditPage() {
  const [page, setPage] = useState(1);
  const { data, loading, error, reload } = useFetch<PageResult<AuditEntry>>(`/admin/audit-logs?page=${page}&page_size=50`);

  async function exportCsv() {
    await runMutation(async () => {
      const res = await fetch(`${API}/admin/audit-logs/export`, { headers: { ...authHeaders() } });
      if (!res.ok) throw new ApiError(res.status, `Export failed (${res.status})`);
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "audit.csv";
      a.click();
      URL.revokeObjectURL(url);
    }, { success: "Audit log exported" });
  }

  return (
    <div className="flex flex-col gap-4 min-w-0">
      <div className="@container/main px-4 lg:px-6">
        <PageHeader
          title="Audit trail"
          description="Every admin action, moderation decision and system event"
          actions={
            <>
              <Button variant="secondary" size="icon" aria-label="Refresh" onClick={reload}><RefreshCw className="size-4" /></Button>
              <Button variant="secondary" onClick={exportCsv}><Download className="size-4" /> Export CSV</Button>
            </>
          }
        />
      </div>

      <div className="@container/main px-4 lg:px-6 min-w-0">
        {error && <PageError message={error.message} onRetry={reload} />}

        {loading && !data && (
          <div className="overflow-hidden rounded-xl border shadow-sm p-4"><div className="space-y-3">{Array.from({ length: 10 }).map((_, i) => <Skeleton key={i} className="h-12 rounded-md" />)}</div></div>
        )}

        {data && data.items.length === 0 && (
          <EmptyState icon={ScrollText} title="No audit events" description="Events appear as soon as the system records them." />
        )}

        {data && data.items.length > 0 && (
          <>
            <div className="overflow-hidden rounded-lg border">
              <div className="overflow-x-auto">
                <Table aria-label="Audit trail" className="min-w-[880px]">
                  <TableHeader>
                    <TableRow>
                      <TableHead>Action</TableHead>
                      <TableHead>Actor</TableHead>
                      <TableHead>Target</TableHead>
                      <TableHead>Details</TableHead>
                      <TableHead className="text-right">When</TableHead>
                      <TableHead className="text-right">IP</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.items.map((a) => (
                      <TableRow key={a._id}>
                        <TableCell>
                          <StatusBadge tone="lime" label={a.action} className="font-mono text-xs" />
                        </TableCell>
                        <TableCell>
                          <span className="text-xs">{a.actor_role || "—"}</span>
                          <span className="block font-mono text-[11px] text-muted-foreground">{a.actor_id ? a.actor_id.slice(0, 10) : ""}</span>
                        </TableCell>
                        <TableCell>
                          <span className="text-xs">{a.target_type || "—"}</span>
                          <span className="block font-mono text-[11px] text-muted-foreground">{a.target_id ? a.target_id.slice(0, 10) : ""}</span>
                        </TableCell>
                        <TableCell className="max-w-72">
                          <p className="truncate font-mono text-[11px] text-muted-foreground">
                            {a.detail ? (typeof a.detail === "string" ? a.detail : JSON.stringify(a.detail)) : "—"}
                          </p>
                        </TableCell>
                        <TableCell className="text-right text-xs text-muted-foreground">{timeAgo(a.created_at)}</TableCell>
                        <TableCell className="text-right font-mono text-[11px] text-muted-foreground">{a.ip || "—"}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </div>
            <Pager page={data.page} pageSize={50} total={data.total} onPage={setPage} />
          </>
        )}
      </div>
    </div>
  );
}