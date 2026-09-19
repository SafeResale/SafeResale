"use client";

import { useState } from "react";
import { Archive, CheckCheck, Mail, RefreshCw } from "lucide-react";
import { del, get, patch, queryString } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { ContactMessage, PageResult } from "@/lib/types";
import { fmtDate, timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { Button, Card, Chip, ListBox, Modal, Select, Skeleton, Table } from "@heroui/react";

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
  new: "warning",
  read: "info",
  resolved: "success",
  archived: "neutral",
};

function chipColorForStatus(status?: string): ChipColor {
  const tone = status ? statusToneMap[status] || "neutral" : "neutral";
  return toneToChipColor[tone] || "default";
}

export default function MessagesPage() {
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState<ContactMessage | null>(null);
  const [detail, setDetail] = useState<ContactMessage | null>(null);

  const path = `/admin/messages${queryString({ status: status === "all" ? undefined : status, page, page_size: 20 })}`;
  const { data, loading, error, reload } = useFetch<PageResult<ContactMessage>>(path);

  async function open(m: ContactMessage) {
    setSelected(m);
    const d = await get<{ message: ContactMessage }>(`/admin/messages/${m._id}`);
    setDetail(d.message);
    if (d.message.status === "read" && m.status === "new") reload();
  }

  async function setState(m: ContactMessage, next: string) {
    const ok = await runMutation(() => patch(`/admin/messages/${m._id}`, { status: next }), { success: `Message ${next}` });
    if (ok) {
      setDetail((d) => (d ? { ...d, status: next as ContactMessage["status"] } : d));
      reload();
    }
  }

  async function remove(m: ContactMessage) {
    const ok = await runMutation(async () => {
      await del(`/admin/messages/${m._id}`);
      return true;
    }, { success: "Message deleted" });
    if (ok) {
      setSelected(null);
      reload();
    }
  }

  return (
    <div>
      <PageHeader
        title="Messages"
        description="Contact form submissions from the public site"
        actions={
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
                <ListBox.Item id="all" textValue="All">
                  All <ListBox.ItemIndicator />
                </ListBox.Item>
                <ListBox.Item id="new" textValue="New">
                  New <ListBox.ItemIndicator />
                </ListBox.Item>
                <ListBox.Item id="read" textValue="Read">
                  Read <ListBox.ItemIndicator />
                </ListBox.Item>
                <ListBox.Item id="resolved" textValue="Resolved">
                  Resolved <ListBox.ItemIndicator />
                </ListBox.Item>
                <ListBox.Item id="archived" textValue="Archived">
                  Archived <ListBox.ItemIndicator />
                </ListBox.Item>
              </ListBox>
            </Select.Popover>
          </Select>
        }
      />

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
        <EmptyState icon={Mail} title="No messages" description="Contact-form submissions will land here as soon as they are sent." />
      )}

      {data && data.items.length > 0 && (
        <>
          <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10 overflow-hidden">
            <Card.Content className="p-0">
              <Table>
                <Table.ScrollContainer>
                  <Table.Content aria-label="Messages" className="min-w-[640px]">
                    <Table.Header>
                      <Table.Column isRowHeader>From</Table.Column>
                      <Table.Column>Subject</Table.Column>
                      <Table.Column>Status</Table.Column>
                      <Table.Column className="text-right">Received</Table.Column>
                    </Table.Header>
                    <Table.Body>
                      {data.items.map((m) => (
                        <Table.Row key={m._id} id={m._id} className="cursor-pointer" onAction={() => open(m)}>
                          <Table.Cell>
                            <span className="block truncate font-medium">{m.name || "Anonymous"}</span>
                            <span className="block max-w-48 truncate text-xs text-muted-foreground">{m.email}</span>
                          </Table.Cell>
                          <Table.Cell className="max-w-72">
                            <span className="block truncate text-sm">{m.subject || "(no subject)"}</span>
                          </Table.Cell>
                          <Table.Cell>
                            <Chip color={chipColorForStatus(m.status)} variant="soft" size="sm" className="capitalize">
                              {m.status}
                            </Chip>
                          </Table.Cell>
                          <Table.Cell className="text-right text-xs text-muted-foreground">{timeAgo(m.created_at)}</Table.Cell>
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

      <Modal.Backdrop
        isOpen={!!selected}
        onOpenChange={(v) => {
          if (!v) {
            setSelected(null);
            setDetail(null);
          }
        }}
      >
        <Modal.Container>
          <Modal.Dialog className="sm:max-w-xl">
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>{detail?.subject || "Message"}</Modal.Heading>
            </Modal.Header>
            <p className="px-6 -mt-2 text-sm text-muted-foreground">
              {detail?.name || "Anonymous"} · {detail?.email} · {fmtDate(detail?.created_at)}
            </p>
            <Modal.Body>
              <div className="max-h-72 overflow-y-auto whitespace-pre-wrap rounded-xl border bg-muted/40 p-4 text-sm">
                {detail?.message || "No body."}
              </div>
              <div className="mt-4 flex justify-end gap-2">
                <Button variant="outline" size="sm" onPress={() => selected && remove(selected)}>
                  <Archive className="size-3.5" /> Delete
                </Button>
                {detail?.status === "resolved" ? (
                  <Button
                    variant="primary"
                    size="sm"
                    className="bg-accent text-accent-foreground hover:bg-accent/90"
                    onPress={() => selected && setState(selected, "archived")}
                  >
                    <CheckCheck className="size-3.5" /> Archive
                  </Button>
                ) : (
                  <Button
                    variant="primary"
                    size="sm"
                    className="bg-accent text-accent-foreground hover:bg-accent/90"
                    onPress={() => selected && setState(selected, "resolved")}
                  >
                    <CheckCheck className="size-3.5" /> Mark resolved
                  </Button>
                )}
              </div>
            </Modal.Body>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </div>
  );
}
