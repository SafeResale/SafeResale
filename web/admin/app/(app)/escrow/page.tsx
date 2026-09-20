"use client";

import { useState } from "react";
import { RefreshCw, Wallet, TrendingUp, TrendingDown, ArrowUpRight } from "lucide-react";
import { post, queryString } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { EscrowRow, PageResult } from "@/lib/types";
import { fmtDate, money } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardAction, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { StatusBadge, statusTone } from "@/components/status-badge";

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

function KpiCard({ label, value, sub, icon: Icon, trend }: { label: string; value: React.ReactNode; sub?: string; icon?: any; trend?: "up" | "down" }) {
  return (
    <Card className="@container/card">
      <CardHeader>
        <CardDescription>{label}</CardDescription>
        <CardTitle className="text-2xl font-semibold tabular-nums @[250px]/card:text-3xl">{value}</CardTitle>
        <CardAction>
          <Badge variant="outline" className="flex items-center gap-1">
            {Icon && <Icon className="size-3.5 text-muted-foreground" />}
            {trend === "down" ? <TrendingDown className="size-3" /> : <TrendingUp className="size-3" />}
            {trend === "down" ? "Down" : "Active"}
          </Badge>
        </CardAction>
      </CardHeader>
      {sub && (
        <CardFooter className="flex-col items-start gap-1.5 text-sm">
          <div className="line-clamp-1 flex gap-2 font-medium">
            {sub} <ArrowUpRight className="size-4" />
          </div>
          <div className="text-muted-foreground">Updated just now</div>
        </CardFooter>
      )}
    </Card>
  );
}

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
    <div className="flex flex-col gap-4">
      <div className="@container/main px-4 lg:px-6">
        <PageHeader
          title="Escrow"
          description="Held funds, review states and release/refund transitions"
          actions={
            <div className="flex items-center gap-2">
              <Select value={status} onValueChange={(v) => { setStatus(v || "all"); setPage(1); }}>
                <SelectTrigger className="w-40 cursor-pointer" aria-label="Filter by status">
                  <SelectValue placeholder="All states" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all" className="cursor-pointer">All states</SelectItem>
                  {FILTERS.map((f) => (
                    <SelectItem key={f} value={f} className="cursor-pointer">
                      <span className="capitalize">{f.replace(/_/g, " ")}</span>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button variant="outline" size="icon" aria-label="Refresh" onClick={reload} className="cursor-pointer">
                <RefreshCw className="size-4" />
              </Button>
            </div>
          }
        />
      </div>

      <div className="@container/main px-4 lg:px-6 space-y-6">
        <div className="*:data-[slot=card]:from-primary/5 *:data-[slot=card]:to-card dark:*:data-[slot=card]:bg-card *:data-[slot=card]:bg-gradient-to-t *:data-[slot=card]:shadow-xs grid gap-4 sm:grid-cols-3">
          <KpiCard label="Held funds" value={money(kpis?.held)} icon={Wallet} sub="currently held" trend="up" />
          <KpiCard label="In review" value={money(kpis?.in_review)} icon={Wallet} sub="dispute / inspection review" trend="down" />
          <KpiCard label="Total in hold" value={money(kpis?.in_hold_total)} icon={Wallet} sub="held + review" trend="up" />
        </div>

        {error && <PageError message={error.message} onRetry={reload} />}

        {loading && !data && (
          <div className="rounded-md border p-4 space-y-3">
            {Array.from({ length: 8 }).map((_, i) => (
              <Skeleton key={i} className="h-11 rounded-md" />
            ))}
          </div>
        )}

        {data && data.items.length === 0 && (
          <EmptyState icon={Wallet} title="No escrows" description="Escrows appear once a buyer and seller start a protected transaction." />
        )}

        {data && data.items.length > 0 && (
          <div className="space-y-4">
            <div className="rounded-md border">
              <Table aria-label="Escrows">
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
                      <TableCell>
                        <StatusBadge tone={(statusTone[e.status] as any) || "neutral"} label={e.status.replace(/_/g, " ")} className="capitalize" />
                      </TableCell>
                      <TableCell className="font-mono text-xs">{e.listing_id?.slice(0, 12) || "—"}…</TableCell>
                      <TableCell className="font-mono text-xs">
                        {e.buyer_id?.slice(0, 8) || "—"}… / {e.seller_id?.slice(0, 8) || "—"}…
                      </TableCell>
                      <TableCell className="text-right text-xs text-muted-foreground">{fmtDate(e.created_at)}</TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-1.5">
                          {(ACTIONS_FOR[e.status] || []).map((a) => {
                            if (a.tone === "danger") {
                              return (
                                <Button key={a.key} variant="destructive" size="sm" onClick={() => setConfirm({ e, action: a.key })} className="cursor-pointer">
                                  {a.label}
                                </Button>
                              );
                            }
                            if (a.tone === "outline") {
                              return (
                                <Button key={a.key} variant="outline" size="sm" onClick={() => setConfirm({ e, action: a.key })} className="cursor-pointer">
                                  {a.label}
                                </Button>
                              );
                            }
                            return (
                              <Button key={a.key} size="sm" onClick={() => setConfirm({ e, action: a.key })} className="cursor-pointer">
                                {a.label}
                              </Button>
                            );
                          })}
                          {!ACTIONS_FOR[e.status]?.length && <span className="text-xs text-muted-foreground">—</span>}
                        </div>
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
