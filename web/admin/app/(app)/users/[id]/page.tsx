"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, KeyRound, RefreshCw } from "lucide-react";
import { patch, post } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { UserDetail } from "@/lib/types";
import { fmtDate, initials, money, timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError } from "@/components/error-state";
import {
  AlertDialog,
  Avatar,
  Button,
  Card,
  Chip,
  Input,
  Label,
  ListBox,
  Modal,
  Select,
  Skeleton,
} from "@heroui/react";
import { toast } from "sonner";

const ROLES = ["seller", "buyer", "admin", "inspector"];

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
  live: "success",
  pending: "warning",
  review: "warning",
  in_review: "warning",
  verifying: "warning",
  submitted: "warning",
  new: "warning",
  inspection_pending: "info",
  draft: "neutral",
  expired: "neutral",
  archived: "neutral",
  dismissed: "neutral",
  deactivated: "neutral",
  unverified: "neutral",
  suspended: "danger",
  blocked: "danger",
  restricted: "danger",
  rejected: "danger",
};

function chipColorForStatus(status?: string): ChipColor {
  const tone = status ? statusToneMap[status] || "neutral" : "neutral";
  return toneToChipColor[tone] || "default";
}

export default function UserDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
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
        <div className="space-y-4">
          <Skeleton className="h-32 rounded-2xl" />
          <Skeleton className="h-64 rounded-2xl" />
        </div>
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
          <Button variant="secondary" size="sm" onPress={() => router.push("/users")}>
            <ArrowLeft className="size-4" /> Back to users
          </Button>
        }
      />

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="space-y-6 xl:col-span-2">
          <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
            <Card.Content className="p-6">
              <div className="flex flex-wrap items-center gap-4">
                <Avatar className="size-14">
                  <Avatar.Fallback className="bg-accent text-accent-foreground text-base font-bold">
                    {initials(u.name, u.email)}
                  </Avatar.Fallback>
                </Avatar>
                <div className="min-w-0 flex-1 space-y-0.5">
                  <p className="text-lg font-semibold">{u.name || "No name"}</p>
                  <p className="text-sm text-muted-foreground">
                    {u.email}
                    {u.phone ? ` · ${u.phone}` : ""}
                  </p>
                  <div className="flex flex-wrap gap-2 pt-1">
                    <Chip color={chipColorForStatus(u.status)} variant="soft" size="sm" className="capitalize">
                      {u.status}
                    </Chip>
                    <Chip color="accent" variant="soft" size="sm" className="capitalize">
                      {u.role}
                    </Chip>
                    <Chip color={u.verified ? "success" : "default"} variant="soft" size="sm">
                      {u.verified ? "verified" : "unverified"}
                    </Chip>
                  </div>
                </div>
                <div className="flex gap-2">
                  <Button
                    variant={u.status === "suspended" ? "primary" : "secondary"}
                    size="sm"
                    className={u.status === "suspended" ? "bg-accent text-accent-foreground hover:bg-accent/90" : ""}
                    onPress={() => setConfirmKind("status")}
                  >
                    {u.status === "suspended" ? "Reactivate" : "Suspend"}
                  </Button>
                  <Button variant="secondary" size="sm" onPress={() => setPwOpen(true)}>
                    <KeyRound className="size-4" /> Reset password
                  </Button>
                  <Modal.Backdrop isOpen={pwOpen} onOpenChange={setPwOpen}>
                    <Modal.Container>
                      <Modal.Dialog className="sm:max-w-[420px]">
                        <Modal.CloseTrigger />
                        <Modal.Header>
                          <Modal.Heading>Reset password</Modal.Heading>
                        </Modal.Header>
                        <p className="px-6 -mt-2 text-sm text-muted-foreground">Revokes all active sessions for this user.</p>
                        <Modal.Body>
                          <form onSubmit={resetPassword} className="space-y-4">
                            <div className="space-y-1.5">
                              <Label htmlFor="pw">New password</Label>
                              <Input
                                id="pw"
                                type="password"
                                required
                                minLength={8}
                                value={pw}
                                onChange={(e) => setPw(e.target.value)}
                              />
                            </div>
                            <Button type="submit" variant="primary" className="w-full bg-accent text-accent-foreground hover:bg-accent/90">
                              Reset password
                            </Button>
                          </form>
                        </Modal.Body>
                      </Modal.Dialog>
                    </Modal.Container>
                  </Modal.Backdrop>
                </div>
              </div>
            </Card.Content>
          </Card>

          <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
            <Card.Header className="border-b">
              <Card.Title className="text-base">Listings ({data.listings.length} shown)</Card.Title>
              <Card.Description>Created by this user</Card.Description>
            </Card.Header>
            <Card.Content className="space-y-2 pt-4">
              {data.listings.length === 0 && <p className="text-sm text-muted-foreground">No listings yet.</p>}
              {data.listings.map((l: any) => (
                <Link key={l._id} href={`/listings/${l._id}`} className="flex items-center gap-3 rounded-xl border bg-card p-3 hover:bg-muted/50 transition-colors">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{l.title || "Untitled listing"}</p>
                    <p className="text-xs text-muted-foreground">
                      {l.category} · {timeAgo(l.created_at)}
                    </p>
                  </div>
                  <span className="text-sm font-medium tabular-nums">{money(l.price, l.currency)}</span>
                  <Chip color={chipColorForStatus(l.status)} variant="soft" size="sm" className="capitalize">
                    {l.status || "—"}
                  </Chip>
                </Link>
              ))}
            </Card.Content>
          </Card>

          <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
            <Card.Header className="border-b">
              <Card.Title className="flex items-center gap-2 text-base">
                <RefreshCw className="size-4" /> Behavior signals
              </Card.Title>
              <Card.Description>Latest seller-behavior feature snapshot</Card.Description>
            </Card.Header>
            <Card.Content className="pt-4">
              {data.behavior?.top_signals?.length ? (
                <div className="space-y-2">
                  {data.behavior.top_signals.map((s: any, i: number) => (
                    <div key={i} className="flex items-center justify-between rounded-xl border bg-card p-3 text-sm">
                      <span className="text-muted-foreground">{Array.isArray(s) ? String(s[0]) : String(s)}</span>
                      <span className="font-medium">{Array.isArray(s) ? String(s[1]) : ""}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No behavior features captured.</p>
              )}
            </Card.Content>
          </Card>
        </div>

        <div className="space-y-6">
          <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
            <Card.Header className="border-b">
              <Card.Title className="text-base">Account</Card.Title>
              <Card.Description>Joined {fmtDate(u.created_at)}</Card.Description>
            </Card.Header>
            <Card.Content className="grid grid-cols-2 gap-3 text-sm pt-4">
              <Stat label="Listings" value={String(data.stats.listing_count)} />
              <Stat label="Reports against" value={String(data.stats.reports_against)} />
              <Stat label="Escrow (buyer)" value={String(data.stats.escrows_as_buyer)} />
              <Stat label="Escrow (seller)" value={String(data.stats.escrows_as_seller)} />
              <Stat label="Active sessions" value={String(data.stats.active_sessions)} />
              <Stat label="Role" value={u.role} />
            </Card.Content>
          </Card>

          <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
            <Card.Header>
              <Card.Title className="text-base">Change role</Card.Title>
            </Card.Header>
            <Card.Content className="space-y-2">
              <Select
                aria-label="New role"
                placeholder="New role"
                value={role}
                onChange={(v) => setRole((v as string) || "")}
              >
                <Select.Trigger>
                  <Select.Value />
                  <Select.Indicator />
                </Select.Trigger>
                <Select.Popover>
                  <ListBox>
                    {ROLES.filter((r) => r !== u.role).map((r) => (
                      <ListBox.Item key={r} id={r} textValue={r}>
                        <span className="capitalize">{r}</span>
                        <ListBox.ItemIndicator />
                      </ListBox.Item>
                    ))}
                  </ListBox>
                </Select.Popover>
              </Select>
              <Button
                variant="secondary"
                className="w-full"
                isDisabled={!role}
                onPress={() => setConfirmKind("role")}
              >
                Apply role
              </Button>
            </Card.Content>
          </Card>

          <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
            <Card.Header>
              <Card.Title className="text-base">Listings by status</Card.Title>
            </Card.Header>
            <Card.Content className="space-y-1.5">
              {Object.entries(data.stats.listings_by_status).length === 0 && (
                <p className="text-sm text-muted-foreground">None.</p>
              )}
              {Object.entries(data.stats.listings_by_status).map(([s, n]) => (
                <div key={s} className="flex items-center justify-between text-sm">
                  <span className="capitalize">{s.replace(/_/g, " ")}</span>
                  <span className="font-medium tabular-nums">{String(n)}</span>
                </div>
              ))}
            </Card.Content>
          </Card>
        </div>
      </div>

      <AlertDialog.Backdrop isOpen={confirmKind !== null} onOpenChange={(v) => !v && setConfirmKind(null)}>
        <AlertDialog.Container>
          <AlertDialog.Dialog className="sm:max-w-[420px]">
            <AlertDialog.CloseTrigger />
            <AlertDialog.Header>
              <AlertDialog.Icon status={confirmKind === "status" && u.status !== "suspended" ? "danger" : "accent"} />
              <AlertDialog.Heading>
                {confirmKind === "status"
                  ? `${u.status === "suspended" ? "Reactivate" : "Suspend"} ${u.name || u.email}?`
                  : confirmKind === "role"
                    ? `Change ${u.name || u.email}'s role to "${role}"?`
                    : ""}
              </AlertDialog.Heading>
            </AlertDialog.Header>
            <AlertDialog.Body>
              <p className="text-sm text-muted-foreground">
                {confirmKind === "role" ? "The user's permissions update immediately." : "Their active sessions remain valid."}
              </p>
            </AlertDialog.Body>
            <AlertDialog.Footer>
              <Button variant="tertiary" slot="close" onPress={() => setConfirmKind(null)}>
                Cancel
              </Button>
              <Button
                variant={confirmKind === "status" && u.status !== "suspended" ? "danger" : "primary"}
                className={confirmKind === "status" && u.status !== "suspended" ? "" : "bg-accent text-accent-foreground hover:bg-accent/90"}
                onPress={() => (confirmKind === "status" ? toggleStatus() : changeRole())}
              >
                {confirmKind === "status"
                  ? u.status === "suspended"
                    ? "Reactivate"
                    : "Suspend"
                  : confirmKind === "role"
                    ? "Change role"
                    : "Confirm"}
              </Button>
            </AlertDialog.Footer>
          </AlertDialog.Dialog>
        </AlertDialog.Container>
      </AlertDialog.Backdrop>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border bg-card p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-lg font-bold tabular-nums">{value}</p>
    </div>
  );
}
