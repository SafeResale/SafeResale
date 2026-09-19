"use client";

import { useState } from "react";
import { RefreshCw, Wallet } from "lucide-react";
import { post, queryString } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { EscrowRow, PageResult } from "@/lib/types";
import { fmtDate, money } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Button, Card, Chip, ListBox, Select, Skeleton, Table } from "@heroui/react";

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

type ChipColor = "default" | "accent" | "success" | "warning" | "danger";

const toneToChipColor: Record<string, ChipColor> = {
  success: "success",
  warning: "warning",
  danger: "danger",
  lime: "accent",
  info: "accent",
  neutral: "default",
  outline: "default",
  accent: "accent",
};

const statusToneMap: Record<string, string> = {
  pending: "warning",
  held: "info",
  in_review: "warning",
  released: "success",
  refunded: "neutral",
  active: "success",
  approved: "success",
  published: "success",
  verified: "success",
  resolved: "success",
  capturing: "neutral",
  draft: "neutral",
  expired: "neutral",
  archived: "neutral",
  suspended: "danger",
  blocked: "danger",
  rejected: "danger",
  disputed: "danger",
};

function chipColorForStatus(status?: string): ChipColor {
  const tone = status ? statusToneMap[status] || "neutral" : "neutral";
  return toneToChipColor[tone] || "default";
}

function KpiCard({ label, value, sub, icon: Icon, tone = "default" }: { label: string; value: React.ReactNode; sub?: string; icon?: any; tone?: "lime" | "danger" | "info" | "default" | "success" | "warning" }) {
  const toneMap: Record<string, string> = {
    lime: "bg-accent text-accent-foreground",
    success: "bg-success text-success-foreground",
    danger: "bg-danger text-danger-foreground",
    warning: "bg-warning text-warning-foreground",
    info: "bg-accent text-accent-foreground",
    default: "bg-default text-default-foreground",
  };
  return (
    <Card className="relative overflow-hidden rounded-2xl border-0 shadow-sm ring-1 ring-black/5 dark:ring-white/10">
      <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-black/5 to-transparent dark:via-white/10" />
      <Card.Header className="pb-2">
        <div className="flex items-start justify-between">
          <Card.Description className="text-[11px] font-semibold uppercase tracking-widest">{label}</Card.Description>
          {Icon && (
            <span className={`inline-flex size-9 items-center justify-center rounded-xl text-xs ${toneMap[tone]}`}>
              <Icon className="size-4" />
            </span>
          )}
        </div>
      </Card.Header>
      <Card.Content className="pt-0">
        <div className="text-2xl font-bold tracking-tight tabular-nums">{value}</div>
        {sub && <p className="text-xs text-muted-foreground mt-1">{sub}</p>}
      </Card.Content>
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
    <div>
      <PageHeader
        title="Escrow"
        description="Held funds, review states and release/refund transitions"
        actions={
          <div className="flex items-center gap-2">
            <Select
              className="w-40"
              placeholder="All states"
              aria-label="Filter by status"
              value={status}
              onChange={(v) => {
                setStatus((v as string) || "all");
                setPage(1);
              }}
            >
              <Select.Trigger>
                <Select.Value />
                <Select.Indicator />
              </Select.Trigger>
              <Select.Popover>
                <ListBox>
                  <ListBox.Item id="all" textValue="All states">
                    All states
                    <ListBox.ItemIndicator />
                  </ListBox.Item>
                  {FILTERS.map((f) => (
                    <ListBox.Item key={f} id={f} textValue={f}>
                      <span className="capitalize">{f.replace(/_/g, " ")}</span>
                      <ListBox.ItemIndicator />
                    </ListBox.Item>
                  ))}
                </ListBox>
              </Select.Popover>
            </Select>
            <Button variant="secondary" isIconOnly aria-label="Refresh" onPress={reload}>
              <RefreshCw className="size-4" />
            </Button>
          </div>
        }
      />

      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        <KpiCard label="Held funds" value={money(kpis?.held)} icon={Wallet} tone="info" sub="currently held" />
        <KpiCard label="In review" value={money(kpis?.in_review)} sub="dispute / inspection review" tone="warning" />
        <KpiCard label="Total in hold" value={money(kpis?.in_hold_total)} sub="held + review" tone="lime" />
      </div>

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <Card className="rounded-2xl p-4 ring-1 ring-black/5 dark:ring-white/10">
          <Card.Content className="space-y-3 p-0">
            {Array.from({ length: 8 }).map((_, i) => (
              <Skeleton key={i} className="h-11 rounded-xl" />
            ))}
          </Card.Content>
        </Card>
      )}

      {data && data.items.length === 0 && (
        <EmptyState icon={Wallet} title="No escrows" description="Escrows appear once a buyer and seller start a protected transaction." />
      )}

      {data && data.items.length > 0 && (
        <>
          <Card className="overflow-hidden rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
            <Card.Content className="p-0">
              <Table>
                <Table.ScrollContainer>
                  <Table.Content aria-label="Escrows" className="min-w-[860px]">
                    <Table.Header>
                      <Table.Column isRowHeader>Escrow</Table.Column>
                      <Table.Column>Amount</Table.Column>
                      <Table.Column>Status</Table.Column>
                      <Table.Column>Listing</Table.Column>
                      <Table.Column>Buyer / Seller</Table.Column>
                      <Table.Column className="text-right">Created</Table.Column>
                      <Table.Column className="text-right">Actions</Table.Column>
                    </Table.Header>
                    <Table.Body>
                      {data.items.map((e) => (
                        <Table.Row key={e._id} id={e._id}>
                          <Table.Cell className="font-mono text-xs">{e._id.slice(0, 12)}…</Table.Cell>
                          <Table.Cell className="font-medium tabular-nums">{money(e.amount, e.currency)}</Table.Cell>
                          <Table.Cell>
                            <Chip color={chipColorForStatus(e.status)} variant="soft" size="sm" className="capitalize">
                              {e.status.replace(/_/g, " ")}
                            </Chip>
                          </Table.Cell>
                          <Table.Cell className="font-mono text-xs">{e.listing_id?.slice(0, 12) || "—"}…</Table.Cell>
                          <Table.Cell className="font-mono text-xs">
                            {e.buyer_id?.slice(0, 8) || "—"}… / {e.seller_id?.slice(0, 8) || "—"}…
                          </Table.Cell>
                          <Table.Cell className="text-right text-xs text-muted-foreground">{fmtDate(e.created_at)}</Table.Cell>
                          <Table.Cell>
                            <div className="flex justify-end gap-1.5">
                              {(ACTIONS_FOR[e.status] || []).map((a) => {
                                if (a.tone === "danger") {
                                  return (
                                    <Button key={a.key} variant="danger" size="sm" onPress={() => setConfirm({ e, action: a.key })}>
                                      {a.label}
                                    </Button>
                                  );
                                }
                                if (a.tone === "outline") {
                                  return (
                                    <Button key={a.key} variant="secondary" size="sm" onPress={() => setConfirm({ e, action: a.key })}>
                                      {a.label}
                                    </Button>
                                  );
                                }
                                return (
                                  <Button
                                    key={a.key}
                                    variant="primary"
                                    size="sm"
                                    className="bg-accent text-accent-foreground hover:bg-accent/90"
                                    onPress={() => setConfirm({ e, action: a.key })}
                                  >
                                    {a.label}
                                  </Button>
                                );
                              })}
                              {!ACTIONS_FOR[e.status]?.length && <span className="text-xs text-muted-foreground">—</span>}
                            </div>
                          </Table.Cell>
                        </Table.Row>
                      ))}
                    </Table.Body>
                  </Table.Content>
                </Table.ScrollContainer>
              </Table>
            </Card.Content>
          </Card>
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
