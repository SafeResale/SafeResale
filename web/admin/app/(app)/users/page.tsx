"use client";

import { useCallback, useState } from "react";
import Link from "next/link";
import { RefreshCw, Users as UsersIcon } from "lucide-react";
import { post, queryString } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { PageResult, UserRow } from "@/lib/types";
import { fmtShort, initials } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import {
  Avatar,
  Button,
  Card,
  Chip,
  Input,
  Label,
  ListBox,
  Modal,
  SearchField,
  Select,
  Skeleton,
  Table,
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
  ok: "success",
  live: "success",
  pending: "warning",
  review: "warning",
  in_review: "warning",
  suspended: "danger",
  blocked: "danger",
  restricted: "danger",
  rejected: "danger",
  deactivated: "neutral",
  expired: "neutral",
  archived: "neutral",
  unverified: "neutral",
};

function chipColorForStatus(status?: string): ChipColor {
  const tone = status ? statusToneMap[status] || "neutral" : "neutral";
  return toneToChipColor[tone] || "default";
}

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

  const commit = useCallback(() => {
    setSearch(q.trim());
    setPage(1);
  }, [q]);

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
          <>
            <Button
              variant="primary"
              className="bg-accent text-accent-foreground hover:bg-accent/90"
              onPress={() => setOpen(true)}
            >
              Add user
            </Button>
            <Modal.Backdrop isOpen={open} onOpenChange={setOpen}>
              <Modal.Container>
                <Modal.Dialog className="sm:max-w-[440px]">
                  <Modal.CloseTrigger />
                  <Modal.Header>
                    <Modal.Heading>Add user</Modal.Heading>
                  </Modal.Header>
                  <p className="px-6 -mt-2 text-sm text-muted-foreground">Creates an account; the user can sign in immediately.</p>
                  <Modal.Body>
                    <form onSubmit={createUser} className="space-y-4">
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
                      <div className="space-y-1.5">
                        <Label>Role</Label>
                        <Select
                          aria-label="Role"
                          placeholder="Select role"
                          value={form.role}
                          onChange={(v) => setForm({ ...form, role: (v as string) || "seller" })}
                        >
                          <Select.Trigger>
                            <Select.Value />
                            <Select.Indicator />
                          </Select.Trigger>
                          <Select.Popover>
                            <ListBox>
                              {ROLES.map((r) => (
                                <ListBox.Item key={r} id={r} textValue={r}>
                                  <span className="capitalize">{r}</span>
                                  <ListBox.ItemIndicator />
                                </ListBox.Item>
                              ))}
                            </ListBox>
                          </Select.Popover>
                        </Select>
                      </div>
                      <Button type="submit" variant="primary" className="w-full bg-accent text-accent-foreground hover:bg-accent/90">
                        Create user
                      </Button>
                    </form>
                  </Modal.Body>
                </Modal.Dialog>
              </Modal.Container>
            </Modal.Backdrop>
          </>
        }
      />

      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center">
        <SearchField
          aria-label="Search users"
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
            <SearchField.Input placeholder="Search name or email…" />
            <SearchField.ClearButton />
          </SearchField.Group>
        </SearchField>
        <Select
          className="w-36"
          placeholder="Role"
          aria-label="Filter by role"
          value={role}
          onChange={(v) => {
            setRole((v as string) || "all");
            setPage(1);
          }}
        >
          <Select.Trigger>
            <Select.Value />
            <Select.Indicator />
          </Select.Trigger>
          <Select.Popover>
            <ListBox>
              <ListBox.Item id="all" textValue="All roles">
                All roles
                <ListBox.ItemIndicator />
              </ListBox.Item>
              {ROLES.map((r) => (
                <ListBox.Item key={r} id={r} textValue={r}>
                  <span className="capitalize">{r}</span>
                  <ListBox.ItemIndicator />
                </ListBox.Item>
              ))}
            </ListBox>
          </Select.Popover>
        </Select>
        <Select
          className="w-36"
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
              <ListBox.Item id="active" textValue="Active">
                Active
                <ListBox.ItemIndicator />
              </ListBox.Item>
              <ListBox.Item id="suspended" textValue="Suspended">
                Suspended
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
              {Array.from({ length: 7 }).map((_, i) => (
                <Skeleton key={i} className="h-11 rounded-xl" />
              ))}
            </div>
          </Card.Content>
        </Card>
      )}

      {data && data.items.length === 0 && (
        <EmptyState icon={UsersIcon} title="No users found" description="Adjust filters or create a new user." />
      )}

      {data && data.items.length > 0 && (
        <>
          <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10 overflow-hidden">
            <Card.Content className="p-0">
              <Table>
                <Table.ScrollContainer>
                  <Table.Content aria-label="Users" className="min-w-[720px]">
                    <Table.Header>
                      <Table.Column isRowHeader>User</Table.Column>
                      <Table.Column>Role</Table.Column>
                      <Table.Column>Status</Table.Column>
                      <Table.Column>Verified</Table.Column>
                      <Table.Column className="text-right">Listings</Table.Column>
                      <Table.Column className="text-right">Joined</Table.Column>
                    </Table.Header>
                    <Table.Body>
                      {data.items.map((u) => (
                        <Table.Row key={u._id} id={u._id}>
                          <Table.Cell>
                            <Link href={`/users/${u._id}`} className="group flex items-center gap-3">
                              <Avatar className="size-8">
                                <Avatar.Fallback className="bg-muted text-xs font-semibold">
                                  {initials(u.name, u.email)}
                                </Avatar.Fallback>
                              </Avatar>
                              <span className="min-w-0">
                                <span className="block truncate font-medium group-hover:text-primary">{u.name || "—"}</span>
                                <span className="block max-w-52 truncate text-xs text-muted-foreground">{u.email}</span>
                              </span>
                            </Link>
                          </Table.Cell>
                          <Table.Cell>
                            <Chip color="accent" variant="soft" size="sm" className="capitalize">
                              {u.role}
                            </Chip>
                          </Table.Cell>
                          <Table.Cell>
                            <Chip color={chipColorForStatus(u.status)} variant="soft" size="sm" className="capitalize">
                              {u.status}
                            </Chip>
                          </Table.Cell>
                          <Table.Cell>
                            <Chip color={u.verified ? "success" : "default"} variant="soft" size="sm">
                              {u.verified ? "yes" : "no"}
                            </Chip>
                          </Table.Cell>
                          <Table.Cell className="text-right tabular-nums">{u.listing_count ?? 0}</Table.Cell>
                          <Table.Cell className="text-right text-xs text-muted-foreground">{fmtShort(u.created_at)}</Table.Cell>
                        </Table.Row>
                      ))}
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
