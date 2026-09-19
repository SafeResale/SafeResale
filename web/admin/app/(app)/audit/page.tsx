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
import { Button, Card, Chip, Skeleton, Table } from "@heroui/react";

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
    <div>
      <PageHeader
        title="Audit trail"
        description="Every admin action, moderation decision and system event"
        actions={
          <>
            <Button variant="secondary" isIconOnly aria-label="Refresh" onPress={reload}><RefreshCw className="size-4" /></Button>
            <Button variant="secondary" onPress={exportCsv}><Download className="size-4" /> Export CSV</Button>
          </>
        }
      />

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <Card className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10 p-4"><div className="space-y-3">{Array.from({ length: 10 }).map((_, i) => <Skeleton key={i} className="h-12 rounded-xl" />)}</div></Card>
      )}

      {data && data.items.length === 0 && (
        <EmptyState icon={ScrollText} title="No audit events" description="Events appear as soon as the system records them." />
      )}

      {data && data.items.length > 0 && (
        <>
          <Card className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10 overflow-hidden">
            <Card.Content className="p-0">
              <Table>
                <Table.ScrollContainer>
                  <Table.Content aria-label="Audit trail" className="min-w-[880px]">
                    <Table.Header>
                      <Table.Column isRowHeader>Action</Table.Column>
                      <Table.Column>Actor</Table.Column>
                      <Table.Column>Target</Table.Column>
                      <Table.Column>Details</Table.Column>
                      <Table.Column className="text-right">When</Table.Column>
                      <Table.Column className="text-right">IP</Table.Column>
                    </Table.Header>
                    <Table.Body>
                      {data.items.map((a) => (
                        <Table.Row key={a._id} id={a._id}>
                          <Table.Cell>
                            <Chip variant="soft" color="accent" size="sm" className="font-mono text-xs">{a.action}</Chip>
                          </Table.Cell>
                          <Table.Cell>
                            <span className="text-xs">{a.actor_role || "—"}</span>
                            <span className="block font-mono text-[11px] text-muted-foreground">{a.actor_id ? a.actor_id.slice(0, 10) : ""}</span>
                          </Table.Cell>
                          <Table.Cell>
                            <span className="text-xs">{a.target_type || "—"}</span>
                            <span className="block font-mono text-[11px] text-muted-foreground">{a.target_id ? a.target_id.slice(0, 10) : ""}</span>
                          </Table.Cell>
                          <Table.Cell className="max-w-72">
                            <p className="truncate font-mono text-[11px] text-muted-foreground">
                              {a.detail ? (typeof a.detail === "string" ? a.detail : JSON.stringify(a.detail)) : "—"}
                            </p>
                          </Table.Cell>
                          <Table.Cell className="text-right text-xs text-muted-foreground">{timeAgo(a.created_at)}</Table.Cell>
                          <Table.Cell className="text-right font-mono text-[11px] text-muted-foreground">{a.ip || "—"}</Table.Cell>
                        </Table.Row>
                      ))}
                    </Table.Body>
                  </Table.Content>
                </Table.ScrollContainer>
              </Table>
            </Card.Content>
          </Card>
          <Pager page={data.page} pageSize={50} total={data.total} onPage={setPage} />
        </>
      )}
    </div>
  );
}
