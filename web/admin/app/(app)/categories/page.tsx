"use client";

import { useEffect, useState } from "react";
import { FolderPlus, Pencil, Tags, Trash2 } from "lucide-react";
import { del, patch, post } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { Category } from "@/lib/types";
import { fmtShort } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Button, Card, Chip, Input, Label, Modal, Skeleton, Switch, TextArea } from "@heroui/react";

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
    <div>
      <PageHeader
        title="Categories"
        description="Marketplace taxonomy — slugs power the verification pipeline"
        actions={
          <>
            <Button
              variant="primary"
              className="bg-accent text-accent-foreground hover:bg-accent/90"
              onPress={() => {
                setEditing(null);
                setOpen(true);
              }}
            >
              <FolderPlus className="size-4" /> Add category
            </Button>
            <Modal.Backdrop isOpen={open} onOpenChange={(v) => { setOpen(v); if (!v) setEditing(null); }}>
              <Modal.Container>
                <Modal.Dialog className="sm:max-w-lg">
                  <Modal.CloseTrigger />
                  <Modal.Header>
                    <Modal.Heading>{editing ? `Edit — ${editing.name}` : "Add category"}</Modal.Heading>
                    <p className="text-sm text-muted-foreground">
                      {editing
                        ? `Slug "${editing.slug}" stays stable while the trust engine uses it.`
                        : "Slug is generated from the name."}
                    </p>
                  </Modal.Header>
                  <Modal.Body>
                    <form onSubmit={save} className="space-y-4">
                      <div className="space-y-1.5">
                        <Label htmlFor="c-name">Name</Label>
                        <Input id="c-name" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
                      </div>
                      <div className="space-y-1.5">
                        <Label htmlFor="c-desc">Description</Label>
                        <TextArea
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
                      <div className="flex items-center justify-between rounded-xl border bg-card px-3 py-2.5">
                        <div>
                          <p className="text-sm font-medium">Active</p>
                          <p className="text-xs text-muted-foreground">Inactive categories are hidden from new listings.</p>
                        </div>
                        <Switch isSelected={form.active} onChange={(v) => setForm({ ...form, active: v })} aria-label="Active">
                          <Switch.Control>
                            <Switch.Thumb />
                          </Switch.Control>
                        </Switch>
                      </div>
                      <Button
                        type="submit"
                        variant="primary"
                        className="w-full bg-accent text-accent-foreground hover:bg-accent/90"
                        isDisabled={saving}
                        isPending={saving}
                      >
                        {saving ? "Saving…" : editing ? "Save changes" : "Create category"}
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
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-44 rounded-2xl" />
          ))}
        </div>
      )}

      {data && data.items.length === 0 && (
        <EmptyState icon={Tags} title="No categories" description="Create a category to start organizing listings." />
      )}

      {data && data.items.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {data.items.map((c) => (
            <Card key={c._id} className="flex flex-col rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
              <Card.Header className="pb-2">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2">
                    {c.icon && (
                      <span className="flex size-8 items-center justify-center rounded-lg bg-muted text-sm font-semibold capitalize">
                        {c.icon.slice(0, 2)}
                      </span>
                    )}
                    <div>
                      <Card.Title className="text-base">{c.name}</Card.Title>
                      <Card.Description className="font-mono text-xs">{c.slug}</Card.Description>
                    </div>
                  </div>
                  <Chip color={c.active ? "success" : "default"} variant="soft" size="sm">
                    {c.active ? "active" : "off"}
                  </Chip>
                </div>
              </Card.Header>
              <Card.Content className="flex flex-1 flex-col gap-3">
                <p className="min-h-8 text-sm text-muted-foreground">{c.description || "No description"}</p>
                <div className="flex flex-wrap gap-1.5">
                  {(c.fields || []).map((f) => (
                    <Chip key={f} color="accent" variant="soft" size="sm">
                      {f}
                    </Chip>
                  ))}
                </div>
                <div className="mt-auto flex items-center justify-between border-t pt-3">
                  <span className="text-xs text-muted-foreground">
                    {c.listing_count ?? 0} listings · sort {c.sort ?? 0} · updated {fmtShort(c.updated_at)}
                  </span>
                  <div className="flex gap-1.5">
                    <Button
                      variant="secondary"
                      isIconOnly
                      aria-label="Toggle active"
                      className="size-8"
                      onPress={() => toggleActive(c)}
                    >
                      <span className={`size-2.5 rounded-full ${c.active ? "bg-success" : "bg-muted-foreground/50"}`} />
                    </Button>
                    <Button
                      variant="secondary"
                      isIconOnly
                      aria-label="Edit"
                      className="size-8"
                      onPress={() => {
                        setEditing(c);
                        setOpen(true);
                      }}
                    >
                      <Pencil className="size-3.5" />
                    </Button>
                    <Button
                      variant="secondary"
                      isIconOnly
                      aria-label="Delete"
                      className="size-8 text-danger"
                      onPress={() => setConfirmDel(c)}
                    >
                      <Trash2 className="size-3.5" />
                    </Button>
                  </div>
                </div>
              </Card.Content>
            </Card>
          ))}
        </div>
      )}

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
