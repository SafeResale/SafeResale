"use client";

import * as React from "react";
import { useCallback, useMemo, useState } from "react";
import Link from "next/link";
import type { ColumnDef } from "@tanstack/react-table";
import {
  flexRender,
  getCoreRowModel,
  getFacetedRowModel,
  getFacetedUniqueValues,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
} from "@tanstack/react-table";
import { Image as ImageIcon, Package, RefreshCw, Search, X } from "lucide-react";
import { queryString } from "@/lib/api";
import { useFetch } from "@/lib/use-fetch";
import type { ListingItem, PageResult } from "@/lib/types";
import { fmtNumber, fmtShort, money, riskLabel, timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { StatusBadge, statusTone, riskTone } from "@/components/status-badge";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { DataTablePagination } from "@/components/data-table/data-table-pagination";
import { DataTableViewOptions } from "@/components/data-table/data-table-view-options";

/* ── Column definitions ────────────────────────────── */

function useListingColumns(): ColumnDef<ListingItem>[] {
  return useMemo<ColumnDef<ListingItem>[]>(
    () => [
      {
        id: "select",
        header: ({ table }) => (
          <Checkbox
            checked={
              table.getIsAllPageRowsSelected()
                ? true
                : table.getIsSomePageRowsSelected()
                  ? "indeterminate"
                  : false
            }
            onCheckedChange={(value) => table.toggleAllPageRowsSelected(!!value)}
            aria-label="Select all"
            className="translate-y-[2px] cursor-pointer"
          />
        ),
        cell: ({ row }) => (
          <Checkbox
            checked={row.getIsSelected()}
            onCheckedChange={(value) => row.toggleSelected(!!value)}
            aria-label="Select row"
            className="translate-y-[2px] cursor-pointer"
          />
        ),
        enableSorting: false,
        enableHiding: false,
      },
      {
        id: "title",
        accessorFn: (row) => row.listing.title ?? "",
        header: ({ column }) => <DataTableColumnHeader column={column} title="Title" />,
        cell: ({ row }) => {
          const it = row.original;
          return (
            <Link href={`/listings/${it.listing._id}`} className="group block max-w-56">
              <p className="truncate font-medium transition-colors group-hover:text-primary">
                {it.listing.title || "Untitled listing"}
              </p>
              <p className="flex items-center gap-1 truncate text-xs text-muted-foreground">
                <span className="truncate">{timeAgo(it.listing.created_at)}</span>
                {it.image_count ? (
                  <>
                    <span>·</span>
                    <span className="inline-flex items-center gap-0.5">
                      {fmtNumber(it.image_count)} <ImageIcon className="size-3" />
                    </span>
                  </>
                ) : null}
              </p>
            </Link>
          );
        },
        enableHiding: false,
      },
      {
        id: "category",
        accessorFn: (row) => row.listing.category ?? "",
        header: ({ column }) => <DataTableColumnHeader column={column} title="Category" />,
        cell: ({ row }) => {
          const cat = row.original.listing.category;
          if (!cat) return <span className="text-xs text-muted-foreground">—</span>;
          return (
            <Badge variant="outline" className="capitalize">
              {cat.replace(/_/g, " ")}
            </Badge>
          );
        },
        filterFn: (row, id, value) => {
          if (!value || value === "all") return true;
          return String(row.getValue(id)).toLowerCase() === String(value).toLowerCase();
        },
      },
      {
        id: "price",
        accessorFn: (row) => row.listing.price ?? 0,
        header: ({ column }) => <DataTableColumnHeader column={column} title="Price" />,
        cell: ({ row }) => {
          const it = row.original;
          return <span className="tabular-nums">{money(it.listing.price, it.listing.currency)}</span>;
        },
      },
      {
        id: "status",
        accessorFn: (row) => row.listing.status ?? "",
        header: ({ column }) => <DataTableColumnHeader column={column} title="Status" />,
        cell: ({ row }) => {
          const it = row.original;
          return (
            <div className="min-w-28">
              <StatusBadge
                tone={it.listing.status ? statusTone[it.listing.status] ?? "neutral" : "neutral"}
                label={it.listing.status ? it.listing.status.replace(/_/g, " ") : "—"}
                className="capitalize"
              />
              {it.decision?.reason ? (
                <p className="mt-0.5 max-w-40 truncate text-xs text-muted-foreground">
                  {it.decision.reason}
                </p>
              ) : null}
            </div>
          );
        },
        filterFn: (row, id, value) => {
          if (!value || value === "all") return true;
          return String(row.getValue(id)).toLowerCase() === String(value).toLowerCase();
        },
      },
      {
        id: "risk",
        accessorFn: (row) => row.risk?.adjusted_score ?? -1,
        header: ({ column }) => <DataTableColumnHeader column={column} title="Risk" />,
        cell: ({ row }) => {
          const it = row.original;
          const info = riskLabel(it.risk?.adjusted_score);
          if (!info.band) return <span className="text-xs text-muted-foreground">no score</span>;
          // Show risk band + score only — badge (verified/restricted) is listing-level and shown in Status column; mixing caused "restricted · 15 / 100" for low-risk drafts
          const bandLabel = info.band ? info.band.charAt(0).toUpperCase() + info.band.slice(1) : "";
          return (
            <StatusBadge
              tone={riskTone[info.band] ?? "neutral"}
              label={bandLabel ? `${bandLabel} · ${info.label}` : info.label}
            />
          );
        },
      },
      {
        id: "seller",
        accessorFn: (row) => row.seller?.name ?? row.seller?.email ?? "",
        header: ({ column }) => <DataTableColumnHeader column={column} title="Seller" />,
        cell: ({ row }) => {
          const s = row.original.seller;
          if (!s) return <span className="text-xs text-muted-foreground">—</span>;
          return (
            <div className="max-w-40">
              <span className="block truncate text-sm">{s.name || "—"}</span>
              <span className="block max-w-40 truncate text-xs text-muted-foreground">{s.email}</span>
            </div>
          );
        },
      },
      {
        id: "created",
        accessorFn: (row) => row.listing.created_at ?? 0,
        header: ({ column }) => <DataTableColumnHeader column={column} title="Created" />,
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-xs text-muted-foreground">
            {fmtShort(row.original.listing.created_at)}
          </span>
        ),
      },
      {
        id: "actions",
        header: () => <span className="text-xs font-medium text-muted-foreground">Actions</span>,
        cell: ({ row }) => (
          <Button asChild variant="ghost" size="sm" className="h-8">
            <Link href={`/listings/${row.original.listing._id}`}>View</Link>
          </Button>
        ),
        enableSorting: false,
        enableHiding: false,
      },
    ],
    []
  );
}

/* ── Page ────────────────────────────────────────── */

export default function ListingsPage() {
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("all");
  const [category, setCategory] = useState("all");
  const [page, setPage] = useState(1);

  const [sorting, setSorting] = useState<import("@tanstack/react-table").SortingState>([]);
  const [columnVisibility, setColumnVisibility] = useState<import("@tanstack/react-table").VisibilityState>({});
  const [columnFilters, setColumnFilters] = useState<import("@tanstack/react-table").ColumnFiltersState>([]);
  const [rowSelection, setRowSelection] = useState({});

  const path = useMemo(
    () =>
      `/admin/listings${queryString({
        q: search || undefined,
        // server supports `q`, `status`, and optionally `category` / `risk`
        status: status === "all" ? undefined : status,
        category: category === "all" ? undefined : category,
        page,
        page_size: 25,
      })}`,
    [search, status, category, page]
  );

  const { data, loading, error, reload } = useFetch<PageResult<ListingItem>>(path);

  const commit = useCallback(() => {
    setSearch(q.trim());
    setPage(1);
  }, [q]);

  const hasActiveFilter = search !== "" || status !== "all" || category !== "all" || q !== "";

  const resetFilters = useCallback(() => {
    setQ("");
    setSearch("");
    setStatus("all");
    setCategory("all");
    setPage(1);
  }, []);

  const columns = useListingColumns();
  const rows = data?.items ?? [];

  // derive category options from server payload when available
  const categoryOptions = useMemo(() => {
    const fromData = Array.from(
      new Set(rows.map((r) => r.listing.category).filter(Boolean) as string[])
    ).sort();
    if (fromData.length) return fromData;
    return ["electronics", "vehicles", "fashion", "home", "collectibles", "other"];
  }, [rows]);

  const statusOptions = useMemo(() => {
    if (data?.statuses?.length) return data.statuses;
    return ["draft", "capturing", "submitted", "verifying", "approved", "review", "blocked", "published", "restricted", "inspection_pending"];
  }, [data?.statuses]);

  const table = useReactTable({
    data: rows,
    columns,
    state: { sorting, columnVisibility, rowSelection, columnFilters },
    enableRowSelection: true,
    onSortingChange: setSorting,
    onColumnFiltersChange: setColumnFilters,
    onColumnVisibilityChange: setColumnVisibility,
    onRowSelectionChange: setRowSelection,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFacetedRowModel: getFacetedRowModel(),
    getFacetedUniqueValues: getFacetedUniqueValues(),
  });

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <div className="@container/main px-4 lg:px-6">
        <PageHeader
          title="Listings"
          description="Full marketplace catalog with enrichment — risk, decisions, evidence counts"
        />
      </div>

      <div className="@container/main px-4 lg:px-6">
        {/* Filters + table block */}
        <Card className="@container/card overflow-hidden rounded-xl border bg-card shadow-sm">
          <CardContent className="pt-6">
            {error && <PageError message={error.message} onRetry={reload} />}

            {loading && !data ? (
              <div className="space-y-3 py-2">
                {Array.from({ length: 8 }).map((_, i) => (
                  <Skeleton key={i} className="h-12 rounded-xl" />
                ))}
              </div>
            ) : data && data.items.length === 0 ? (
              <EmptyState
                icon={Package}
                title="No listings found"
                description="Adjust the filters or create a draft listing to see rows here."
              />
            ) : (
              <div className="space-y-4">
                {/* Toolbar — template pattern: faceted selects + search + view options */}
                <div className="space-y-4">
                  <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                    <Select
                      value={status}
                      onValueChange={(v) => {
                        setStatus(v || "all");
                        setPage(1);
                      }}
                    >
                      <SelectTrigger className="w-full cursor-pointer" aria-label="Filter by status">
                        <SelectValue placeholder="Status" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="all" className="cursor-pointer">
                          All statuses
                        </SelectItem>
                        {statusOptions.map((s) => (
                          <SelectItem key={s} value={s} className="cursor-pointer capitalize">
                            {s.replace(/_/g, " ")}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>

                    <Select
                      value={category}
                      onValueChange={(v) => {
                        setCategory(v || "all");
                        setPage(1);
                      }}
                    >
                      <SelectTrigger className="w-full cursor-pointer" aria-label="Filter by category">
                        <SelectValue placeholder="Category" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="all" className="cursor-pointer">
                          All categories
                        </SelectItem>
                        {categoryOptions.map((c) => (
                          <SelectItem key={c} value={c} className="cursor-pointer capitalize">
                            {c.replace(/_/g, " ")}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>

                    <Button
                      variant="outline"
                      onClick={reload}
                      className="w-full cursor-pointer md:w-auto"
                      aria-label="Refresh"
                    >
                      <RefreshCw className="size-4" />
                      Refresh
                    </Button>
                  </div>

                  <div className="flex items-center justify-between gap-2">
                    <div className="flex flex-1 items-center gap-2">
                      <form
                        className="relative flex-1 sm:max-w-[320px]"
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
                          placeholder="Search title…"
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
                      <Button
                        variant="outline"
                        onClick={resetFilters}
                        className="px-3 cursor-pointer"
                        disabled={!hasActiveFilter}
                      >
                        <RefreshCw className="size-4" />
                        <span className="hidden lg:inline">Reset</span>
                      </Button>
                    </div>
                    <DataTableViewOptions table={table} />
                  </div>
                </div>

                {/* Table */}
                <div className="rounded-md border">
                  <Table>
                    <TableHeader>
                      {table.getHeaderGroups().map((headerGroup) => (
                        <TableRow key={headerGroup.id}>
                          {headerGroup.headers.map((header) => (
                            <TableHead key={header.id} colSpan={header.colSpan}>
                              {header.isPlaceholder
                                ? null
                                : flexRender(header.column.columnDef.header, header.getContext())}
                            </TableHead>
                          ))}
                        </TableRow>
                      ))}
                    </TableHeader>
                    <TableBody>
                      {table.getRowModel().rows?.length ? (
                        table.getRowModel().rows.map((row) => (
                          <TableRow key={row.id} data-state={row.getIsSelected() && "selected"}>
                            {row.getVisibleCells().map((cell) => (
                              <TableCell key={cell.id}>
                                {flexRender(cell.column.columnDef.cell, cell.getContext())}
                              </TableCell>
                            ))}
                          </TableRow>
                        ))
                      ) : (
                        <TableRow>
                          <TableCell colSpan={columns.length} className="h-24 text-center">
                            No results.
                          </TableCell>
                        </TableRow>
                      )}
                    </TableBody>
                  </Table>
                </div>

                {/* Pagination — server-aware, styled like template */}
                <DataTablePagination
                  table={table}
                  server={
                    data
                      ? {
                          page: data.page,
                          pageSize: data.page_size,
                          total: data.total,
                          onPageChange: setPage,
                        }
                      : undefined
                  }
                />
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
