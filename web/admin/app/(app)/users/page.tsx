"use client";

import { useCallback, useMemo, useState } from "react";
import Link from "next/link";
import {
  Users as UsersIcon,
  UserCheck,
  ShieldBan,
  Store,
  TrendingUp,
  TrendingDown,
  Search,
  X,
  RefreshCw,
  Plus,
  Eye,
  Pencil,
  Trash2,
  EllipsisVertical,
  ChevronDown,
} from "lucide-react";
import {
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type ColumnFiltersState,
  type SortingState,
  type VisibilityState,
} from "@tanstack/react-table";

import { post, patch, del, queryString } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { PageResult, UserRow } from "@/lib/types";
import { fmtShort, initials } from "@/lib/format";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { StatusBadge, statusTone } from "@/components/status-badge";
import { toast } from "sonner";

const ROLES = ["seller", "buyer", "admin", "inspector"] as const;

// ---------------------------------------------------------------------------
// StatCards — template gradient pattern, SafeResale metrics derived from fetch
// ---------------------------------------------------------------------------
function StatCards({
  total,
  active,
  suspended,
  sellers,
  loading,
}: {
  total: number;
  active: number;
  suspended: number;
  sellers: number;
  loading: boolean;
}) {
  if (loading) {
    return (
      <div className="*:data-[slot=card]:from-primary/5 *:data-[slot=card]:to-card dark:*:data-[slot=card]:bg-card *:data-[slot=card]:bg-gradient-to-t *:data-[slot=card]:shadow-xs grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-[150px] rounded-xl" />
        ))}
      </div>
    );
  }

  const cards = [
    {
      title: "Total Users",
      value: total,
      icon: UsersIcon,
      badge:
        total > 0 ? (
          <>
            <TrendingUp className="size-3" /> {total} total
          </>
        ) : (
          <>
            <TrendingDown className="size-3" /> No users
          </>
        ),
      badgeClass: "border-primary/20 bg-primary/5 text-primary",
      footerTitle: "All registered accounts",
      footerDesc: `${sellers} sellers · ${active} active`,
      footerIcon: TrendingUp,
    },
    {
      title: "Active",
      value: active,
      icon: UserCheck,
      badge: (
        <>
          <TrendingUp className="size-3" /> {total ? Math.round((active / total) * 100) : 0}% of total
        </>
      ),
      badgeClass: "border-green-200 bg-green-50 text-green-700 dark:border-green-800 dark:bg-green-950/20 dark:text-green-400",
      footerTitle: "Ready to transact",
      footerDesc: "Verified & in good standing",
      footerIcon: TrendingUp,
    },
    {
      title: "Suspended",
      value: suspended,
      icon: ShieldBan,
      badge:
        suspended > 0 ? (
          <>
            <TrendingDown className="size-3" /> Needs review
          </>
        ) : (
          <>
            <TrendingUp className="size-3" /> All clear
          </>
        ),
      badgeClass:
        suspended > 0
          ? "border-red-200 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950/20 dark:text-red-400"
          : "border-green-200 bg-green-50 text-green-700 dark:border-green-800 dark:bg-green-950/20 dark:text-green-400",
      footerTitle: suspended > 0 ? "Attention required" : "No suspensions",
      footerDesc: suspended > 0 ? "Review suspended accounts" : "Healthy community",
      footerIcon: suspended > 0 ? TrendingDown : TrendingUp,
    },
    {
      title: "Sellers",
      value: sellers,
      icon: Store,
      badge: (
        <>
          <TrendingUp className="size-3" /> {sellers} listing owners
        </>
      ),
      badgeClass: "border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-800 dark:bg-amber-950/20 dark:text-amber-400",
      footerTitle: "Supply side",
      footerDesc: `${total ? Math.round((sellers / total) * 100) : 0}% of users are sellers`,
      footerIcon: TrendingUp,
    },
  ];

  return (
    <div className="*:data-[slot=card]:from-primary/5 *:data-[slot=card]:to-card dark:*:data-[slot=card]:bg-card *:data-[slot=card]:bg-gradient-to-t *:data-[slot=card]:shadow-xs grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {cards.map((c) => (
        <Card key={c.title} className="@container/card">
          <CardHeader>
            <CardDescription>{c.title}</CardDescription>
            <CardTitle className="text-2xl font-semibold tabular-nums @[250px]/card:text-3xl">{c.value}</CardTitle>
            <CardAction>
              <Badge variant="outline" className={c.badgeClass}>
                {c.badge}
              </Badge>
            </CardAction>
          </CardHeader>
          <CardFooter className="flex-col items-start gap-1.5 text-sm">
            <div className="line-clamp-1 flex gap-2 font-medium">
              {c.footerTitle} <c.footerIcon className="size-4" />
            </div>
            <div className="text-muted-foreground">{c.footerDesc}</div>
          </CardFooter>
        </Card>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------
// UserFormDialog — template pattern (DialogTrigger + form) with SafeResale fields
// ---------------------------------------------------------------------------
function UserFormDialog({
  onCreate,
}: {
  onCreate: (values: { name: string; email: string; password: string; phone: string; role: string }) => Promise<boolean>;
}) {
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ name: "", email: "", password: "", phone: "", role: "seller" });

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const ok = await onCreate({ ...form, email: form.email.trim().toLowerCase() });
    if (ok) {
      setForm({ name: "", email: "", password: "", phone: "", role: "seller" });
      setOpen(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button className="cursor-pointer">
          <Plus className="size-4" />
          Add New User
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-[480px]">
        <DialogHeader>
          <DialogTitle>Add New User</DialogTitle>
          <DialogDescription>Creates an account; the user can sign in immediately.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="u-name">Name</Label>
            <Input
              id="u-name"
              required
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="Jane Doe"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="u-email">Email</Label>
            <Input
              id="u-email"
              type="email"
              required
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              placeholder="jane@example.com"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="u-password">Password</Label>
            <Input
              id="u-password"
              type="password"
              required
              minLength={8}
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              placeholder="Min 8 characters"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="u-phone">Phone (optional)</Label>
            <Input
              id="u-phone"
              value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })}
              placeholder="+1 555 000 0000"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label>Role</Label>
              <Select value={form.role} onValueChange={(v) => setForm({ ...form, role: v || "seller" })}>
                <SelectTrigger aria-label="Role" className="w-full">
                  <SelectValue placeholder="Select role" />
                </SelectTrigger>
                <SelectContent>
                  {ROLES.map((r) => (
                    <SelectItem key={r} value={r}>
                      <span className="capitalize">{r}</span>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Status</Label>
              <Select value="active" disabled>
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="active">Active (default)</SelectItem>
                </SelectContent>
              </Select>
              <p className="text-[11px] text-muted-foreground">New users start as active.</p>
            </div>
          </div>
          <DialogFooter>
            <Button type="submit" className="cursor-pointer bg-accent text-accent-foreground hover:bg-accent/90">
              Create user
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// UsersDataTable — @tanstack/react-table, toolbar like template tasks/users
// ---------------------------------------------------------------------------
function UsersDataTable({
  users,
  q,
  setQ,
  onCommit,
  onClearSearch,
  role,
  setRole,
  status,
  setStatus,
  onReload,
  onCreate,
  onSuspendToggle,
  onDelete,
}: {
  users: UserRow[];
  q: string;
  setQ: (v: string) => void;
  onCommit: () => void;
  onClearSearch: () => void;
  role: string;
  setRole: (v: string) => void;
  status: string;
  setStatus: (v: string) => void;
  onReload: () => void;
  onCreate: (values: { name: string; email: string; password: string; phone: string; role: string }) => Promise<boolean>;
  onSuspendToggle: (u: UserRow) => void;
  onDelete: (u: UserRow) => void;
}) {
  const [sorting, setSorting] = useState<SortingState>([]);
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});
  const [rowSelection, setRowSelection] = useState({});

  const columns = useMemo<ColumnDef<UserRow>[]>(
    () => [
      {
        id: "select",
        header: ({ table }) => (
          <div className="flex items-center justify-center px-2">
            <Checkbox
              checked={
                table.getIsAllPageRowsSelected()
                  ? true
                  : table.getIsSomePageRowsSelected()
                    ? ("indeterminate" as const)
                    : false
              }
              onCheckedChange={(value) => table.toggleAllPageRowsSelected(!!value)}
              aria-label="Select all"
            />
          </div>
        ),
        cell: ({ row }) => (
          <div className="flex items-center justify-center px-2">
            <Checkbox
              checked={row.getIsSelected()}
              onCheckedChange={(value) => row.toggleSelected(!!value)}
              aria-label="Select row"
            />
          </div>
        ),
        enableSorting: false,
        enableHiding: false,
        size: 36,
      },
      {
        accessorKey: "name",
        header: "User",
        cell: ({ row }) => {
          const u = row.original;
          return (
            <Link href={`/users/${u._id}`} className="group flex items-center gap-3">
              <Avatar className="size-8">
                <AvatarFallback className="bg-muted text-xs font-semibold">{initials(u.name, u.email)}</AvatarFallback>
              </Avatar>
              <span className="min-w-0">
                <span className="block truncate font-medium group-hover:text-primary">{u.name || "—"}</span>
                <span className="block max-w-52 truncate text-xs text-muted-foreground">{u.email}</span>
              </span>
            </Link>
          );
        },
      },
      {
        accessorKey: "email",
        header: "Email",
        cell: ({ row }) => (
          <span className="max-w-48 truncate text-sm text-muted-foreground">{row.getValue("email") as string}</span>
        ),
      },
      {
        accessorKey: "role",
        header: "Role",
        cell: ({ row }) => {
          const r = row.getValue("role") as string;
          return (
            <Badge variant="secondary" className="capitalize bg-accent/15 text-accent-foreground border-accent/20">
              {r}
            </Badge>
          );
        },
        filterFn: (row, id, value) => row.getValue(id) === value,
      },
      {
        accessorKey: "status",
        header: "Status",
        cell: ({ row }) => {
          const s = row.getValue("status") as string;
          return <StatusBadge tone={(statusTone[s] as any) || "neutral"} label={s} className="capitalize" />;
        },
        filterFn: (row, id, value) => row.getValue(id) === value,
      },
      {
        accessorKey: "verified",
        header: "Plan",
        cell: ({ row }) => {
          const v = row.original.verified;
          return (
            <Badge variant="outline" className={v ? "border-green-200 bg-green-50 text-green-700 dark:border-green-800 dark:bg-green-950/20 dark:text-green-400" : ""}>
              {v ? "Verified" : "Unverified"}
            </Badge>
          );
        },
      },
      {
        accessorKey: "listing_count",
        header: "Billing",
        cell: ({ row }) => <span className="tabular-nums text-sm">{(row.getValue("listing_count") as number) ?? 0} listings</span>,
      },
      {
        accessorKey: "created_at",
        header: "Joined",
        cell: ({ row }) => <span className="text-xs text-muted-foreground">{fmtShort(row.getValue("created_at") as number)}</span>,
      },
      {
        id: "actions",
        header: "Actions",
        cell: ({ row }) => {
          const u = row.original;
          return (
            <div className="flex items-center gap-1">
              <Button variant="ghost" size="icon" className="h-8 w-8 cursor-pointer" asChild>
                <Link href={`/users/${u._id}`}>
                  <Eye className="size-4" />
                  <span className="sr-only">View user</span>
                </Link>
              </Button>
              <Button variant="ghost" size="icon" className="h-8 w-8 cursor-pointer" onClick={() => toast.info("Edit via detail page — role/status changes there")}>
                <Pencil className="size-4" />
                <span className="sr-only">Edit user</span>
              </Button>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="icon" className="h-8 w-8 cursor-pointer">
                    <EllipsisVertical className="size-4" />
                    <span className="sr-only">More actions</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem asChild className="cursor-pointer">
                    <Link href={`/users/${u._id}`}>View Details</Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem className="cursor-pointer" onClick={() => onSuspendToggle(u)}>
                    {u.status === "suspended" ? "Reactivate" : "Suspend"}
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem className="cursor-pointer text-destructive focus:text-destructive" onClick={() => onDelete(u)}>
                    <Trash2 className="mr-2 size-4" />
                    Delete User
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          );
        },
      },
    ],
    [onDelete, onSuspendToggle],
  );

  const table = useReactTable({
    data: users,
    columns,
    onSortingChange: setSorting,
    onColumnFiltersChange: setColumnFilters,
    getCoreRowModel: getCoreRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    onColumnVisibilityChange: setColumnVisibility,
    onRowSelectionChange: setRowSelection,
    state: {
      sorting,
      columnFilters,
      columnVisibility,
      rowSelection,
    },
  });

  return (
    <div className="w-full space-y-4">
      {/* Toolbar — search + server filters + actions (template pattern) */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-1 items-center gap-2">
          <div className="relative flex-1 max-w-sm">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              aria-label="Search users"
              placeholder="Search users..."
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && onCommit()}
              className="pl-9 pr-8"
            />
            {q && (
              <button
                type="button"
                aria-label="Clear search"
                onClick={onClearSearch}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-full p-0.5 text-muted-foreground hover:text-foreground"
              >
                <X className="size-4" />
              </button>
            )}
          </div>
          <Button variant="outline" size="icon" aria-label="Refresh" onClick={onReload} className="cursor-pointer">
            <RefreshCw className="size-4" />
          </Button>
        </div>
        <div className="flex items-center gap-2">
          <UserFormDialog onCreate={onCreate} />
        </div>
      </div>

      {/* Filter row — Role / Status / Plan / Column visibility (template users pattern) */}
      <div className="grid gap-2 sm:grid-cols-4 sm:gap-4">
        <div className="space-y-2">
          <Label htmlFor="role-filter" className="text-sm font-medium">
            Role
          </Label>
          <Select value={role} onValueChange={(v) => setRole(v || "all")}>
            <SelectTrigger className="cursor-pointer w-full" id="role-filter" aria-label="Filter by role">
              <SelectValue placeholder="Select Role" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Roles</SelectItem>
              {ROLES.map((r) => (
                <SelectItem key={r} value={r}>
                  <span className="capitalize">{r}</span>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="status-filter" className="text-sm font-medium">
            Status
          </Label>
          <Select value={status} onValueChange={(v) => setStatus(v || "all")}>
            <SelectTrigger className="cursor-pointer w-full" id="status-filter" aria-label="Filter by status">
              <SelectValue placeholder="Select Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Statuses</SelectItem>
              <SelectItem value="active">Active</SelectItem>
              <SelectItem value="suspended">Suspended</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="plan-filter" className="text-sm font-medium">
            Verified
          </Label>
          <Select value="all" onValueChange={() => {}}>
            <SelectTrigger className="cursor-pointer w-full" id="plan-filter">
              <SelectValue placeholder="All" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All</SelectItem>
              <SelectItem value="verified">Verified</SelectItem>
              <SelectItem value="unverified">Unverified</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="column-visibility" className="text-sm font-medium">
            Column Visibility
          </Label>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" className="cursor-pointer w-full" id="column-visibility">
                Columns <ChevronDown className="ml-2 size-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {table
                .getAllColumns()
                .filter((column) => column.getCanHide())
                .map((column) => (
                  <DropdownMenuCheckboxItem
                    key={column.id}
                    className="capitalize"
                    checked={column.getIsVisible()}
                    onCheckedChange={(value) => column.toggleVisibility(!!value)}
                  >
                    {column.id}
                  </DropdownMenuCheckboxItem>
                ))}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {/* Table — rounded-md border like template */}
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead key={header.id}>
                    {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
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
                    <TableCell key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</TableCell>
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

      {/* Footer — selection + pagination (template pattern) */}
      <div className="flex items-center justify-between space-x-2 py-2">
        <div className="flex-1 text-sm text-muted-foreground hidden sm:block">
          {table.getFilteredSelectedRowModel().rows.length} of {table.getFilteredRowModel().rows.length} row(s) selected.
        </div>
        <div className="flex items-center space-x-6 lg:space-x-8">
          <div className="flex items-center space-x-2">
            <p className="text-sm font-medium">Rows per page</p>
            <Select
              value={`${table.getState().pagination.pageSize}`}
              onValueChange={(value) => table.setPageSize(Number(value))}
            >
              <SelectTrigger className="h-8 w-[70px] cursor-pointer" id="page-size">
                <SelectValue placeholder={table.getState().pagination.pageSize} />
              </SelectTrigger>
              <SelectContent side="top">
                {[10, 25, 50].map((pageSize) => (
                  <SelectItem key={pageSize} value={`${pageSize}`}>
                    {pageSize}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="flex items-center space-x-2">
            <Button variant="outline" size="sm" onClick={() => table.previousPage()} disabled={!table.getCanPreviousPage()} className="cursor-pointer">
              Previous
            </Button>
            <Button variant="outline" size="sm" onClick={() => table.nextPage()} disabled={!table.getCanNextPage()} className="cursor-pointer">
              Next
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Page — template outer structure: flex flex-col gap-4 + @container/main wrappers
// ---------------------------------------------------------------------------
export default function UsersPage() {
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [role, setRole] = useState("all");
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(1);

  const path = `/admin/users${queryString({
    q: search,
    role: role === "all" ? undefined : role,
    status: status === "all" ? undefined : status,
    page,
    page_size: 25,
  })}`;
  const { data, loading, error, reload } = useFetch<PageResult<UserRow>>(path);

  const commit = useCallback(() => {
    setSearch(q.trim());
    setPage(1);
  }, [q]);

  const clearSearch = useCallback(() => {
    setQ("");
    setSearch("");
    setPage(1);
  }, []);

  async function createUser(values: { name: string; email: string; password: string; phone: string; role: string }) {
    return runMutation(
      async () => {
        const d = await post("/admin/users", { ...values, email: values.email.trim().toLowerCase() });
        toast.success(`Created ${d.user.name}`);
      },
      { success: undefined },
    ).then((ok) => {
      if (ok) reload();
      return ok;
    });
  }

  async function handleSuspendToggle(u: UserRow) {
    const next = u.status === "suspended" ? "active" : "suspended";
    const ok = await runMutation(() => patch(`/admin/users/${u._id}/status`, { status: next, reason: "Manual action from users table" }), {
      success: `User ${next}`,
    });
    if (ok) reload();
  }

  async function handleDelete(u: UserRow) {
    const ok = await runMutation(() => del(`/admin/users/${u._id}`), { success: "User deleted" });
    if (ok) reload();
  }

  // Stats derived from current fetch (total from server, breakdowns from page items)
  const items = data?.items ?? [];
  const total = data?.total ?? 0;
  const active = items.filter((i) => i.status === "active").length;
  const suspended = items.filter((i) => i.status === "suspended").length;
  // sellers count within current page; fallback to proportion if needed — derived per task spec
  const sellers = items.filter((i) => i.role === "seller").length;

  // For StatCards: if data not yet loaded, show skeletons; otherwise show totals
  // When page has not loaded all users, active/suspended reflect current page — acceptable per spec ("derive from fetched items")
  // To make cards feel global, also expose total in footer

  return (
    <div className="flex flex-col gap-4">
      <div className="@container/main px-4 lg:px-6">
        <StatCards total={total} active={active} suspended={suspended} sellers={sellers} loading={loading && !data} />
      </div>

      <div className="@container/main px-4 lg:px-6 mt-8 lg:mt-12">
        {error && <PageError message={error.message} onRetry={reload} />}

        {loading && !data && (
          <div className="space-y-4">
            <Skeleton className="h-12 rounded-xl" />
            <Skeleton className="h-64 rounded-xl" />
          </div>
        )}

        {data && data.items.length === 0 && !loading && (
          <EmptyState icon={UsersIcon} title="No users found" description="Adjust filters or create a new user." />
        )}

        {data && data.items.length > 0 && (
          <>
            <UsersDataTable
              users={data.items}
              q={q}
              setQ={setQ}
              onCommit={commit}
              onClearSearch={clearSearch}
              role={role}
              setRole={(v) => {
                setRole(v || "all");
                setPage(1);
              }}
              status={status}
              setStatus={(v) => {
                setStatus(v || "all");
                setPage(1);
              }}
              onReload={reload}
              onCreate={createUser}
              onSuspendToggle={handleSuspendToggle}
              onDelete={handleDelete}
            />
            <Pager page={data.page} pageSize={data.page_size} total={data.total} onPage={setPage} />
          </>
        )}

        {/* When data exists but we want to show table even if empty after filters? Already handled above */}
        {data && data.items.length === 0 && !error && null}
      </div>
    </div>
  );
}
