"use client";

import { useCallback, useState } from "react";
import Link from "next/link";
import { Image as ImageIcon, Package, RefreshCw, Search, X } from "lucide-react";
import { queryString } from "@/lib/api";
import { useFetch } from "@/lib/use-fetch";
import type { ListingItem, PageResult } from "@/lib/types";
import { fmtNumber, fmtShort, money, riskLabel, timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { StatusBadge, statusTone, riskTone } from "@/components/status-badge";

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
        <form
          className="relative flex-1 sm:max-w-xs"
          role="search"
          onSubmit={(e) => {
            e.preventDefault();
            commit();
          }}
        >
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            aria-label="Search listings"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search title or description…"
            className="pl-9 pr-8"
          />
          {q && (
            <button
              type="button"
              aria-label="Clear search"
              className="absolute right-2 top-1/2 -translate-y-1/2 rounded-full p-1 text-muted-foreground transition-colors hover:text-foreground"
              onClick={() => {
                setQ("");
                setSearch("");
                setPage(1);
              }}
            >
              <X className="size-3.5" />
            </button>
          )}
        </form>
        <Select
          value={status}
          onValueChange={(v) => {
            setStatus((v as string) || "all");
            setPage(1);
          }}
        >
          <SelectTrigger className="w-40" aria-label="Filter by status">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All statuses</SelectItem>
            {(data?.statuses || ["draft", "capturing", "submitted", "verifying", "approved", "review", "blocked", "published", "restricted", "inspection_pending"]).map((s) => (
              <SelectItem key={s} value={s}>
                {s.replace(/_/g, " ")}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={risk}
          onValueChange={(v) => {
            setRisk((v as string) || "all");
            setPage(1);
          }}
        >
          <SelectTrigger className="w-40" aria-label="Filter by risk">
            <SelectValue placeholder="Risk" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All risk levels</SelectItem>
            <SelectItem value="low">Low</SelectItem>
            <SelectItem value="medium">Medium</SelectItem>
            <SelectItem value="high">High</SelectItem>
          </SelectContent>
        </Select>
        <Button variant="secondary" size="icon" aria-label="Refresh" onClick={reload}>
          <RefreshCw className="size-4" />
        </Button>
      </div>

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <Card className="rounded-2xl p-0 ring-1 ring-black/5 dark:ring-white/10">
          <CardContent className="p-4">
            <div className="space-y-3">
              {Array.from({ length: 8 }).map((_, i) => (
                <Skeleton key={i} className="h-12 rounded-xl" />
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {data && data.items.length === 0 && (
        <EmptyState icon={Package} title="No listings found" description="Adjust the filters or create a draft listing to see rows here." />
      )}

      {data && data.items.length > 0 && (
        <>
          <Card className="rounded-2xl p-0 ring-1 ring-black/5 dark:ring-white/10 overflow-hidden">
            <CardContent className="p-0">
              <div className="overflow-x-auto">
                <Table className="min-w-[720px]">
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
                      const riskInfo = riskLabel(it.risk?.adjusted_score);
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
                            {riskInfo.band ? (
                              <StatusBadge tone={riskTone[riskInfo.band] ?? "neutral"} label={it.risk?.badge ? `${it.risk.badge} · ${riskInfo.label}` : riskInfo.label} />
                            ) : (
                              <span className="text-xs text-muted-foreground">no score</span>
                            )}
                          </TableCell>
                          <TableCell>
                            <StatusBadge tone={it.listing.status ? statusTone[it.listing.status] ?? "neutral" : "neutral"} label={it.listing.status || "—"} />
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
            </CardContent>
          </Card>
          <Pager page={data.page} pageSize={data.page_size} total={data.total} onPage={setPage} />
        </>
      )}
    </div>
  );
}