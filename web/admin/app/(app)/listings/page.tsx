"use client";

import { useCallback, useState } from "react";
import Link from "next/link";
import { Image as ImageIcon, Package, RefreshCw, Search } from "lucide-react";
import { queryString } from "@/lib/api";
import { useFetch } from "@/lib/use-fetch";
import type { ListingItem, PageResult } from "@/lib/types";
import { fmtNumber, fmtShort, money, riskLabel, timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { StatusBadge, riskTone, statusTone } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

export default function ListingsPage() {
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("all");
  const [risk, setRisk] = useState("all");
  const [page, setPage] = useState(1);

  const path = `/admin/listings${queryString({
    q: search,
    status: status === "all" ? undefined : status,
    risk: risk === "all" ? undefined : risk,
    page,
    page_size: 25,
  })}`;
  const { data, loading, error, reload } = useFetch<PageResult<ListingItem>>(path);

  const commit = useCallback(() => {
    setSearch(q.trim());
    setPage(1);
  }, [q]);

  return (
    <div>
      <PageHeader title="Listings" description="Full marketplace catalog with enrichment — risk, decisions, evidence counts" />

      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center">
        <div className="relative flex-1 sm:max-w-xs">
          <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-8"
            placeholder="Search title or description…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && commit()}
          />
        </div>
        <Select value={status} onValueChange={(v) => { setStatus(v); setPage(1); }}>
          <SelectTrigger className="w-40"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All statuses</SelectItem>
            {(data?.statuses || ["draft", "capturing", "submitted", "verifying", "approved", "review", "blocked", "published", "restricted", "inspection_pending"]).map((s) => (
              <SelectItem key={s} value={s}>{s.replace(/_/g, " ")}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={risk} onValueChange={(v) => { setRisk(v); setPage(1); }}>
          <SelectTrigger className="w-40"><SelectValue placeholder="Risk" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All risk levels</SelectItem>
            <SelectItem value="low">Low</SelectItem>
            <SelectItem value="medium">Medium</SelectItem>
            <SelectItem value="high">High</SelectItem>
          </SelectContent>
        </Select>
        <Button variant="outline" size="icon" onClick={reload} aria-label="Refresh"><RefreshCw className="size-4" /></Button>
      </div>

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <Card className="p-4">
          <div className="space-y-3">
            {Array.from({ length: 8 }).map((_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        </Card>
      )}

      {data && data.items.length === 0 && (
        <EmptyState icon={Package} title="No listings found" description="Adjust the filters or create a draft listing to see rows here." />
      )}

      {data && data.items.length > 0 && (
        <>
          <div className="overflow-hidden rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Listing</TableHead>
                  <TableHead>Price</TableHead>
                  <TableHead>Risk</TableHead>
                  <TableHead>Decision</TableHead>
                  <TableHead>Seller</TableHead>
                  <TableHead className="text-right">Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((it) => {
                  const risk = riskLabel(it.risk?.adjusted_score);
                  return (
                    <TableRow key={it.listing._id}>
                      <TableCell>
                        <Link href={`/listings/${it.listing._id}`} className="group">
                          <p className="max-w-56 truncate font-medium transition-colors group-hover:text-primary">{it.listing.title || "Untitled listing"}</p>
                          <p className="text-xs text-muted-foreground">
                            {it.listing.category} · {fmtNumber(it.image_count)} <ImageIcon className="inline size-3" /> · {timeAgo(it.listing.created_at)}
                          </p>
                        </Link>
                      </TableCell>
                      <TableCell className="tabular-nums">{money(it.listing.price, it.listing.currency)}</TableCell>
                      <TableCell>
                        {risk.band ? (
                          <StatusBadge tone={riskTone[risk.band]} label={it.risk?.badge ? `${it.risk.badge} · ${risk.label}` : `${risk.label}`} />
                        ) : (
                          <span className="text-xs text-muted-foreground">no score</span>
                        )}
                      </TableCell>
                      <TableCell>
                        <StatusBadge tone={statusTone[it.listing.status] || "neutral"} label={it.listing.status || "—"} />
                        {it.decision && it.decision.reason && <p className="mt-0.5 max-w-40 truncate text-xs text-muted-foreground">{it.decision.reason}</p>}
                      </TableCell>
                      <TableCell className="max-w-40">
                        <span className="block truncate text-sm">{it.seller?.name || "—"}</span>
                        <span className="block max-w-40 truncate text-xs text-muted-foreground">{it.seller?.email}</span>
                      </TableCell>
                      <TableCell className="text-right text-xs text-muted-foreground">{fmtShort(it.listing.created_at)}</TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
          <Pager page={data.page} pageSize={data.page_size} total={data.total} onPage={setPage} />
        </>
      )}
    </div>
  );
}