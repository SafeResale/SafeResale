"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, KeyRound, RefreshCw, UserPlus } from "lucide-react";
import { patch, post, queryString } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { UserDetail } from "@/lib/types";
import { fmtDate, initials, money, timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError } from "@/components/error-state";
import { StatusBadge, statusTone } from "@/components/status-badge";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { toast } from "sonner";

const ROLES = ["seller", "buyer", "admin", "inspector"];

export default function UserDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { data, loading, error, reload } = useFetch<UserDetail>(`/admin/users/${id}`);
  const [role, setRole] = useState("");
  const [pw, setPw] = useState("");
  const [pwOpen, setPwOpen] = useState(false);
  const [confirmKind, setConfirmKind] = useState<"status" | "role" | null>(null);

  if (error) {
    return (
      <div>
        <PageHeader title="User detail" description={id} />
        <PageError message={error.message} onRetry={reload} />
      </div>
    );
  }

  if (loading || !data) {
    return (
      <div>
        <PageHeader title="User detail" description={id} />
        <div className="space-y-4"><Skeleton className="h-32 rounded-xl" /><Skeleton className="h-64 rounded-xl" /></div>
      </div>
    );
  }

  const u = data.user;

  async function toggleStatus() {
    const next = u.status === "suspended" ? "active" : "suspended";
    const ok = await runMutation(
      () => patch(`/admin/users/${id}/status`, { status: next, reason: "Manual action from admin console" }),
      { success: `User ${next}` },
    );
    setConfirmKind(null);
    if (ok) reload();
  }

  async function changeRole() {
    if (!role || role === u.role) return;
    const ok = await runMutation(() => patch(`/admin/users/${id}/role`, { role }), { success: "Role updated" });
    setConfirmKind(null);
    if (ok) {
      setRole("");
      reload();
    }
  }

  async function resetPassword(e: React.FormEvent) {
    e.preventDefault();
    const ok = await runMutation(async () => {
      await post(`/admin/users/${id}/reset-password`, { new_password: pw });
      toast.success("Password reset — existing sessions revoked");
    });
    if (ok) {
      setPw("");
      setPwOpen(false);
    }
  }

  return (
    <div>
      <PageHeader
        title={u.name || u.email}
        description={u.email}
        actions={
          <Button asChild variant="outline" size="sm">
            <Link href="/users"><ArrowLeft className="size-4" /> Back to users</Link>
          </Button>
        }
      />

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="space-y-6 xl:col-span-2">
          <Card>
            <CardContent>
              <div className="flex flex-wrap items-center gap-4">
                <Avatar className="size-14">
                  <AvatarFallback className="bg-primary text-primary-foreground text-base font-bold">{initials(u.name, u.email)}</AvatarFallback>
                </Avatar>
                <div className="min-w-0 flex-1 space-y-0.5">
                  <p className="text-lg font-semibold">{u.name || "No name"}</p>
                  <p className="text-sm text-muted-foreground">{u.email}{u.phone ? ` · ${u.phone}` : ""}</p>
                  <div className="flex flex-wrap gap-2 pt-1">
                    <StatusBadge tone={statusTone[u.status] || "neutral"} label={u.status} />
                    <StatusBadge tone="lime" label={u.role} />
                    <StatusBadge tone={u.verified ? "success" : "neutral"} label={u.verified ? "verified" : "unverified"} />
                  </div>
                </div>
                <div className="flex gap-2">
                  <Button variant={u.status === "suspended" ? "default" : "outline"} size="sm" onClick={() => setConfirmKind("status")}>
                    {u.status === "suspended" ? "Reactivate" : "Suspend"}
                  </Button>
                  <Dialog open={pwOpen} onOpenChange={setPwOpen}>
                    <DialogTrigger asChild>
                      <Button variant="outline" size="sm"><KeyRound className="size-4" /> Reset password</Button>
                    </DialogTrigger>
                    <DialogContent>
                      <DialogHeader>
                        <DialogTitle>Reset password</DialogTitle>
                        <DialogDescription>Revokes all active sessions for this user.</DialogDescription>
                      </DialogHeader>
                      <form onSubmit={resetPassword} className="space-y-4">
                        <div className="space-y-1.5">
                          <Label htmlFor="pw">New password</Label>
                          <Input id="pw" type="password" required minLength={8} value={pw} onChange={(e) => setPw(e.target.value)} />
                        </div>
                        <DialogFooter>
                          <Button type="submit" className="w-full">Reset password</Button>
                        </DialogFooter>
                      </form>
                    </DialogContent>
                  </Dialog>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Listings ({data.listings.length} shown)</CardTitle>
              <CardDescription>Created by this user</CardDescription>
            </CardHeader>
            <CardContent className="space-y-2">
              {data.listings.length === 0 && <p className="text-sm text-muted-foreground">No listings yet.</p>}
              {data.listings.map((l: any) => (
                <Link key={l._id} href={`/listings/${l._id}`} className="flex items-center gap-3 rounded-lg border bg-card p-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{l.title || "Untitled listing"}</p>
                    <p className="text-xs text-muted-foreground">{l.category} · {timeAgo(l.created_at)}</p>
                  </div>
                  <span className="text-sm font-medium tabular-nums">{money(l.price, l.currency)}</span>
                  <StatusBadge tone={statusTone[l.status] || "neutral"} label={l.status || "—"} />
                </Link>
              ))}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base"><RefreshCw className="size-4" /> Behavior signals</CardTitle>
              <CardDescription>Latest seller-behavior feature snapshot</CardDescription>
            </CardHeader>
            <CardContent>
              {data.behavior?.top_signals?.length ? (
                <div className="space-y-2">
                  {data.behavior.top_signals.map((s: any, i: number) => (
                    <div key={i} className="flex items-center justify-between rounded-lg border bg-card p-3 text-sm">
                      <span className="text-muted-foreground">{Array.isArray(s) ? String(s[0]) : String(s)}</span>
                      <span className="font-medium">{Array.isArray(s) ? String(s[1]) : ""}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No behavior features captured.</p>
              )}
            </CardContent>
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Account</CardTitle>
              <CardDescription>Joined {fmtDate(u.created_at)}</CardDescription>
            </CardHeader>
            <CardContent className="grid grid-cols-2 gap-3 text-sm">
              <Stat label="Listings" value={String(data.stats.listing_count)} />
              <Stat label="Reports against" value={String(data.stats.reports_against)} />
              <Stat label="Escrow (buyer)" value={String(data.stats.escrows_as_buyer)} />
              <Stat label="Escrow (seller)" value={String(data.stats.escrows_as_seller)} />
              <Stat label="Active sessions" value={String(data.stats.active_sessions)} />
              <Stat label="Role" value={u.role} />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Change role</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              <Select value={role} onValueChange={setRole}>
                <SelectTrigger><SelectValue placeholder="New role" /></SelectTrigger>
                <SelectContent>
                  {ROLES.filter((r) => r !== u.role).map((r) => <SelectItem key={r} value={r}>{r}</SelectItem>)}
                </SelectContent>
              </Select>
              <Button variant="outline" className="w-full" onClick={() => setConfirmKind("role")} disabled={!role}>
                Apply role
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Listings by status</CardTitle>
            </CardHeader>
            <CardContent className="space-y-1.5">
              {Object.entries(data.stats.listings_by_status).length === 0 && <p className="text-sm text-muted-foreground">None.</p>}
              {Object.entries(data.stats.listings_by_status).map(([s, n]) => (
                <div key={s} className="flex items-center justify-between text-sm">
                  <span className="capitalize">{s.replace(/_/g, " ")}</span>
                  <span className="font-medium tabular-nums">{n}</span>
                </div>
              ))}
            </CardContent>
          </Card>
        </div>
      </div>

      <ConfirmDialog
        open={confirmKind !== null}
        onOpenChange={(v) => !v && setConfirmKind(null)}
        title={
          confirmKind === "status"
            ? `${u.status === "suspended" ? "Reactivate" : "Suspend"} ${u.name || u.email}?`
            : confirmKind === "role"
              ? `Change ${u.name || u.email}'s role to "${role}"?`
              : ""
        }
        description={confirmKind === "role" ? "The user's permissions update immediately." : "Their active sessions remain valid."}
        confirmLabel={
          confirmKind === "status"
            ? u.status === "suspended"
              ? "Reactivate"
              : "Suspend"
            : confirmKind === "role"
              ? "Change role"
              : "Confirm"
        }
        destructive={confirmKind === "status" && u.status !== "suspended"}
        onConfirm={() => (confirmKind === "status" ? toggleStatus() : changeRole())}
      />
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border bg-card p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-lg font-bold tabular-nums">{value}</p>
    </div>
  );
}