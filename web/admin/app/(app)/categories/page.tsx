"use client";

import { useEffect, useState } from "react";
import { FolderPlus, Loader2, Pencil, Tags, Trash2 } from "lucide-react";
import { del, patch, post } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { Category } from "@/lib/types";
import { fmtShort } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { StatusBadge } from "@/components/status-badge";

interface FormState {
  name: string;
  description: string;
  icon: string;
  fields: string;
  sort: string;
  active: boolean;
}

const EMPTY: FormState = { name: "", description: "", icon: "", fields: "", sort: "0", active: true };

export default function CategoriesPage() {
  const { data, loading, error, reload } = useFetch<{ items: Category[]; defaults_count: number }>("/admin/categories");
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Category | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [saving, setSaving] = useState(false);
  const [confirmDel, setConfirmDel] = useState<Category | null>(null);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      setForm({
        name: editing.name,
        description: editing.description || "",
        icon: editing.icon || "",
        fields: (editing.fields || []).join(", "),
        sort: String(editing.sort ?? 0),
        active: editing.active,
      });
    } else {
      setForm(EMPTY);
    }
  }, [open, editing]);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    const payload = {
      name: form.name,
      description: form.description,
      icon: form.icon,
      fields: form.fields
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean),
      sort: Number(form.sort) || 0,
      active: form.active,
    };
    const ok = await runMutation(
      () => (editing ? patch(`/admin/categories/${editing._id}`, payload) : post("/admin/categories", payload)),
      { success: editing ? "Category updated" : "Category created" },
    );
    setSaving(false);
    if (ok) {
      setOpen(false);
      setEditing(null);
      reload();
    }
  }

  async function remove() {
    if (!confirmDel) return;
    const c = confirmDel;
    const ok = await runMutation(() => del(`/admin/categories/${c._id}`), { success: "Category deleted" });
    setConfirmDel(null);
    if (ok) reload();
  }

  async function toggleActive(c: Category) {
    const ok = await runMutation(() => patch(`/admin/categories/${c._id}`, { active: !c.active }), {
      success: `Category ${c.active ? "deactivated" : "activated"}`,
    });
    if (ok) reload();
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="@container/main px-4 lg:px-6">
        <PageHeader
          title="Categories"
          description="Marketplace taxonomy — slugs power the verification pipeline"
          actions={
            <>
              <Button
                onClick={() => {
                  setEditing(null);
                  setOpen(true);
                }}
                className="cursor-pointer"
              >
                <FolderPlus className="size-4" /> Add category
              </Button>
              <Dialog open={open} onOpenChange={(v) => { setOpen(v); if (!v) setEditing(null); }}>
                <DialogContent className="sm:max-w-lg rounded-xl">
                  <DialogHeader>
                    <DialogTitle>{editing ? `Edit — ${editing.name}` : "Add category"}</DialogTitle>
                    <DialogDescription>
                      {editing
                        ? `Slug "${editing.slug}" stays stable while the trust engine uses it.`
                        : "Slug is generated from the name."}
                    </DialogDescription>
                  </DialogHeader>
                  <form onSubmit={save} className="space-y-4">
                    <div className="space-y-1.5">
                      <Label htmlFor="c-name">Name</Label>
                      <Input id="c-name" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
                    </div>
                    <div className="space-y-1.5">
                      <Label htmlFor="c-desc">Description</Label>
                      <Textarea
                        id="c-desc"
                        rows={2}
                        value={form.description}
                        onChange={(e) => setForm({ ...form, description: e.target.value })}
                      />
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <div className="space-y-1.5">
                        <Label htmlFor="c-icon">Icon</Label>
                        <Input
                          id="c-icon"
                          value={form.icon}
                          onChange={(e) => setForm({ ...form, icon: e.target.value })}
                          placeholder="smartphone"
                        />
                      </div>
                      <div className="space-y-1.5">
                        <Label htmlFor="c-sort">Sort order</Label>
                        <Input
                          id="c-sort"
                          type="number"
                          value={form.sort}
                          onChange={(e) => setForm({ ...form, sort: e.target.value })}
                        />
                      </div>
                    </div>
                    <div className="space-y-1.5">
                      <Label htmlFor="c-fields">Seller form fields (comma separated)</Label>
                      <Input
                        id="c-fields"
                        value={form.fields}
                        onChange={(e) => setForm({ ...form, fields: e.target.value })}
                        placeholder="price, year, brand, model, condition"
                      />
                    </div>
                    <div className="flex items-center justify-between rounded-md border bg-card px-3 py-2.5">
                      <div>
                        <p className="text-sm font-medium">Active</p>
                        <p className="text-xs text-muted-foreground">Inactive categories are hidden from new listings.</p>
                      </div>
                      <Switch
                        checked={form.active}
                        onCheckedChange={(v) => setForm({ ...form, active: v })}
                        aria-label="Active"
                      />
                    </div>
                    <Button
                      type="submit"
                      disabled={saving}
                      className="w-full cursor-pointer"
                    >
                      {saving && <Loader2 className="size-4 animate-spin" />}
                      {saving ? "Saving…" : editing ? "Save changes" : "Create category"}
                    </Button>
                  </form>
                </DialogContent>
              </Dialog>
            </>
          }
        />
      </div>

      <div className="@container/main px-4 lg:px-6">
        {error && <PageError message={error.message} onRetry={reload} />}

        {loading && !data && (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-44 rounded-xl" />
            ))}
          </div>
        )}

        {data && data.items.length === 0 && (
          <EmptyState icon={Tags} title="No categories" description="Create a category to start organizing listings." />
        )}

        {data && data.items.length > 0 && (
          <div className="*:data-[slot=card]:from-primary/5 *:data-[slot=card]:to-card dark:*:data-[slot=card]:bg-card *:data-[slot=card]:bg-gradient-to-t *:data-[slot=card]:shadow-xs grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {data.items.map((c) => (
              <Card key={c._id} className="@container/card flex flex-col">
                <CardHeader>
                  <div className="flex items-center gap-2">
                    {c.icon && (
                      <span className="flex size-8 items-center justify-center rounded-md bg-muted text-sm font-semibold capitalize shrink-0">
                        {c.icon.slice(0, 2)}
                      </span>
                    )}
                    <div className="min-w-0">
                      <CardTitle className="text-base truncate">{c.name}</CardTitle>
                      <CardDescription className="font-mono text-xs truncate">{c.slug}</CardDescription>
                    </div>
                  </div>
                  <CardAction>
                    <Badge variant="outline" className={c.active ? "border-green-200 bg-green-50 text-green-700 dark:border-green-800 dark:bg-green-950/20 dark:text-green-400" : "bg-muted text-muted-foreground"}>
                      {c.active ? "active" : "off"}
                    </Badge>
                  </CardAction>
                </CardHeader>
                <CardContent className="flex flex-1 flex-col gap-3">
                  <p className="min-h-8 text-sm text-muted-foreground line-clamp-2">{c.description || "No description"}</p>
                  <div className="flex flex-wrap gap-1.5">
                    {(c.fields || []).length ? (
                      (c.fields || []).map((f) => (
                        <Badge key={f} variant="outline" className="bg-accent/10 border-accent/20 text-accent-foreground">
                          {f}
                        </Badge>
                      ))
                    ) : (
                      <span className="text-xs text-muted-foreground">No fields</span>
                    )}
                  </div>
                  <div className="mt-auto flex items-center justify-between border-t pt-3">
                    <span className="text-xs text-muted-foreground">
                      {c.listing_count ?? 0} listings · sort {c.sort ?? 0} · {fmtShort(c.updated_at)}
                    </span>
                    <div className="flex gap-1.5">
                      <Button
                        variant="outline"
                        size="icon"
                        aria-label="Toggle active"
                        className="size-8 cursor-pointer"
                        onClick={() => toggleActive(c)}
                      >
                        <span className={`size-2.5 rounded-full ${c.active ? "bg-green-500" : "bg-muted-foreground/50"}`} />
                      </Button>
                      <Button
                        variant="outline"
                        size="icon"
                        aria-label="Edit"
                        className="size-8 cursor-pointer"
                        onClick={() => {
                          setEditing(c);
                          setOpen(true);
                        }}
                      >
                        <Pencil className="size-3.5" />
                      </Button>
                      <Button
                        variant="outline"
                        size="icon"
                        aria-label="Delete"
                        className="size-8 text-destructive hover:text-destructive cursor-pointer"
                        onClick={() => setConfirmDel(c)}
                      >
                        <Trash2 className="size-3.5" />
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </div>

      <ConfirmDialog
        open={confirmDel !== null}
        onOpenChange={(v) => !v && setConfirmDel(null)}
        title={confirmDel ? `Delete "${confirmDel.name}"?` : ""}
        description="Listings using it will block deletion."
        confirmLabel="Delete"
        destructive
        onConfirm={remove}
      />
    </div>
  );
}
