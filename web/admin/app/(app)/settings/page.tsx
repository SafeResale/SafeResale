"use client";

import { useEffect, useState } from "react";
import { CheckCircle2, RefreshCw, Save } from "lucide-react";
import { get, put } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { SettingsData } from "@/lib/types";
import { PageHeader } from "@/components/page-header";
import { PageError } from "@/components/error-state";
import { Button, Card, Chip, Input, Skeleton, Switch, Tabs, TextArea, Tooltip } from "@heroui/react";

export default function SettingsPage() {
  const { data, loading, error, reload } = useFetch<SettingsData>("/admin/settings");
  const [form, setForm] = useState<Record<string, any>>({});
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (data) {
      const next: Record<string, any> = {};
      for (const g of Object.values(data.groups)) for (const [k, e] of Object.entries(g)) next[k] = e.value;
      setForm(next);
      setDirty(false);
    }
  }, [data]);

  function setValue(key: string, value: any) {
    setForm((f) => ({ ...f, [key]: value }));
    setDirty(true);
  }

  async function save() {
    setSaving(true);
    const res = await runMutation(() => put("/admin/settings", form), { success: "Settings saved" });
    setSaving(false);
    if (res) {
      setDirty(false);
      reload();
    }
  }

  return (
    <div>
      <PageHeader
        title="Settings"
        description="Platform configuration — published live to the public site"
        actions={
          <>
            <Button variant="secondary" isIconOnly aria-label="Reset" onPress={reload}>
              <RefreshCw className="size-4" />
            </Button>
            <Button
              variant="primary"
              className="bg-accent text-accent-foreground hover:bg-accent/90"
              onPress={save}
              isDisabled={!dirty || saving}
              isPending={saving}
            >
              {saving ? "Saving…" : <><Save className="size-4" /> Save changes</>}
            </Button>
          </>
        }
      />

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <div className="space-y-3">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-32 rounded-2xl" />)}</div>
      )}

      {data && (
        <Tabs defaultSelectedKey={data.group_names[0]} className="gap-4">
          <Tabs.ListContainer>
            <Tabs.List aria-label="Settings groups">
              {data.group_names.map((g) => (
                <Tabs.Tab key={g} id={g} className="capitalize">
                  {g.replace(/_/g, " ")}
                  <Tabs.Indicator />
                </Tabs.Tab>
              ))}
            </Tabs.List>
          </Tabs.ListContainer>
          {data.group_names.map((g) => (
            <Tabs.Panel key={g} id={g} className="space-y-4">
              <div className="grid gap-4 md:grid-cols-2">
                {Object.entries(data.groups[g] || {}).map(([key, entry]) => (
                  <Card key={key} className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
                    <Card.Header className="pb-2">
                      <Card.Title className="flex items-center gap-2 text-sm">
                        {entry.key.replace(/_/g, " ")}
                        {entry.overridden && (
                          <Tooltip delay={0}>
                            <Tooltip.Trigger aria-label="Customized">
                              <span className="inline-flex">
                                <CheckCircle2 className="size-3.5 text-success" />
                              </span>
                            </Tooltip.Trigger>
                            <Tooltip.Content>Customized — differs from default</Tooltip.Content>
                          </Tooltip>
                        )}
                      </Card.Title>
                      <Card.Description className="text-xs">{entry.description}</Card.Description>
                    </Card.Header>
                    <Card.Content>
                      {entry.type === "boolean" ? (
                        <div className="flex items-center gap-3">
                          <Switch isSelected={!!form[key]} onChange={(v) => setValue(key, v)} aria-label={entry.key}>
                            <Switch.Control>
                              <Switch.Thumb />
                            </Switch.Control>
                          </Switch>
                          <Chip color={form[key] ? "success" : "default"} variant="soft" size="sm">
                            {form[key] ? "Enabled" : "Disabled"}
                          </Chip>
                        </div>
                      ) : entry.type === "textarea" ? (
                        <TextArea
                          rows={4}
                          aria-label={entry.key}
                          value={typeof form[key] === "string" ? form[key] : ""}
                          onChange={(e) => setValue(key, e.target.value)}
                          placeholder={entry.description}
                        />
                      ) : (
                        <Input
                          aria-label={entry.key}
                          type={entry.type === "number" ? "number" : entry.type === "email" ? "email" : "text"}
                          value={String(form[key] ?? "")}
                          onChange={(e) => setValue(key, entry.type === "number" ? Number(e.target.value) : e.target.value)}
                        />
                      )}
                    </Card.Content>
                  </Card>
                ))}
              </div>
            </Tabs.Panel>
          ))}
        </Tabs>
      )}
    </div>
  );
}
