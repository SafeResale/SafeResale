"use client";

import { useCallback, useState } from "react";
import { FileText, Pencil, RefreshCw, Search, Trash2 } from "lucide-react";
import { del, patch, post, queryString } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { ContentItem, PageResult } from "@/lib/types";
import { timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { StatusBadge } from "@/components/status-badge";

interface Cfg {
  plural: string;
  singular: string;
  api: string;
  title: (it: any) => string;
  subtitle?: (it: any) => string;
  fields: { key: string; label: string; type?: "text" | "textarea" | "number" | "select"; options?: string[] }[];
  defaults: Record<string, any>;
}

const statusToneMap: Record<string, string> = {
  published: "success",
  draft: "neutral",
  active: "success",
  archived: "neutral",
};

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
          <Button className="bg-accent text-accent-foreground hover:bg-accent/90" onClick={openNew}>
            Add {cfg.singular}
          </Button>
        }
      />

      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center">
        <div className="relative flex-1 sm:max-w-xs">
          <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-8"
            placeholder="Search…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && commit()}
          />
        </div>
        <Select value={status} onValueChange={(v) => { setStatus(v || "all"); setPage(1); }}>
          <SelectTrigger className="w-36">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All statuses</SelectItem>
            <SelectItem value="published">Published</SelectItem>
            <SelectItem value="draft">Draft</SelectItem>
          </SelectContent>
        </Select>
        <Button variant="outline" size="icon" aria-label="Refresh" onClick={reload}>
          <RefreshCw className="size-4" />
        </Button>
      </div>

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <Card className="rounded-2xl">
          <CardContent className="space-y-3 p-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-12 rounded-xl" />
            ))}
          </CardContent>
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
          <Card className="overflow-hidden rounded-2xl">
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow className="hover:bg-transparent">
                    <TableHead className="w-1/2">{cfg.singular}</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Updated</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((it: any) => (
                    <TableRow key={it._id}>
                      <TableCell className="max-w-96">
                        <p className="truncate font-medium">{cfg.title(it)}</p>
                        {cfg.subtitle && <p className="truncate text-xs text-muted-foreground">{cfg.subtitle(it)}</p>}
                      </TableCell>
                      <TableCell>
                        <StatusBadge tone={(statusToneMap[it.status] as any) || "neutral"} label={it.status} className="capitalize" />
                      </TableCell>
                      <TableCell className="text-right text-xs text-muted-foreground">
                        {timeAgo(it.updated_at || it.created_at)}
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-1.5">
                          <Button variant="ghost" size="icon" aria-label="Edit" onClick={() => openEdit(it)}>
                            <Pencil className="size-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label="Delete"
                            className="text-destructive hover:text-destructive"
                            onClick={() => setConfirmDel(it)}
                          >
                            <Trash2 className="size-3.5" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
          <Pager page={data.page} pageSize={data.page_size} total={data.total} onPage={setPage} />
        </>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>{editing ? `Edit ${cfg.singular}` : `Add ${cfg.singular}`}</DialogTitle>
            <DialogDescription>Published items are visible on the public site.</DialogDescription>
          </DialogHeader>
          <form onSubmit={save} className="space-y-4">
            {cfg.fields.map((f) => {
              const value = form[f.key] ?? "";
              return (
                <div key={f.key} className="space-y-1.5">
                  <Label htmlFor={`f-${f.key}`}>{f.label}</Label>
                  {f.type === "select" ? (
                    <Select
                      value={String(value)}
                      onValueChange={(v) => setForm({ ...form, [f.key]: v })}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder={f.label} />
                      </SelectTrigger>
                      <SelectContent>
                        {f.options?.map((o) => (
                          <SelectItem key={o} value={o}>{o}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  ) : f.type === "textarea" ? (
                    <Textarea
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
                value={String(form.status ?? "draft")}
                onValueChange={(v) => setForm({ ...form, status: v || "draft" })}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select status" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="published">Published</SelectItem>
                  <SelectItem value="draft">Draft</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <Button
              type="submit"
              className="w-full bg-accent text-accent-foreground hover:bg-accent/90"
              disabled={saving}
            >
              {saving ? "Saving…" : editing ? "Save changes" : `Create ${cfg.singular}`}
            </Button>
          </form>
        </DialogContent>
      </Dialog>

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