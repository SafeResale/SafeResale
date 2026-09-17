"use client";

import { useState } from "react";
import Link from "next/link";
import { RefreshCw, Search, UserPlus, Users as UsersIcon } from "lucide-react";
import { post, queryString, ApiError } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { PageResult, UserRow } from "@/lib/types";
import { fmtShort, initials } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { StatusBadge, statusTone } from "@/components/status-badge";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { toast } from "sonner";

const ROLES = ["seller", "buyer", "admin", "inspector"];

export default function UsersPage() {
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [role, setRole] = useState("all");
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(1);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ name: "", email: "", password: "", role: "seller", phone: "" });

  const path = `/admin/users${queryString({
    q: search,
    role: role === "all" ? undefined : role,
    status: status === "all" ? undefined : status,
    page,
    page_size: 25,
  })}`;
  const { data, loading, error, reload } = useFetch<PageResult<UserRow>>(path);

  async function createUser(e: React.FormEvent) {
    e.preventDefault();
    const ok = await runMutation(
      async () => {
        const d = await post("/admin/users", { ...form, email: form.email.trim().toLowerCase() });
        toast.success(`Created ${d.user.name}`);
      },
      { success: undefined },
    );
    if (ok) {
      setOpen(false);
      setForm({ name: "", email: "", password: "", role: "seller", phone: "" });
      reload();
    }
  }

  return (
    <div>
      <PageHeader
        title="Users"
        description="Customers, sellers, admins and inspectors — manage roles and status"
        actions={
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
              <Button><UserPlus className="size-4" /> Add user</Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Add user</DialogTitle>
                <DialogDescription>Creates an account; the user can sign in immediately.</DialogDescription>
              </DialogHeader>
              <form onSubmit={createUser} className="space-y-4">
                <div className="space-y-1.5">
                  <Label htmlFor="u-name">Name</Label>
                  <Input id="u-name" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Jane Doe" />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="u-email">Email</Label>
                  <Input id="u-email" type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} placeholder="jane@example.com" />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="u-password">Password</Label>
                  <Input id="u-password" type="password" required minLength={8} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} placeholder="Min 8 characters" />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="u-phone">Phone (optional)</Label>
                  <Input id="u-phone" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} placeholder="+1 555 000 0000" />
                </div>
                <div className="space-y-1.5">
                  <Label>Role</Label>
                  <Select value={form.role} onValueChange={(v) => setForm({ ...form, role: v })}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {ROLES.map((r) => <SelectItem key={r} value={r}>{r}</SelectItem>)}
                    </SelectContent>
                  </Select>
                </div>
                <DialogFooter>
                  <Button type="submit" className="w-full">Create user</Button>
                </DialogFooter>
              </form>
            </DialogContent>
          </Dialog>
        }
      />

      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center">
        <div className="relative flex-1 sm:max-w-xs">
          <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input className="pl-8" placeholder="Search name or email…" value={q} onChange={(e) => setQ(e.target.value)} onKeyDown={(e) => e.key === "Enter" && (setSearch(q.trim()), setPage(1))} />
        </div>
        <Select value={role} onValueChange={(v) => { setRole(v); setPage(1); }}>
          <SelectTrigger className="w-36"><SelectValue placeholder="Role" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All roles</SelectItem>
            {ROLES.map((r) => <SelectItem key={r} value={r}>{r}</SelectItem>)}
          </SelectContent>
        </Select>
        <Select value={status} onValueChange={(v) => { setStatus(v); setPage(1); }}>
          <SelectTrigger className="w-36"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All statuses</SelectItem>
            <SelectItem value="active">Active</SelectItem>
            <SelectItem value="suspended">Suspended</SelectItem>
          </SelectContent>
        </Select>
        <Button variant="outline" size="icon" onClick={reload} aria-label="Refresh"><RefreshCw className="size-4" /></Button>
      </div>

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <Card className="p-4"><div className="space-y-3">{Array.from({ length: 7 }).map((_, i) => <Skeleton key={i} className="h-11" />)}</div></Card>
      )}

      {data && data.items.length === 0 && (
        <EmptyState icon={UsersIcon} title="No users found" description="Adjust filters or create a new user." />
      )}

      {data && data.items.length > 0 && (
        <>
          <div className="overflow-hidden rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>User</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Verified</TableHead>
                  <TableHead className="text-right">Listings</TableHead>
                  <TableHead className="text-right">Joined</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((u) => (
                  <TableRow key={u._id}>
                    <TableCell>
                      <Link href={`/users/${u._id}`} className="group flex items-center gap-3">
                        <Avatar className="size-8">
                          <AvatarFallback className="bg-muted text-xs font-semibold">{initials(u.name, u.email)}</AvatarFallback>
                        </Avatar>
                        <span className="min-w-0">
                          <span className="block truncate font-medium group-hover:text-primary">{u.name || "—"}</span>
                          <span className="block max-w-52 truncate text-xs text-muted-foreground">{u.email}</span>
                        </span>
                      </Link>
                    </TableCell>
                    <TableCell><span className="text-sm capitalize">{u.role}</span></TableCell>
                    <TableCell><StatusBadge tone={statusTone[u.status] || "neutral"} label={u.status} /></TableCell>
                    <TableCell><StatusBadge tone={u.verified ? "success" : "neutral"} label={u.verified ? "yes" : "no"} /></TableCell>
                    <TableCell className="text-right tabular-nums">{u.listing_count ?? 0}</TableCell>
                    <TableCell className="text-right text-xs text-muted-foreground">{fmtShort(u.created_at)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Pager page={data.page} pageSize={data.page_size} total={data.total} onPage={setPage} />
        </>
      )}
    </div>
  );
}