"use client";

import { useCallback, useState } from "react";
import { FileText, Pencil, RefreshCw, Trash2 } from "lucide-react";
import { del, patch, post, queryString } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { ContentItem, PageResult } from "@/lib/types";
import { timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { ConfirmDialog } from "@/components/confirm-dialog";
import {
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
  TextArea,
} from "@heroui/react";

interface Cfg {
  plural: string;
  singular: string;
  api: string;
  title: (it: any) => string;
  subtitle?: (it: any) => string;
  fields: { key: string; label: string; type?: "text" | "textarea" | "number" | "select"; options?: string[] }[];
  defaults: Record<string, any>;
}

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
  published: "success",
  draft: "neutral",
  active: "success",
  archived: "neutral",
};
function chipColorForStatus(status?: string): ChipColor {
  const tone = status ? statusToneMap[status] || "neutral" : "neutral";
  return toneToChipColor[tone] || "default";
}

export function ContentManager({ cfg }: { cfg: Cfg }) {
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(1);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<any | null>(null);
  const [form, setForm] = useState<Record<string, any>>({});
  const [saving, setSaving] = useState(false);
  const [confirmDel, setConfirmDel] = useState<any | null>(null);

  const path = `/admin/${cfg.api}${queryString({ q: search, status: status === "all" ? undefined : status, page, page_size: 20 })}`;
  const { data, loading, error, reload } = useFetch<PageResult<ContentItem>>(path);

  function openNew() {
    setEditing(null);
    setForm({ ...cfg.defaults });
    setOpen(true);
  }

  function openEdit(it: any) {
    setEditing(it);
    setForm({ ...cfg.defaults, ...it });
    setOpen(true);
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    const payload: Record<string, any> = {};
    for (const f of cfg.fields) {
      const v = form[f.key];
      if (f.type === "number") payload[f.key] = Number(v) || 0;
      else if (v !== undefined && v !== null && v !== "") payload[f.key] = v;
    }
    payload.status = form.status || "draft";
    const ok = await runMutation(
      () => (editing ? patch(`/admin/${cfg.api}/${editing._id}`, payload) : post(`/admin/${cfg.api}`, payload)),
      { success: editing ? `${cfg.singular} updated` : `${cfg.singular} created` },
    );
    setSaving(false);
    if (ok) {
      setOpen(false);
      reload();
    }
  }

  async function remove() {
    if (!confirmDel) return;
    const ok = await runMutation(() => del(`/admin/${cfg.api}/${confirmDel._id}`), { success: `${cfg.singular} deleted` });
    setConfirmDel(null);
    if (ok) reload();
  }

  const commit = useCallback(() => {
    setSearch(q.trim());
    setPage(1);
  }, [q]);

  return (
    <div>
      <PageHeader
        title={cfg.plural}
        description={`Manage ${cfg.plural.toLowerCase()} shown on the public site`}
        actions={
          <Button variant="primary" className="bg-accent text-accent-foreground hover:bg-accent/90" onPress={openNew}>
            Add {cfg.singular}
          </Button>
        }
      />

      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center">
        <SearchField
          aria-label="Search"
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
            <SearchField.Input placeholder="Search…" />
            <SearchField.ClearButton />
          </SearchField.Group>
        </SearchField>
        <Select
          className="w-36"
          aria-label="Filter by status"
          placeholder="Status"
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
              <ListBox.Item id="published" textValue="Published">
                Published
                <ListBox.ItemIndicator />
              </ListBox.Item>
              <ListBox.Item id="draft" textValue="Draft">
                Draft
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
        <Card className="rounded-2xl p-4 ring-1 ring-black/5 dark:ring-white/10">
          <Card.Content className="space-y-3 p-0">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-12 rounded-xl" />
            ))}
          </Card.Content>
        </Card>
      )}

      {data && data.items.length === 0 && (
        <EmptyState
          icon={FileText}
          title={`No ${cfg.plural.toLowerCase()} yet`}
          description={`Create the first ${cfg.singular.toLowerCase()} to populate this list.`}
        />
      )}

      {data && data.items.length > 0 && (
        <>
          <Card className="overflow-hidden rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
            <Card.Content className="p-0">
              <Table>
                <Table.ScrollContainer>
                  <Table.Content aria-label={cfg.plural} className="min-w-[640px]">
                    <Table.Header>
                      <Table.Column isRowHeader>{cfg.singular}</Table.Column>
                      <Table.Column>Status</Table.Column>
                      <Table.Column className="text-right">Updated</Table.Column>
                      <Table.Column className="text-right">Actions</Table.Column>
                    </Table.Header>
                    <Table.Body>
                      {data.items.map((it: any) => (
                        <Table.Row key={it._id} id={it._id}>
                          <Table.Cell className="max-w-96">
                            <p className="truncate font-medium">{cfg.title(it)}</p>
                            {cfg.subtitle && <p className="truncate text-xs text-muted-foreground">{cfg.subtitle(it)}</p>}
                          </Table.Cell>
                          <Table.Cell>
                            <Chip color={chipColorForStatus(it.status)} variant="soft" size="sm" className="capitalize">
                              {it.status}
                            </Chip>
                          </Table.Cell>
                          <Table.Cell className="text-right text-xs text-muted-foreground">
                            {timeAgo(it.updated_at || it.created_at)}
                          </Table.Cell>
                          <Table.Cell>
                            <div className="flex justify-end gap-1.5">
                              <Button variant="secondary" isIconOnly aria-label="Edit" className="size-8" onPress={() => openEdit(it)}>
                                <Pencil className="size-3.5" />
                              </Button>
                              <Button
                                variant="secondary"
                                isIconOnly
                                aria-label="Delete"
                                className="size-8 text-danger"
                                onPress={() => setConfirmDel(it)}
                              >
                                <Trash2 className="size-3.5" />
                              </Button>
                            </div>
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

      <Modal.Backdrop isOpen={open} onOpenChange={setOpen}>
        <Modal.Container>
          <Modal.Dialog className="sm:max-w-xl">
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>{editing ? `Edit ${cfg.singular}` : `Add ${cfg.singular}`}</Modal.Heading>
              <p className="text-sm text-muted-foreground">Published items are visible on the public site.</p>
            </Modal.Header>
            <Modal.Body>
              <form onSubmit={save} className="space-y-4">
                {cfg.fields.map((f) => {
                  const value = form[f.key] ?? "";
                  return (
                    <div key={f.key} className="space-y-1.5">
                      <Label htmlFor={`f-${f.key}`}>{f.label}</Label>
                      {f.type === "select" ? (
                        <Select
                          aria-label={f.label}
                          placeholder={f.label}
                          value={String(value)}
                          onChange={(v) => setForm({ ...form, [f.key]: v as string })}
                        >
                          <Select.Trigger>
                            <Select.Value />
                            <Select.Indicator />
                          </Select.Trigger>
                          <Select.Popover>
                            <ListBox>
                              {f.options?.map((o) => (
                                <ListBox.Item key={o} id={o} textValue={o}>
                                  {o}
                                  <ListBox.ItemIndicator />
                                </ListBox.Item>
                              ))}
                            </ListBox>
                          </Select.Popover>
                        </Select>
                      ) : f.type === "textarea" ? (
                        <TextArea
                          id={`f-${f.key}`}
                          rows={f.key === "content" ? 8 : 3}
                          value={String(value)}
                          onChange={(e) => setForm({ ...form, [f.key]: e.target.value })}
                        />
                      ) : f.type === "number" ? (
                        <Input
                          id={`f-${f.key}`}
                          type="number"
                          value={String(value)}
                          onChange={(e) => setForm({ ...form, [f.key]: e.target.value })}
                        />
                      ) : (
                        <Input
                          id={`f-${f.key}`}
                          value={String(value)}
                          onChange={(e) => setForm({ ...form, [f.key]: e.target.value })}
                        />
                      )}
                    </div>
                  );
                })}
                <div className="space-y-1.5">
                  <Label>Status</Label>
                  <Select
                    aria-label="Status"
                    placeholder="Select status"
                    value={String(form.status ?? "draft")}
                    onChange={(v) => setForm({ ...form, status: (v as string) || "draft" })}
                  >
                    <Select.Trigger>
                      <Select.Value />
                      <Select.Indicator />
                    </Select.Trigger>
                    <Select.Popover>
                      <ListBox>
                        <ListBox.Item id="published" textValue="Published">
                          Published
                          <ListBox.ItemIndicator />
                        </ListBox.Item>
                        <ListBox.Item id="draft" textValue="Draft">
                          Draft
                          <ListBox.ItemIndicator />
                        </ListBox.Item>
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
                  {saving ? "Saving…" : editing ? "Save changes" : `Create ${cfg.singular}`}
                </Button>
              </form>
            </Modal.Body>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>

      <ConfirmDialog
        open={confirmDel !== null}
        onOpenChange={(v) => !v && setConfirmDel(null)}
        title={`Delete this ${cfg.singular}?`}
        description="This permanently removes the item from the public site."
        confirmLabel="Delete"
        destructive
        onConfirm={remove}
      />
    </div>
  );
}
