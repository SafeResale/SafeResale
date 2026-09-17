"use client";

import { useState } from "react";
import { RefreshCw, Wallet } from "lucide-react";
import { post, queryString } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { EscrowRow, PageResult } from "@/lib/types";
import { fmtDate, money, timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { StatusBadge, statusTone } from "@/components/status-badge";
import { StatCard } from "@/components/stat-card";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

const FILTERS = ["pending", "held", "in_review", "released", "refunded"];

const ACTIONS_FOR: Record<string, { key: string; label: string; tone: "default" | "outline" | "danger" }[]> = {
  pending: [
    { key: "hold", label: "Hold", tone: "default" },
    { key: "refund", label: "Refund", tone: "danger" },
  ],
  held: [
    { key: "review", label: "Review", tone: "outline" },
    { key: "release", label: "Release", tone: "default" },
    { key: "refund", label: "Refund", tone: "danger" },
  ],
  in_review: [
    { key: "release", label: "Release", tone: "default" },
    { key: "refund", label: "Refund", tone: "danger" },
  ],
  released: [],
  refunded: [],
};

export default function EscrowPage() {
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(1);
  const [confirm, setConfirm] = useState<{ e: EscrowRow; action: string } | null>(null);
  const { data: kpis } = useFetch<{ held: number; in_review: number; in_hold_total: number; counts: Record<string, number> }>("/admin/escrows/kpis");
  const { data, loading, error, reload } = useFetch<PageResult<EscrowRow>>(`/admin/escrows${queryString({ status: status === "all" ? undefined : status, page, page_size: 20 })}`);

  async function applyAction() {
    if (!confirm) return;
    const { e, action } = confirm;
    const ok = await runMutation(() => post(`/admin/escrows/${e._id}/${action}`), { success: `Escrow ${action}d` });
    setConfirm(null);
    if (ok) reload();
  }

  return (
    <div>
      <PageHeader
        title="Escrow"
        description="Held funds, review states and release/refund transitions"
        actions={
          <Select value={status} onValueChange={(v) => { setStatus(v); setPage(1); }}>
            <SelectTrigger className="w-40"><SelectValue placeholder="Status" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All states</SelectItem>
              {FILTERS.map((f) => <SelectItem key={f} value={f}>{f.replace(/_/g, " ")}</SelectItem>)}
            </SelectContent>
          </Select>
        }
      />

      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        <StatCard label="Held funds" value={money(kpis?.held)} icon={Wallet} tone="info" sub="currently held" />
        <StatCard label="In review" value={money(kpis?.in_review)} sub="dispute / inspection review" />
        <StatCard label="Total in hold" value={money(kpis?.in_hold_total)} sub="held + review" tone="lime" />
      </div>

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <Card className="p-4"><div className="space-y-3">{Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-11" />)}</div></Card>
      )}

      {data && data.items.length === 0 && (
        <EmptyState icon={Wallet} title="No escrows" description="Escrows appear once a buyer and seller start a protected transaction." />
      )}

      {data && data.items.length > 0 && (
        <>
          <div className="overflow-hidden rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Escrow</TableHead>
                  <TableHead>Amount</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Listing</TableHead>
                  <TableHead>Buyer / Seller</TableHead>
                  <TableHead className="text-right">Created</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((e) => (
                  <TableRow key={e._id}>
                    <TableCell className="font-mono text-xs">{e._id.slice(0, 12)}…</TableCell>
                    <TableCell className="font-medium tabular-nums">{money(e.amount, e.currency)}</TableCell>
                    <TableCell><StatusBadge tone={statusTone[e.status] || "neutral"} label={e.status.replace(/_/g, " ")} /></TableCell>
                    <TableCell className="font-mono text-xs">{e.listing_id?.slice(0, 12) || "—"}…</TableCell>
                    <TableCell className="font-mono text-xs">{e.buyer_id?.slice(0, 8) || "—"}… / {e.seller_id?.slice(0, 8) || "—"}…</TableCell>
                    <TableCell className="text-right text-xs text-muted-foreground">{fmtDate(e.created_at)}</TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1.5">
                        {(ACTIONS_FOR[e.status] || []).map((a) => (
                          <Button key={a.key} variant={a.tone === "danger" ? "destructive" : a.tone} size="sm" onClick={() => setConfirm({ e, action: a.key })}>
                            {a.label}
                          </Button>
                        ))}
                        {!ACTIONS_FOR[e.status]?.length && <span className="text-xs text-muted-foreground">—</span>}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Pager page={data.page} pageSize={data.page_size} total={data.total} onPage={setPage} />
        </>
      )}

      <ConfirmDialog
        open={confirm !== null}
        onOpenChange={(v) => !v && setConfirm(null)}
        title={confirm ? `Apply "${confirm.action.replace(/_/g, " ")}"?` : ""}
        description={confirm ? `Escrow ${confirm.e._id.slice(0, 8)}… will move to "${confirm.action.replace(/_/g, " ")}".` : ""}
        confirmLabel="Confirm"
        destructive={confirm?.action === "refund"}
        onConfirm={applyAction}
      />
    </div>
  );
}