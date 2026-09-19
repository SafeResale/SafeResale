"use client";

import { useCallback, useState } from "react";
import Link from "next/link";
import { Image as ImageIcon, Package, RefreshCw } from "lucide-react";
import { queryString } from "@/lib/api";
import { useFetch } from "@/lib/use-fetch";
import type { ListingItem, PageResult } from "@/lib/types";
import { fmtNumber, fmtShort, money, riskLabel, timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { Card, Chip, Button, Table, Select, ListBox, SearchField, Skeleton } from "@heroui/react";

// Map old Tone/statusBadge tones to HeroUI Chip color
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
        <SearchField
          aria-label="Search listings"
          className="flex-1 sm:max-w-xs"
          value={q}
          onChange={setQ}
          onSubmit={commit}
          onClear={() => {
            setQ("");
            setSearch("");
            setPage(1);
          }}
        >
          <SearchField.Group>
            <SearchField.SearchIcon />
            <SearchField.Input placeholder="Search title or description…" />
            <SearchField.ClearButton />
          </SearchField.Group>
        </SearchField>
        <Select
          className="w-40"
          placeholder="Status"
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
              <ListBox.Item id="all" textValue="All statuses">
                All statuses
                <ListBox.ItemIndicator />
              </ListBox.Item>
              {(data?.statuses || ["draft", "capturing", "submitted", "verifying", "approved", "review", "blocked", "published", "restricted", "inspection_pending"]).map((s) => (
                <ListBox.Item key={s} id={s} textValue={s}>
                  {s.replace(/_/g, " ")}
                  <ListBox.ItemIndicator />
                </ListBox.Item>
              ))}
            </ListBox>
          </Select.Popover>
        </Select>
        <Select
          className="w-40"
          placeholder="Risk"
          aria-label="Filter by risk"
          value={risk}
          onChange={(v) => {
            setRisk((v as string) || "all");
            setPage(1);
          }}
        >
          <Select.Trigger>
            <Select.Value />
            <Select.Indicator />
          </Select.Trigger>
          <Select.Popover>
            <ListBox>
              <ListBox.Item id="all" textValue="All risk levels">
                All risk levels
                <ListBox.ItemIndicator />
              </ListBox.Item>
              <ListBox.Item id="low" textValue="Low">
                Low
                <ListBox.ItemIndicator />
              </ListBox.Item>
              <ListBox.Item id="medium" textValue="Medium">
                Medium
                <ListBox.ItemIndicator />
              </ListBox.Item>
              <ListBox.Item id="high" textValue="High">
                High
                <ListBox.ItemIndicator />
              </ListBox.Item>
            </ListBox>
          </Select.Popover>
        </Select>
        <Button variant="secondary" isIconOnly aria-label="Refresh" onPress={reload}>
          <RefreshCw className="size-4" />
        </Button>
      </div>

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
          <Card.Content className="p-4">
            <div className="space-y-3">
              {Array.from({ length: 8 }).map((_, i) => (
                <Skeleton key={i} className="h-12 rounded-xl" />
              ))}
            </div>
          </Card.Content>
        </Card>
      )}

      {data && data.items.length === 0 && (
        <EmptyState icon={Package} title="No listings found" description="Adjust the filters or create a draft listing to see rows here." />
      )}

      {data && data.items.length > 0 && (
        <>
          <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10 overflow-hidden">
            <Card.Content className="p-0">
              <Table>
                <Table.ScrollContainer>
                  <Table.Content aria-label="Listings" className="min-w-[720px]">
                    <Table.Header>
                      <Table.Column isRowHeader>Listing</Table.Column>
                      <Table.Column>Price</Table.Column>
                      <Table.Column>Risk</Table.Column>
                      <Table.Column>Decision</Table.Column>
                      <Table.Column>Seller</Table.Column>
                      <Table.Column className="text-right">Created</Table.Column>
                    </Table.Header>
                    <Table.Body>
                      {data.items.map((it) => {
                        const riskInfo = riskLabel(it.risk?.adjusted_score);
                        return (
                          <Table.Row key={it.listing._id} id={it.listing._id}>
                            <Table.Cell>
                              <Link href={`/listings/${it.listing._id}`} className="group">
                                <p className="max-w-56 truncate font-medium transition-colors group-hover:text-primary">{it.listing.title || "Untitled listing"}</p>
                                <p className="text-xs text-muted-foreground">
                                  {it.listing.category} · {fmtNumber(it.image_count)} <ImageIcon className="inline size-3" /> · {timeAgo(it.listing.created_at)}
                                </p>
                              </Link>
                            </Table.Cell>
                            <Table.Cell className="tabular-nums">{money(it.listing.price, it.listing.currency)}</Table.Cell>
                            <Table.Cell>
                              {riskInfo.band ? (
                                <Chip color={chipColorForRiskBand(riskInfo.band)} variant="soft" size="sm">
                                  {it.risk?.badge ? `${it.risk.badge} · ${riskInfo.label}` : riskInfo.label}
                                </Chip>
                              ) : (
                                <span className="text-xs text-muted-foreground">no score</span>
                              )}
                            </Table.Cell>
                            <Table.Cell>
                              <Chip color={chipColorForStatus(it.listing.status)} variant="soft" size="sm">
                                {it.listing.status || "—"}
                              </Chip>
                              {it.decision && it.decision.reason && <p className="mt-0.5 max-w-40 truncate text-xs text-muted-foreground">{it.decision.reason}</p>}
                            </Table.Cell>
                            <Table.Cell className="max-w-40">
                              <span className="block truncate text-sm">{it.seller?.name || "—"}</span>
                              <span className="block max-w-40 truncate text-xs text-muted-foreground">{it.seller?.email}</span>
                            </Table.Cell>
                            <Table.Cell className="text-right text-xs text-muted-foreground">{fmtShort(it.listing.created_at)}</Table.Cell>
                          </Table.Row>
                        );
                      })}
                    </Table.Body>
                  </Table.Content>
                </Table.ScrollContainer>
              </Table>
            </Card.Content>
          </Card>
          <Pager page={data.page} pageSize={data.page_size} total={data.total} onPage={setPage} />
        </>
      )}
    </div>
  );
}
