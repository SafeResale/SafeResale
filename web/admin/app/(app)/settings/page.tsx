"use client";

import { useEffect, useState } from "react";
import { CheckCircle2, RefreshCw, Save } from "lucide-react";
import { get, put } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import type { SettingsData } from "@/lib/types";
import { PageHeader } from "@/components/page-header";
import { PageError } from "@/components/error-state";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";

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
            <Button variant="outline" size="icon" onClick={reload} aria-label="Reset"><RefreshCw className="size-4" /></Button>
            <Button onClick={save} disabled={!dirty || saving}>
              {saving ? "Saving…" : <><Save className="size-4" /> Save changes</>}
            </Button>
          </>
        }
      />

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <div className="space-y-3">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-32 rounded-xl" />)}</div>
      )}

      {data && (
        <Tabs defaultValue="general">
          <TabsList className="mb-4">
            {data.group_names.map((g) => (
              <TabsTrigger key={g} value={g} className="capitalize">{g.replace(/_/g, " ")}</TabsTrigger>
            ))}
          </TabsList>
          {data.group_names.map((g) => (
            <TabsContent key={g} value={g} className="space-y-4">
              <div className="grid gap-4 md:grid-cols-2">
                {Object.entries(data.groups[g] || {}).map(([key, entry]) => (
                  <Card key={key}>
                    <CardHeader className="pb-2">
                      <CardTitle className="flex items-center gap-2 text-sm">
                        {entry.key.replace(/_/g, " ")}
                        {entry.overridden && (
                          <TooltipProvider>
                            <Tooltip>
                              <TooltipTrigger asChild><CheckCircle2 className="size-3.5 text-success" /></TooltipTrigger>
                              <TooltipContent>Customized — differs from default</TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        )}
                      </CardTitle>
                      <CardDescription>{entry.description}</CardDescription>
                    </CardHeader>
                    <CardContent>
                      {entry.type === "boolean" ? (
                        <div className="flex items-center gap-2">
                          <Switch checked={!!form[key]} onCheckedChange={(v) => setValue(key, v)} />
                          <span className="text-xs text-muted-foreground">{form[key] ? "Enabled" : "Disabled"}</span>
                        </div>
                      ) : entry.type === "textarea" ? (
                        <Textarea rows={4} value={typeof form[key] === "string" ? form[key] : ""} onChange={(e) => setValue(key, e.target.value)} />
                      ) : (
                        <Input
                          type={entry.type === "number" ? "number" : entry.type === "email" ? "email" : "text"}
                          value={typeof form[key] === "number" && form[key] === 0 ? 0 : form[key] ?? ""}
                          onChange={(e) => setValue(key, entry.type === "number" ? Number(e.target.value) : e.target.value)}
                        />
                      )}
                    </CardContent>
                  </Card>
                ))}
              </div>
            </TabsContent>
          ))}
        </Tabs>
      )}
    </div>
  );
}