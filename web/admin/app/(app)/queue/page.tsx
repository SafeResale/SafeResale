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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { StatusBadge, statusTone, riskTone } from "@/components/status-badge";
import { Button } from "@/components/ui/button";

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
    <div className="flex flex-col gap-4">
      <div className="@container/main px-4 lg:px-6">
        <PageHeader
          title="Moderation queue"
          description="Listings flagged by the verification orchestrator — vision, diagnostics and risk"
          actions={
            <Select value={status} onValueChange={(v) => changeStatus((v as string) || "all")}>
              <SelectTrigger className="w-44 cursor-pointer" aria-label="Filter by flag status">
                <SelectValue placeholder="All flags" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all" className="cursor-pointer">All flags</SelectItem>
                <SelectItem value="review" className="cursor-pointer">Review</SelectItem>
                <SelectItem value="blocked" className="cursor-pointer">Blocked</SelectItem>
              </SelectContent>
            </Select>
          }
        />
      </div>

      <div className="@container/main px-4 lg:px-6">
        {error && <PageError message={error.message} onRetry={reload} />}

        {loading && !data && (
          <div className="rounded-md border">
            <div className="p-4 space-y-3">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-12 rounded-md" />
              ))}
            </div>
          </div>
        )}

        {data && data.items.length === 0 && (
          <EmptyState
            icon={ShieldAlert}
            title="Nothing in the queue"
            description="No flagged listings match the current filter. When the orchestrator flags an item it will appear here."
          />
        )}

        {data && data.items.length > 0 && (
          <div className="space-y-4">
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Listing</TableHead>
                    <TableHead>Category</TableHead>
                    <TableHead>Risk</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>When</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((it) => {
                    const risk = riskLabel(it.risk?.adjusted_score);
                    return (
                      <TableRow key={it.decision._id}>
                        <TableCell>
                          <div className="flex flex-col">
                            <span className="max-w-[260px] truncate text-sm font-medium">
                              {it.listing?.title || "Untitled listing"}
                            </span>
                            <span className="max-w-[260px] truncate text-xs text-muted-foreground">
                              {it.listing?._id?.slice(0, 8) ?? it.decision.listing_id.slice(0, 8)}…
                            </span>
                          </div>
                        </TableCell>
                        <TableCell className="text-sm text-muted-foreground capitalize">
                          {it.listing?.category || "—"}
                        </TableCell>
                        <TableCell>
                          {risk.band ? (
                            <StatusBadge
                              tone={riskTone[risk.band] ?? "neutral"}
                              label={it.risk?.badge ? `${it.risk.badge} · ${risk.label}` : `${risk.label}`}
                            />
                          ) : (
                            <span className="text-xs text-muted-foreground">—</span>
                          )}
                        </TableCell>
                        <TableCell>
                          <StatusBadge tone={it.decision.status ? statusTone[it.decision.status] ?? "neutral" : "neutral"} label={it.decision.status} className="capitalize" />
                        </TableCell>
                        <TableCell className="text-xs text-muted-foreground whitespace-nowrap">
                          <div className="flex flex-col">
                            <span>flagged {timeAgo(it.decision.created_at)}</span>
                            <span className="text-[11px]">created {timeAgo(it.listing?.created_at)}</span>
                          </div>
                        </TableCell>
                        <TableCell className="text-right">
                          <Button variant="ghost" size="icon" className="h-8 w-8" asChild>
                            <Link href={`/listings/${it.listing?._id || it.decision.listing_id}`} aria-label="Open listing">
                              <ArrowUpRight className="size-4" />
                            </Link>
                          </Button>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
            <Pager page={data.page} pageSize={data.page_size} total={data.total} onPage={setPage} />
          </div>
        )}
      </div>
    </div>
  );
}
