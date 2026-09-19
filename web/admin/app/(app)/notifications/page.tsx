"use client";

import { useState } from "react";
import { Bell, Megaphone } from "lucide-react";
import { post } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { NotificationItem, PageResult } from "@/lib/types";
import { fmtDate, timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { Button, Card, Chip, Input, Label, ListBox, Modal, Select, Skeleton, Table, TextArea } from "@heroui/react";

const AUDIENCES = ["all", "sellers", "buyers", "admins", "inspectors"];

export default function NotificationsPage() {
  const [page, setPage] = useState(1);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ title: "", body: "", audience: "all" });
  const [saving, setSaving] = useState(false);

  const { data, loading, error, reload } = useFetch<PageResult<NotificationItem>>(
    `/admin/notifications?page=${page}&page_size=20`,
  );

  async function send(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    const ok = await runMutation(() => post("/admin/notifications", form), {
      success: `Notification queued for ${form.audience}`,
    });
    setSaving(false);
    if (ok) {
      setOpen(false);
      setForm({ title: "", body: "", audience: "all" });
      reload();
    }
  }

  return (
    <div>
      <PageHeader
        title="Notifications"
        description="In-app announcements broadcast to user segments"
        actions={
          <>
            <Button
              variant="primary"
              className="bg-accent text-accent-foreground hover:bg-accent/90"
              onPress={() => setOpen(true)}
            >
              <Megaphone className="size-4" /> Broadcast
            </Button>
            <Modal.Backdrop isOpen={open} onOpenChange={setOpen}>
              <Modal.Container>
                <Modal.Dialog className="sm:max-w-md">
                  <Modal.CloseTrigger />
                  <Modal.Header>
                    <Modal.Heading>New announcement</Modal.Heading>
                    <p className="text-sm text-muted-foreground">
                      Recorded as an in-app notification for the target audience.
                    </p>
                  </Modal.Header>
                  <Modal.Body>
                    <form onSubmit={send} className="space-y-4">
                      <div className="space-y-1.5">
                        <Label htmlFor="n-title">Title</Label>
                        <Input
                          id="n-title"
                          required
                          value={form.title}
                          onChange={(e) => setForm({ ...form, title: e.target.value })}
                        />
                      </div>
                      <div className="space-y-1.5">
                        <Label htmlFor="n-body">Body</Label>
                        <TextArea
                          id="n-body"
                          rows={3}
                          value={form.body}
                          onChange={(e) => setForm({ ...form, body: e.target.value })}
                        />
                      </div>
                      <div className="space-y-1.5">
                        <Label>Audience</Label>
                        <Select
                          aria-label="Audience"
                          placeholder="Select audience"
                          value={form.audience}
                          onChange={(v) => setForm({ ...form, audience: (v as string) || "all" })}
                        >
                          <Select.Trigger>
                            <Select.Value />
                            <Select.Indicator />
                          </Select.Trigger>
                          <Select.Popover>
                            <ListBox>
                              {AUDIENCES.map((a) => (
                                <ListBox.Item key={a} id={a} textValue={a}>
                                  <span className="capitalize">{a}</span>
                                  <ListBox.ItemIndicator />
                                </ListBox.Item>
                              ))}
                            </ListBox>
                          </Select.Popover>
                        </Select>
                      </div>
                      <Button
                        type="submit"
                        variant="primary"
                        className="w-full bg-accent text-accent-foreground hover:bg-accent/90"
                        isDisabled={saving}
                        isPending={saving}
                      >
                        {saving ? "Sending…" : "Send announcement"}
                      </Button>
                    </form>
                  </Modal.Body>
                </Modal.Dialog>
              </Modal.Container>
            </Modal.Backdrop>
          </>
        }
      />

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <Card className="rounded-2xl p-4 ring-1 ring-black/5 dark:ring-white/10">
          <Card.Content className="space-y-3 p-0">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-14 rounded-xl" />
            ))}
          </Card.Content>
        </Card>
      )}

      {data && data.items.length === 0 && (
        <EmptyState icon={Bell} title="No notifications" description="Announcements to buyers and sellers will appear here." />
      )}

      {data && data.items.length > 0 && (
        <>
          <Card className="overflow-hidden rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
            <Card.Content className="p-0">
              <Table>
                <Table.ScrollContainer>
                  <Table.Content aria-label="Notifications" className="min-w-[640px]">
                    <Table.Header>
                      <Table.Column isRowHeader>Title</Table.Column>
                      <Table.Column>Audience</Table.Column>
                      <Table.Column>Delivery</Table.Column>
                      <Table.Column className="text-right">Recipients</Table.Column>
                      <Table.Column className="text-right">Sent</Table.Column>
                    </Table.Header>
                    <Table.Body>
                      {data.items.map((n) => (
                        <Table.Row key={n._id} id={n._id}>
                          <Table.Cell>
                            <p className="font-medium">{n.title}</p>
                            {n.body && <p className="max-w-96 truncate text-xs text-muted-foreground">{n.body}</p>}
                          </Table.Cell>
                          <Table.Cell>
                            <Chip color="default" variant="soft" size="sm" className="capitalize">
                              {n.audience}
                            </Chip>
                          </Table.Cell>
                          <Table.Cell>
                            <Chip color="accent" variant="soft" size="sm">
                              {n.delivery || "in_app"}
                            </Chip>
                          </Table.Cell>
                          <Table.Cell className="text-right tabular-nums">{n.recipient_count ?? 0}</Table.Cell>
                          <Table.Cell className="text-right text-xs text-muted-foreground">
                            {n.sent_at ? timeAgo(n.sent_at) : fmtDate(n.created_at)}
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
    </div>
  );
}
