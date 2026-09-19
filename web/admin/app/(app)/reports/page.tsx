"use client";

import { useState } from "react";
import Link from "next/link";
import { Flag, RefreshCw, ShieldCheck, X } from "lucide-react";
import { post, queryString } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { PageResult, Report } from "@/lib/types";
import { timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { Button, Card, Chip, Input, Label, ListBox, Modal, Select, Skeleton, Table } from "@heroui/react";

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
  pending: "warning",
  resolved: "success",
  dismissed: "neutral",
};

function chipColorForStatus(status?: string): ChipColor {
  const tone = status ? statusToneMap[status] || "neutral" : "neutral";
  return toneToChipColor[tone] || "default";
}

export default function ReportsPage() {
  const [status, setStatus] = useState("all");
  const [type, setType] = useState("all");
  const [page, setPage] = useState(1);
  const [acting, setActing] = useState<Report | null>(null);
  const [resolution, setResolution] = useState("");

  const path = `/admin/reports${queryString({
    status: status === "all" ? undefined : status,
    target_type: type === "all" ? undefined : type,
    page,
    page_size: 20,
  })}`;
  const { data, loading, error, reload } = useFetch<PageResult<Report>>(path);

  async function resolve(r: Report, action: "resolve" | "dismiss") {
    if (!r) return;
    const ok = await runMutation(
      () => post(`/admin/reports/${r._id}/${action}`, { note: resolution.trim() }),
      { success: `Report ${action}d` },
    );
    if (ok) {
      setActing(null);
      setResolution("");
      reload();
    }
  }

  return (
    <div>
      <PageHeader
        title="Reports"
        description="Content and user reports submitted through the app"
        actions={
          <Button variant="secondary" isIconOnly aria-label="Refresh" onPress={reload}>
            <RefreshCw className="size-4" />
          </Button>
        }
      />

      <div className="mb-4 flex flex-wrap gap-2">
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
                All statuses <ListBox.ItemIndicator />
              </ListBox.Item>
              <ListBox.Item id="pending" textValue="Pending">
                Pending <ListBox.ItemIndicator />
              </ListBox.Item>
              <ListBox.Item id="resolved" textValue="Resolved">
                Resolved <ListBox.ItemIndicator />
              </ListBox.Item>
              <ListBox.Item id="dismissed" textValue="Dismissed">
                Dismissed <ListBox.ItemIndicator />
              </ListBox.Item>
            </ListBox>
          </Select.Popover>
        </Select>
        <Select
          className="w-36"
          placeholder="Target"
          aria-label="Filter by target"
          value={type}
          onChange={(v) => {
            setType((v as string) || "all");
            setPage(1);
          }}
        >
          <Select.Trigger>
            <Select.Value />
            <Select.Indicator />
          </Select.Trigger>
          <Select.Popover>
            <ListBox>
              <ListBox.Item id="all" textValue="All targets">
                All targets <ListBox.ItemIndicator />
              </ListBox.Item>
              <ListBox.Item id="listing" textValue="Listing">
                Listing <ListBox.ItemIndicator />
              </ListBox.Item>
              <ListBox.Item id="user" textValue="User">
                User <ListBox.ItemIndicator />
              </ListBox.Item>
            </ListBox>
          </Select.Popover>
        </Select>
      </div>

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
          <Card.Content className="p-4">
            <div className="space-y-3">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-14 rounded-xl" />
              ))}
            </div>
          </Card.Content>
        </Card>
      )}

      {data && data.items.length === 0 && (
        <EmptyState icon={Flag} title="No reports" description="Nothing matches this filter. Reported listings and users will show here." />
      )}

      {data && data.items.length > 0 && (
        <>
          <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10 overflow-hidden">
            <Card.Content className="p-0">
              <Table>
                <Table.ScrollContainer>
                  <Table.Content aria-label="Reports" className="min-w-[760px]">
                    <Table.Header>
                      <Table.Column isRowHeader>Target</Table.Column>
                      <Table.Column>Reason</Table.Column>
                      <Table.Column>Reporter</Table.Column>
                      <Table.Column>Status</Table.Column>
                      <Table.Column className="text-right">Reported</Table.Column>
                      <Table.Column className="text-right">Actions</Table.Column>
                    </Table.Header>
                    <Table.Body>
                      {data.items.map((r) => (
                        <Table.Row key={r._id} id={r._id}>
                          <Table.Cell className="max-w-52">
                            {r.target_type === "listing" && r.target?.id ? (
                              <Link href={`/listings/${r.target.id}`}>
                                <span className="block truncate font-medium hover:text-primary">{r.target.title || "Untitled listing"}</span>
                                <span className="text-xs text-muted-foreground">listing · {r.target.status}</span>
                              </Link>
                            ) : r.target_type === "user" && r.target?.id ? (
                              <Link href={`/users/${r.target.id}`}>
                                <span className="block truncate font-medium hover:text-primary">{r.target.name || "User"}</span>
                                <span className="text-xs text-muted-foreground">user · {r.target.role}</span>
                              </Link>
                            ) : (
                              <span className="text-xs text-muted-foreground">
                                {r.target_id} ({r.target_type})
                              </span>
                            )}
                          </Table.Cell>
                          <Table.Cell className="max-w-56">
                            <span className="block truncate text-sm">{r.reason}</span>
                            {r.description && <span className="block truncate text-xs text-muted-foreground">{r.description}</span>}
                          </Table.Cell>
                          <Table.Cell className="text-xs text-muted-foreground">{r.reporter?.name || r.reporter?.email || "—"}</Table.Cell>
                          <Table.Cell>
                            <Chip color={chipColorForStatus(r.status)} variant="soft" size="sm" className="capitalize">
                              {r.status}
                            </Chip>
                          </Table.Cell>
                          <Table.Cell className="text-right text-xs text-muted-foreground">{timeAgo(r.created_at)}</Table.Cell>
                          <Table.Cell>
                            {r.status === "pending" ? (
                              <div className="flex justify-end gap-1.5">
                                <Button
                                  variant="primary"
                                  size="sm"
                                  className="bg-accent text-accent-foreground hover:bg-accent/90"
                                  onPress={() => {
                                    setActing(r);
                                    setResolution("");
                                  }}
                                >
                                  <ShieldCheck className="size-3.5" /> Resolve
                                </Button>
                                <Button variant="ghost" size="sm" onPress={() => resolve(r, "dismiss")}>
                                  <X className="size-3.5" /> Dismiss
                                </Button>
                              </div>
                            ) : (
                              <span className="block text-right text-xs text-muted-foreground">{r.resolved_at ? timeAgo(r.resolved_at) : "—"}</span>
                            )}
                          </Table.Cell>
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

      <Modal.Backdrop isOpen={!!acting} onOpenChange={(v) => !v && setActing(null)}>
        <Modal.Container>
          <Modal.Dialog className="sm:max-w-[440px]">
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>Resolve report</Modal.Heading>
            </Modal.Header>
            <p className="px-6 -mt-2 text-sm text-muted-foreground">Record how the review was actioned for the audit trail.</p>
            <Modal.Body>
              <div className="space-y-1.5">
                <Label htmlFor="note">Resolution note (optional)</Label>
                <Input
                  id="note"
                  value={resolution}
                  onChange={(e) => setResolution(e.target.value)}
                  placeholder="e.g. verified with seller — listing approved"
                />
              </div>
              <Button
                variant="primary"
                className="mt-4 w-full bg-accent text-accent-foreground hover:bg-accent/90"
                onPress={() => acting && resolve(acting, "resolve")}
              >
                Mark resolved
              </Button>
            </Modal.Body>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </div>
  );
}
