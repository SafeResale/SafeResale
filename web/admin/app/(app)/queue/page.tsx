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
import { Card, CardContent } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { StatusBadge, statusTone, riskTone } from "@/components/status-badge";

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
          <Select value={status} onValueChange={(v) => changeStatus((v as string) || "all")}>
            <SelectTrigger className="w-44" aria-label="Filter by flag status">
              <SelectValue placeholder="All flags" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All flags</SelectItem>
              <SelectItem value="review">Review</SelectItem>
              <SelectItem value="blocked">Blocked</SelectItem>
            </SelectContent>
          </Select>
        }
      />

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <Card className="rounded-2xl p-0 ring-1 ring-black/5 dark:ring-white/10">
          <CardContent className="p-4">
            <div className="space-y-3">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-20 rounded-xl" />
              ))}
            </div>
          </CardContent>
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
          <Card className="rounded-2xl p-0 ring-1 ring-black/5 dark:ring-white/10 overflow-hidden">
            <CardContent className="p-0">
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
                        <StatusBadge tone={riskTone[risk.band] ?? "neutral"} label={it.risk?.badge ? `${it.risk.badge} (${risk.label})` : `${risk.label} risk`} />
                      )}
                      <StatusBadge tone={it.decision.status ? statusTone[it.decision.status] ?? "neutral" : "neutral"} label={it.decision.status} />
                      <ArrowUpRight className="size-4 shrink-0 text-muted-foreground" />
                    </Link>
                  );
                })}
              </div>
            </CardContent>
          </Card>
          <Pager page={data.page} pageSize={data.page_size} total={data.total} onPage={setPage} />
        </>
      )}
    </div>
  );
}