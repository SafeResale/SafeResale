"use client";

import { useCallback, useState } from "react";
import Link from "next/link";
import { ArrowUpRight, ShieldAlert } from "lucide-react";
import { queryString } from "@/lib/api";
import { useFetch } from "@/lib/use-fetch";
import type { PageResult } from "@/lib/types";
import { riskLabel, timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { Card, Chip, Select, ListBox, Skeleton } from "@heroui/react";

// Chip color mapping — SafeResale lime accent system
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
  active: "success",
  approved: "success",
  published: "success",
  released: "success",
  verified: "success",
  resolved: "success",
  ok: "success",
  live: "success",
  review_passed: "warning",
  warn: "warning",
  held: "info",
  pending: "warning",
  review: "warning",
  in_review: "warning",
  verifying: "warning",
  submitted: "warning",
  new: "warning",
  inspection_pending: "info",
  capturing: "neutral",
  draft: "neutral",
  expired: "neutral",
  archived: "neutral",
  dismissed: "neutral",
  refunded: "neutral",
  deactivated: "neutral",
  sold: "info",
  paid: "info",
  shipped: "info",
  delivered: "info",
  unverified: "neutral",
  suspended: "danger",
  blocked: "danger",
  restricted: "danger",
  rejected: "danger",
  disputed: "danger",
  read: "info",
};

const riskToneMap: Record<string, string> = {
  low: "success",
  medium: "warning",
  high: "danger",
};

function chipColorForStatus(status?: string): ChipColor {
  const tone = status ? statusToneMap[status] || "neutral" : "neutral";
  return toneToChipColor[tone] || "default";
}

function chipColorForRiskBand(band: string | null): ChipColor {
  if (!band) return "default";
  const tone = riskToneMap[band] || "neutral";
  return toneToChipColor[tone] || "default";
}

interface FlaggedItem {
  decision: { _id: string; listing_id: string; status: string; reason?: string; created_at?: number };
  listing: { _id: string; title?: string; category?: string; price?: number; created_at?: number } | null;
  risk?: { adjusted_score?: number; badge?: string } | null;
}

export default function QueuePage() {
  const [status, setStatus] = useState<string>("all");
  const [page, setPage] = useState(1);
  const path = `/admin/listings/flagged${queryString({ status: status === "all" ? undefined : status, page, page_size: 20 })}`;
  const { data, loading, error, reload } = useFetch<PageResult<FlaggedItem> & { items: FlaggedItem[] }>(path);

  const changeStatus = useCallback(
    (s: string) => {
      setStatus(s);
      setPage(1);
    },
    [],
  );

  return (
    <div>
      <PageHeader
        title="Moderation queue"
        description="Listings flagged by the verification orchestrator — vision, diagnostics and risk"
        actions={
          <Select
            className="w-44"
            aria-label="Filter by flag status"
            placeholder="All flags"
            value={status}
            onChange={(v) => changeStatus((v as string) || "all")}
          >
            <Select.Trigger>
              <Select.Value />
              <Select.Indicator />
            </Select.Trigger>
            <Select.Popover>
              <ListBox>
                <ListBox.Item id="all" textValue="All flags">
                  All flags
                  <ListBox.ItemIndicator />
                </ListBox.Item>
                <ListBox.Item id="review" textValue="Review">
                  Review
                  <ListBox.ItemIndicator />
                </ListBox.Item>
                <ListBox.Item id="blocked" textValue="Blocked">
                  Blocked
                  <ListBox.ItemIndicator />
                </ListBox.Item>
              </ListBox>
            </Select.Popover>
          </Select>
        }
      />

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
          <Card.Content className="p-4">
            <div className="space-y-3">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-20 rounded-xl" />
              ))}
            </div>
          </Card.Content>
        </Card>
      )}

      {data && data.items.length === 0 && (
        <EmptyState
          icon={ShieldAlert}
          title="Nothing in the queue"
          description="No flagged listings match the current filter. When the orchestrator flags an item it will appear here."
        />
      )}

      {data && data.items.length > 0 && (
        <>
          <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10 overflow-hidden">
            <Card.Content className="p-0">
              <div className="divide-y">
                {data.items.map((it) => {
                  const risk = riskLabel(it.risk?.adjusted_score);
                  return (
                    <Link
                      key={it.decision._id}
                      href={`/listings/${it.listing?._id || it.decision.listing_id}`}
                      className="flex items-center gap-4 px-4 py-3 transition-colors hover:bg-accent/50"
                    >
                      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                        <p className="truncate text-sm font-medium">{it.listing?.title || "Untitled listing"}</p>
                        <p className="text-xs text-muted-foreground">
                          {it.listing?.category || "—"} · created {timeAgo(it.listing?.created_at)} · flagged{" "}
                          {timeAgo(it.decision.created_at)}
                        </p>
                      </div>
                      {risk.band && (
                        <Chip color={chipColorForRiskBand(risk.band)} variant="soft" size="sm">
                          {it.risk?.badge ? `${it.risk.badge} (${risk.label})` : `${risk.label} risk`}
                        </Chip>
                      )}
                      <Chip color={chipColorForStatus(it.decision.status)} variant="soft" size="sm">
                        {it.decision.status}
                      </Chip>
                      <ArrowUpRight className="size-4 shrink-0 text-muted-foreground" />
                    </Link>
                  );
                })}
              </div>
            </Card.Content>
          </Card>
          <Pager page={data.page} pageSize={data.page_size} total={data.total} onPage={setPage} />
        </>
      )}
    </div>
  );
}
