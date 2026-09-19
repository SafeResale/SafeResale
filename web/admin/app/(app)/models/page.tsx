"use client";

import { Cpu, RefreshCw } from "lucide-react";
import { useFetch } from "@/lib/use-fetch";
import type { ModelInfo } from "@/lib/types";
import { fmtDate } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError, EmptyState } from "@/components/error-state";
import { Button, Card, Chip, Skeleton } from "@heroui/react";

type ChipColor = "default" | "accent" | "success" | "warning" | "danger";

function chipColorForModelStatus(status?: string): ChipColor {
  if (status === "active") return "success";
  return "default";
}

export default function ModelsPage() {
  const { data, loading, error, reload } = useFetch<{ models: ModelInfo[] }>("/admin/models");

  return (
    <div>
      <PageHeader
        title="ML models"
        description="Validation pipeline model versions & metrics"
        actions={
          <Button variant="secondary" isIconOnly aria-label="Refresh" onPress={reload}>
            <RefreshCw className="size-4" />
          </Button>
        }
      />

      {error && <PageError message={error.message} onRetry={reload} />}

      {loading && !data && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-40 rounded-2xl" />
          ))}
        </div>
      )}

      {data && data.models.length === 0 && (
        <EmptyState
          icon={Cpu}
          title="No model metrics yet"
          description="When the verification pipeline records a model run, its metrics will surface here."
        />
      )}

      {data && data.models.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {data.models.map((m) => (
            <Card key={m._id} variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
              <Card.Header className="pb-2">
                <div className="flex items-start justify-between gap-2">
                  <Card.Title className="flex items-center gap-2 text-base">
                    <span className="inline-flex size-8 items-center justify-center rounded-xl bg-accent text-accent-foreground">
                      <Cpu className="size-4" />
                    </span>
                    {m.name || (m as any).model || "Model"}
                  </Card.Title>
                  <Chip color={chipColorForModelStatus(m.status)} variant="soft" size="sm" className="capitalize">
                    {m.status || "—"}
                  </Chip>
                </div>
                <Card.Description className="font-mono text-xs">{m.version || m._id.slice(0, 10)}</Card.Description>
              </Card.Header>
              <Card.Content>
                <dl className="grid grid-cols-2 gap-2 text-sm">
                  {(Object.entries(m) as [string, any][])
                    .filter(([k]) => !["_id", "name", "model", "status", "version", "created_at", "updated_at", "detail"].includes(k))
                    .slice(0, 6)
                    .map(([k, v]) => (
                      <div key={k}>
                        <dt className="text-xs text-muted-foreground">{k.replace(/_/g, " ")}</dt>
                        <dd className="font-medium tabular-nums">
                          {typeof v === "number" ? (Math.abs(v) < 1 ? (v * 100).toFixed(1) + "%" : v.toLocaleString()) : String(v)}
                        </dd>
                      </div>
                    ))}
                </dl>
                <p className="mt-3 border-t pt-2 text-xs text-muted-foreground">updated {fmtDate((m as any).updated_at)}</p>
              </Card.Content>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
