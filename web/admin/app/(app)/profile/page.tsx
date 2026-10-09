"use client";

import { useState } from "react";
import { CircleUser, KeyRound, LogOut } from "lucide-react";
import { clearSession, post, getSessionUser } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { AuditEntry, PageResult } from "@/lib/types";
import { fmtDate } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError } from "@/components/error-state";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from "@/components/ui/table";

export default function ProfilePage() {
  const me = getSessionUser();
  const [pw, setPw] = useState({ current: "", next: "", confirm: "" });
  const [pwdError, setPwdError] = useState("");
  const [saving, setSaving] = useState(false);
  const { data, loading, error } = useFetch<PageResult<AuditEntry>>("/admin/audit-logs?page=1&page_size=8");

  async function changePassword(e: React.FormEvent) {
    e.preventDefault();
    setPwdError("");
    if (pw.next.length < 8) return setPwdError("New password must be at least 8 characters.");
    if (pw.next !== pw.confirm) return setPwdError("Passwords do not match.");
    setSaving(true);
    const ok = await runMutation(
      () => post("/auth/change-password", { current_password: pw.current, new_password: pw.next }),
      { success: "Password updated" },
    );
    setSaving(false);
    if (ok) setPw({ current: "", next: "", confirm: "" });
  }

  return (
    <div className="flex flex-col gap-4 min-w-0">
      <div className="@container/main px-4 lg:px-6">
        <PageHeader title="My account" description="Your admin profile and security" />
      </div>

      <div className="@container/main px-4 lg:px-6 min-w-0">
        <div className="grid gap-4 lg:grid-cols-2 min-w-0">
          <Card className="@container/card rounded-xl border shadow-sm">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base"><CircleUser className="size-4" /> Profile</CardTitle>
            <CardDescription>Details attached to your session</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex gap-2"><dt className="w-24 shrink-0 text-muted-foreground">Name</dt><dd className="font-medium">{me?.name || "—"}</dd></div>
            <div className="flex gap-2"><dt className="w-24 shrink-0 text-muted-foreground">Email</dt><dd className="font-medium">{me?.email || "—"}</dd></div>
            <div className="flex gap-2 items-center"><dt className="w-24 shrink-0 text-muted-foreground">Role</dt><dd><StatusBadge tone="lime" label={me?.role || "admin"} className="capitalize" /></dd></div>
            <div className="flex gap-2 items-center justify-between border-t pt-3">
              <div>
                <dt className="text-muted-foreground">Sign out</dt>
                <dd className="text-xs text-muted-foreground">Clears the session and returns to the login screen</dd>
              </div>
              <Button variant="secondary" onClick={() => { clearSession(); window.location.href = "/login"; }}>
                <LogOut className="size-4" /> Sign out
              </Button>
            </div>
          </CardContent>
        </Card>

        <Card className="@container/card rounded-xl border shadow-sm">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base"><KeyRound className="size-4" /> Change password</CardTitle>
            <CardDescription>Choose at least 8 characters. Active sessions will be signed out.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={changePassword} className="space-y-3">
              <div className="space-y-1.5">
                <Label htmlFor="cur">Current password</Label>
                <Input id="cur" type="password" required value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="next">New password</Label>
                <Input id="next" type="password" required value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="confirm">Confirm new password</Label>
                <Input id="confirm" type="password" required value={pw.confirm} onChange={(e) => setPw({ ...pw, confirm: e.target.value })} />
              </div>
              {pwdError && <p className="text-xs text-destructive">{pwdError}</p>}
              <Button type="submit" variant="default" className="w-full bg-accent text-accent-foreground hover:bg-accent/90" disabled={saving}>
                {saving ? "Updating…" : "Update password"}
              </Button>
            </form>
          </CardContent>
        </Card>
        </div>

        <Card className="@container/card mt-4 overflow-hidden rounded-xl border shadow-sm">
        <CardHeader>
          <CardTitle className="text-base">Recent activity</CardTitle>
          <CardDescription>Your latest actions from the audit trail</CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          {loading && <div className="space-y-2 p-4">{Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-10 rounded-md" />)}</div>}
          {error && <div className="p-4"><PageError message={error.message} /></div>}
          {data && data.items.length === 0 && <p className="p-4 text-sm text-muted-foreground">No activity recorded yet.</p>}
          {data && data.items.length > 0 && (
            <div className="overflow-hidden rounded-lg border">
              <div className="overflow-x-auto">
                <Table aria-label="Recent activity" className="min-w-[560px]">
                <TableHeader>
                  <TableRow>
                    <TableHead>Action</TableHead>
                    <TableHead>Target</TableHead>
                    <TableHead className="text-right">When</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.slice(0, 8).map((a) => (
                    <TableRow key={a._id}>
                      <TableCell><StatusBadge tone="lime" label={a.action} className="font-mono text-xs" /></TableCell>
                      <TableCell className="text-xs text-muted-foreground">{a.target_type || "—"}</TableCell>
                      <TableCell className="text-right text-xs text-muted-foreground">{fmtDate(a.created_at)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
      </div>
    </div>
  );
}