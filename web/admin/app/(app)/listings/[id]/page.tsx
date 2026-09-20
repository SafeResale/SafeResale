"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ArrowLeft,
  CheckCircle2,
  FileSearch,
  Fingerprint,
  Gauge,
  Image as ImageIcon,
  Loader2,
  ScrollText,
  ShieldCheck,
  Stethoscope,
  TriangleAlert,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { post } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import { capitalize, fmtDate, money, riskLabel, timeAgo } from "@/lib/format";
import { defectFamily, defectLabel, defectsFor, isRelevantDefect } from "@/lib/defects";
import { PageError } from "@/components/error-state";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { StatusBadge, statusTone, riskTone } from "@/components/status-badge";

function conditionTone(grade: unknown): "success" | "warning" | "danger" | "neutral" {
  const s = String(grade ?? "").toLowerCase();
  if (/excellent|good|like ?new|pass|immaculate/.test(s)) return "success";
  if (/fair|moderate|average/.test(s)) return "warning";
  if (/poor|fail|worn|damaged/.test(s)) return "danger";
  return "neutral";
}

const ACTIONS: { key: string; label: string; variant: "primary" | "secondary" | "danger" }[] = [
  { key: "approve", label: "Approve", variant: "primary" },
  { key: "warn", label: "Warn", variant: "secondary" },
  { key: "block", label: "Block", variant: "danger" },
  { key: "request_inspection", label: "Request inspection", variant: "secondary" },
  { key: "suspend_seller", label: "Suspend seller", variant: "danger" },
];

const ACTION_BUTTON_STYLE: Record<"primary" | "secondary" | "danger", { variant: "default" | "secondary" | "destructive"; className?: string }> = {
  primary: { variant: "default" },
  secondary: { variant: "secondary" },
  danger: { variant: "destructive" },
};

function actionLabel(key: string) {
  return ACTIONS.find((a) => a.key === key)?.label ?? key.replace(/_/g, " ");
}

function StatusIcon({ action }: { action: string }) {
  if (!action) return null;
  if (action === "approve") return <CheckCircle2 className="size-4" />;
  if (action === "block" || action === "suspend_seller") return <TriangleAlert className="size-4" />;
  return <Stethoscope className="size-4" />;
}

function humanizeTest(id: string) {
  const map: Record<string, string> = {
    sensor_accelerometer: "Accelerometer",
    sensor_gyroscope: "Gyroscope",
    sensor_proximity: "Proximity",
    sys_snapshot: "System snapshot",
    camera: "Camera",
    battery: "Battery",
    touch: "Touchscreen",
    gps: "GPS",
    wifi: "Wi-Fi",
    bluetooth: "Bluetooth",
    speaker: "Speaker",
    microphone: "Microphone",
  };
  return map[id] || capitalize(String(id).replace(/[_-]+/g, " "));
}

const levelDot: Record<string, string> = {
  danger: "bg-destructive",
  warn: "bg-warning",
  info: "bg-info",
  success: "bg-success",
};

export default function ListingDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { data, loading, error, reload } = useFetch<any>(`/admin/listings/${id}`);
  const [reason, setReason] = useState("");
  const [pendingAction, setPendingAction] = useState<string | null>(null);
  const [pendingConfirm, setPendingConfirm] = useState<string | null>(null);

  if (error) {
    return (
      <div className="flex flex-col gap-4">
        <div className="px-4 lg:px-6">
          <div className="flex flex-col gap-2">
            <h1 className="text-2xl font-bold tracking-tight">Listing detail</h1>
            <p className="text-sm text-muted-foreground font-mono text-xs">{id}</p>
          </div>
        </div>
        <div className="@container/main px-4 lg:px-6">
          <PageError message={error.message} onRetry={reload} />
        </div>
      </div>
    );
  }

  if (loading || !data) {
    return (
      <div className="flex flex-col gap-4">
        <div className="px-4 lg:px-6">
          <div className="flex flex-col gap-2">
            <h1 className="text-2xl font-bold tracking-tight">Listing detail</h1>
            <p className="text-sm text-muted-foreground">{id}</p>
          </div>
        </div>
        <div className="@container/main px-4 lg:px-6 space-y-4">
          <Skeleton className="h-40 rounded-xl" />
          <Skeleton className="h-64 rounded-xl" />
        </div>
      </div>
    );
  }

  const listing = data.listing;
  const seller = data.seller;
  const evidence = data.evidence;
  const latestRisk = evidence?.risk_history?.[0];
  const risk = riskLabel(latestRisk?.adjusted_score);
  const latestCondition = evidence?.condition?.[0];
  const conditionGrade =
    latestCondition?.label || latestCondition?.grade || latestCondition?.predicted || latestCondition?.result || null;

  async function run(action: string) {
    setPendingAction(action);
    const ok = await runMutation(
      () => post(`/admin/listings/${id}/review`, { action, reason: reason.trim() || "(no reason provided)" }),
      { success: `Listing ${action.replace(/_/g, " ")}d` },
    );
    setPendingAction(null);
    setPendingConfirm(null);
    if (ok) {
      setReason("");
      reload();
    }
  }

  const isDestructive = pendingConfirm === "block" || pendingConfirm === "suspend_seller";

  return (
    <div className="flex flex-col gap-4">
      {/* Page heading — template style px-4 lg:px-6 */}
      <div className="px-4 lg:px-6">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-col gap-1">
            <h1 className="text-2xl font-bold tracking-tight">Listing detail</h1>
            <p className="text-muted-foreground text-xs font-mono">{id}</p>
          </div>
          <Button variant="outline" size="sm" onClick={() => router.push("/listings")}>
            <ArrowLeft className="size-4" /> Back to listings
          </Button>
        </div>
      </div>

      {/* Overview card — template Card @container/card rounded-xl border shadow-xs */}
      <div className="@container/main px-4 lg:px-6">
        <Card className="@container/card">
          <CardHeader>
            <CardTitle className="text-xl font-semibold">{listing.title || "Untitled listing"}</CardTitle>
            <CardDescription className="flex flex-wrap items-center gap-x-3">
              <span>{listing.category}</span>
              <span className="font-medium text-foreground">{money(listing.price, listing.currency)}</span>
              <span>{typeof listing.condition === "object" ? JSON.stringify(listing.condition) : String(listing.condition ?? "—")}</span>
            </CardDescription>
            <CardAction>
              <div className="flex flex-wrap items-center justify-end gap-2">
                {conditionGrade && (
                  <StatusBadge tone={conditionTone(conditionGrade)} label={`${String(conditionGrade)} condition`} />
                )}
                {risk.band && (
                  <StatusBadge tone={riskTone[risk.band] ?? "neutral"} label={`${risk.label} risk`} />
                )}
                <StatusBadge tone={listing.status ? statusTone[listing.status] ?? "neutral" : "neutral"} label={listing.status || "—"} />
              </div>
            </CardAction>
          </CardHeader>
          <CardContent>
            {listing.description && <p className="mb-4 text-sm leading-relaxed text-muted-foreground">{listing.description}</p>}
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <Row label="Listing ID" mono>
                {listing._id}
              </Row>
              <Row label="Created">{fmtDate(listing.created_at)}</Row>
              <Row label="Updated">{fmtDate(listing.updated_at)}</Row>
              <Row label="Seller">
                {seller?.name || listing.seller_name ? (
                  <span className="flex flex-col items-end gap-0.5">
                    {seller?.id || listing.seller_id ? (
                      <Link href={`/users/${seller?.id || listing.seller_id}`} className="text-sm font-medium underline-offset-4 hover:underline">
                        {capitalize(seller?.name || listing.seller_name)}
                      </Link>
                    ) : (
                      <span className="text-sm font-medium">{capitalize(seller?.name || listing.seller_name)}</span>
                    )}
                    {seller?.email && <span className="text-xs text-muted-foreground">{seller.email}</span>}
                  </span>
                ) : (
                  "—"
                )}
              </Row>
              <Row label="Seller ID" mono>
                {seller?.id || listing.seller_id || "—"}
              </Row>
              <Row label="Tamper evidence">
                <StatusBadge tone="success" label="SHA256 sealed" />
              </Row>
              {(listing.notes_field || listing.notes) && <Row label="Notes">{listing.notes_field || listing.notes}</Row>}
            </dl>
          </CardContent>
        </Card>
      </div>

      {/* Main grid — constrained inside @container/main to avoid SidebarInset overflow */}
      <div className="@container/main px-4 lg:px-6">
        <div className="grid gap-4 xl:grid-cols-3">
          <div className="space-y-4 xl:col-span-2 min-w-0">
            <Section title="Images & tamper evidence" icon={Fingerprint}>
              {!evidence?.images?.length && <EmptyNote>No images received for this listing.</EmptyNote>}
              <div className="grid gap-3 sm:grid-cols-2">
                {evidence?.images?.map((img: any) => (
                  <figure key={img._id} className="overflow-hidden rounded-lg border bg-card shadow-xs">
                    <div className="relative aspect-[4/3] bg-muted">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img
                        src={`/uploads/${img.stored_key}`}
                        alt={`${img.angle} view`}
                        className="absolute inset-0 h-full w-full object-contain"
                        loading="lazy"
                      />
                    </div>
                    <figcaption className="flex items-center justify-between gap-2 p-3">
                      <span className="text-sm font-medium capitalize">{img.angle.replace(/_/g, " ")}</span>
                      <StatusBadge
                        tone={img.quality?.passed === false ? "danger" : "success"}
                        label={img.quality?.passed === false ? "quality fail" : "sealed"}
                      />
                    </figcaption>
                    <div className="space-y-1 border-t px-3 pb-3 pt-2 font-mono text-[11px] text-muted-foreground">
                      <p className="truncate" title={img.stored_key}>
                        key: {img.stored_key}
                      </p>
                      <p className="truncate" title={img.sha256}>
                        sha256: {img.sha256?.slice(0, 32)}…
                      </p>
                    </div>
                    <p className="px-3 pb-3 text-[11px] text-muted-foreground">
                      server:{" "}
                      {img.server_quality
                        ? typeof img.server_quality.passed === "boolean"
                          ? img.server_quality.passed
                            ? "passed"
                            : "failed"
                          : img.server_quality.error || "n/a"
                        : "n/a"}{" "}
                      · {timeAgo(img.created_at)}
                    </p>
                  </figure>
                ))}
              </div>
            </Section>

            <Section title="Risk history" icon={Gauge}>
              {!evidence?.risk_history?.length && <EmptyNote>No risk scores computed yet — run verification from the queue.</EmptyNote>}
              {evidence?.risk_history?.length > 0 && (
                <EvidenceTable
                  head={
                    <>
                      <TableHead>Score</TableHead>
                      <TableHead>Badge</TableHead>
                      <TableHead>Calculated</TableHead>
                    </>
                  }
                >
                  {evidence.risk_history.map((r: any) => {
                    const info = riskLabel(r.adjusted_score ?? r.raw_score);
                    const bandLabel = info.band ? info.band.charAt(0).toUpperCase() + info.band.slice(1) : "";
                    return (
                      <TableRow key={r._id}>
                        <TableCell className="font-medium tabular-nums">{r.adjusted_score ?? r.raw_score ?? "—"}</TableCell>
                        <TableCell>
                          <StatusBadge
                            tone={info.band ? riskTone[info.band] ?? "neutral" : "neutral"}
                            label={bandLabel ? `${bandLabel} · ${info.label}` : info.label}
                          />
                        </TableCell>
                        <TableCell className="text-xs text-muted-foreground">{fmtDate(r.created_at)}</TableCell>
                      </TableRow>
                    );
                  })}
                </EvidenceTable>
              )}
              {latestRisk?.factors && <DetailMap title="Factors" data={latestRisk.factors} />}
              {latestRisk?.signals && <DetailMap title="Signals" data={latestRisk.signals} />}
            </Section>

            <Section title="Decisions" icon={ShieldCheck}>
              {!evidence?.decisions?.length && <EmptyNote>No moderation decisions recorded.</EmptyNote>}
              {evidence?.decisions?.length > 0 && (
                <EvidenceTable
                  head={
                    <>
                      <TableHead>Status</TableHead>
                      <TableHead>Reason</TableHead>
                      <TableHead>When</TableHead>
                    </>
                  }
                >
                  {evidence.decisions.map((d: any) => {
                    const reasons = d.reasons?.length
                      ? d.reasons
                      : d.hard_stops?.length
                        ? d.hard_stops
                        : d.reason
                          ? [{ level: d.level || "danger", message: d.reason }]
                          : [];
                    return (
                      <TableRow key={d._id}>
                        <TableCell>
                          <StatusBadge tone={d.status ? statusTone[d.status] ?? "neutral" : "neutral"} label={d.status || "—"} />
                        </TableCell>
                        <TableCell>
                          {reasons.length ? (
                            <div className="flex max-w-80 flex-col gap-1">
                              {reasons.map((r: any, i: number) => (
                                <span key={i} className="flex items-start gap-1.5">
                                  <span className={cn("mt-1 size-1.5 shrink-0 rounded-full", levelDot[r.level] || "bg-muted-foreground/60")} />
                                  <span className="text-xs leading-tight">{r.message}</span>
                                </span>
                              ))}
                              {d.reason_code && (
                                <span className="font-mono text-[10px] uppercase text-muted-foreground">{d.reason_code}</span>
                              )}
                            </div>
                          ) : (
                            <span className="text-xs text-muted-foreground">—</span>
                          )}
                        </TableCell>
                        <TableCell className="text-xs text-muted-foreground">{fmtDate(d.created_at)}</TableCell>
                      </TableRow>
                    );
                  })}
                </EvidenceTable>
              )}
            </Section>

            <Section title="Detections" icon={FileSearch}>
              {evidence?.detections?.length ? (
                <>
                  <p className="mb-2 text-xs text-muted-foreground">
                    Showing {evidence.detections.length} detection{evidence.detections.length !== 1 ? "s" : ""} for{" "}
                    <span className="font-medium text-foreground">{listing.category ?? "—"}</span> — relevant types:{" "}
                    {defectsFor(listing.category).slice(0, 6).map(defectLabel).join(", ")}…
                  </p>
                  <EvidenceTable
                    head={
                      <>
                        <TableHead>Type</TableHead>
                        <TableHead>Category match</TableHead>
                        <TableHead>Confidence</TableHead>
                        <TableHead>Label</TableHead>
                      </>
                    }
                  >
                    {evidence.detections.map((d: any) => {
                      const cls = d.det_type || d.class || d.technique || "";
                      const relevant = isRelevantDefect(listing.category, cls);
                      return (
                        <TableRow key={d._id}>
                          <TableCell className="text-sm">
                            <span className="flex items-center gap-2">
                              {defectLabel(cls)}
                              <StatusBadge tone="neutral" label={defectFamily(cls)} className="text-[10px] leading-none" />
                            </span>
                          </TableCell>
                          <TableCell>
                            <StatusBadge tone={relevant ? "success" : "neutral"} label={relevant ? "relevant" : "other category"} />
                          </TableCell>
                          <TableCell className="tabular-nums">{d.confidence != null ? `${(d.confidence * 100).toFixed(0)}%` : "—"}</TableCell>
                          <TableCell className="text-xs text-muted-foreground">{d.label || "—"}</TableCell>
                        </TableRow>
                      );
                    })}
                  </EvidenceTable>
                  <div className="mt-3 flex flex-wrap gap-1.5">
                    {defectsFor(listing.category).map((c) => (
                      <StatusBadge key={c} tone="neutral" label={defectLabel(c)} className="text-[11px]" />
                    ))}
                  </div>
                  <p className="mt-1 text-[11px] text-muted-foreground">
                    Full taxonomy per category — highlighted row is what the model actually detected. Irrelevant types appear as “other category”.
                  </p>
                </>
              ) : (
                <EmptyNote>No detections recorded for this listing (image tampering and defect detection run only when photos are submitted through the app).</EmptyNote>
              )}
            </Section>

            <Section title="Device diagnostics" icon={Stethoscope}>
              {evidence?.diagnostics?.length ? (
                <div className="grid gap-3 xl:grid-cols-2">
                  {evidence.diagnostics.map((d: any) => {
                    const failed = (d.metrics?.failed ?? 0) > 0 || (d.tests || []).some((t: any) => t.passed === false || t.status === "failed");
                    const passedCount = d.metrics?.passed ?? (d.tests || []).filter((t: any) => t.passed !== false && t.status !== "failed").length;
                    return (
                      <div key={d._id} className="rounded-lg border p-3 shadow-xs">
                        <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                          <div className="min-w-0 space-y-0.5">
                            <p className="truncate text-sm font-medium">
                              {d.device?.model || capitalize(d.category || "device") || "Device"}
                              {d.device?.os_version ? ` · ${d.device.os_version}` : ""}
                            </p>
                            <p className="font-mono text-[10px] uppercase tracking-wide text-muted-foreground">
                              report v{d.report_version || "1.0"}
                              {d.simulated ? " · simulated" : ""}
                            </p>
                          </div>
                          <StatusBadge tone={failed ? "danger" : "success"} label={`${failed ? "fail" : "pass"}${d.score != null ? ` · ${d.score}` : ""}`} />
                        </div>
                        <div className="space-y-1">
                          {(d.tests || []).map((t: any, i: number) => {
                            const ok = !(t.passed === false || t.status === "failed");
                            return (
                              <div key={`${t.id}-${i}`} className="flex items-center justify-between gap-2 text-xs">
                                <span className="flex min-w-0 items-center gap-1.5">
                                  <span className={cn("size-1.5 shrink-0 rounded-full", ok ? "bg-success" : "bg-destructive")} />
                                  <span className="truncate">{humanizeTest(t.id)}</span>
                                </span>
                                <span className="shrink-0 text-muted-foreground">
                                  {ok ? "passed" : "failed"}
                                  {t.value != null ? ` · ${t.value}${t.unit ? ` ${t.unit}` : ""}` : ""}
                                </span>
                              </div>
                            );
                          })}
                          {!d.tests?.length && (
                            <p className="text-xs text-muted-foreground">no per-test data recorded{d.score != null ? ` · score ${d.score}` : ""}</p>
                          )}
                        </div>
                        {d.basis?.length ? <p className="mt-2 text-[11px] text-muted-foreground">{d.basis.join(" · ")}</p> : null}
                        <p className="mt-2 text-[11px] text-muted-foreground">
                          {d.metrics?.passed != null
                            ? `${d.metrics.passed} passed · ${d.metrics.failed ?? 0} failed · ${d.metrics.unsupported ?? 0} unsupported`
                            : passedCount > 0
                              ? `${passedCount} tests passed`
                              : ""}
                          {d.missing_penalty ? ` · penalty ${d.missing_penalty}` : ""} · {timeAgo(d.created_at)}
                        </p>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <EmptyNote>No device diagnostics received for this listing.</EmptyNote>
              )}
            </Section>

            <Section title="Predicted condition" icon={Gauge}>
              {evidence?.condition?.length ? (
                <div className="grid gap-3 sm:grid-cols-2">
                  {evidence.condition.map((c: any) => {
                    const grade = c.label || c.grade || c.predicted || c.result;
                    const probs = c.probabilities && typeof c.probabilities === "object" ? Object.entries(c.probabilities) : [];
                    const top = [...probs].sort((a: any, b: any) => Number(b[1]) - Number(a[1]))[0];
                    return (
                      <div key={c._id} className="rounded-lg border p-3 shadow-xs">
                        <div className="mb-1 flex items-center justify-between">
                          <span className="text-sm font-medium">Predicted condition</span>
                          {grade ? <StatusBadge tone={conditionTone(grade)} label={String(grade)} /> : <span className="text-xs text-muted-foreground">—</span>}
                        </div>
                        {top && <p className="text-xs text-muted-foreground">confidence {(Number(top[1]) * 100).toFixed(0)}%</p>}
                        {c.model_version && (
                          <p className="mt-1 font-mono text-[11px] text-muted-foreground">
                            model {c.model_version}
                            {c.simulated ? " · simulated" : ""}
                          </p>
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : (
                <EmptyNote>No condition prediction recorded for this listing.</EmptyNote>
              )}
            </Section>

            <Card className="@container/card">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <ScrollText className="size-4" /> Audit trail
                </CardTitle>
                <CardDescription>Actions taken against this listing</CardDescription>
              </CardHeader>
              <CardContent>
                {!evidence?.audit_trail?.length && <EmptyNote>No audit entries yet.</EmptyNote>}
                <div className="space-y-1">
                  {evidence?.audit_trail?.map((a: any) => (
                    <div key={a._id} className="flex items-start gap-3 py-1.5 text-sm">
                      <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-muted-foreground/60" />
                      <div className="min-w-0 flex-1">
                        <p className="leading-tight">{a.action}</p>
                        <p className="text-xs text-muted-foreground">
                          {timeAgo(a.created_at)}
                          {a.detail ? ` · ${typeof a.detail === "string" ? a.detail : JSON.stringify(a.detail)}` : ""}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="space-y-4 min-w-0">
            <Card className="@container/card">
              <CardHeader>
                <CardTitle className="text-base">Moderation action</CardTitle>
                <CardDescription>Every decision is audit-logged with a reason.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="space-y-1.5">
                  <Label htmlFor="reason">Reason</Label>
                  <Textarea
                    id="reason"
                    aria-label="Moderation reason"
                    rows={4}
                    placeholder="Evidence summary for the audit trail…"
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  {ACTIONS.map((a) => (
                    <Button
                      key={a.key}
                      variant={ACTION_BUTTON_STYLE[a.variant].variant}
                      className={cn("w-full", ACTION_BUTTON_STYLE[a.variant].className)}
                      disabled={pendingAction !== null}
                      onClick={() => setPendingConfirm(a.key)}
                    >
                      {pendingAction === a.key ? <Loader2 className="size-4 animate-spin" /> : <StatusIcon action={a.key} />}
                      {a.label}
                    </Button>
                  ))}
                </div>
                <p className="text-xs text-muted-foreground">Approve publishes the listing; Block and Suspend seller move it to restricted state.</p>
              </CardContent>
            </Card>

            <Card className="@container/card">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <ImageIcon className="size-4" /> Evidence counts
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-3 gap-3 text-sm">
                  <Info label="Images" value={String(evidence?.images?.length ?? 0)} />
                  <Info label="Scores" value={String(evidence?.risk_history?.length ?? 0)} />
                  <Info label="Detections" value={String(evidence?.detections?.length ?? 0)} />
                  <Info label="Diagnostics" value={String(evidence?.diagnostics?.length ?? 0)} />
                  <Info label="Decisions" value={String(evidence?.decisions?.length ?? 0)} />
                  <Info label="Audit events" value={String(evidence?.audit_trail?.length ?? 0)} />
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      </div>

      <Dialog
        open={pendingConfirm !== null}
        onOpenChange={(open) => {
          if (!open) setPendingConfirm(null);
        }}
      >
        <DialogContent className="sm:max-w-[420px]">
          <DialogHeader>
            <DialogTitle>{pendingConfirm ? `Run "${actionLabel(pendingConfirm)}" on this listing?` : ""}</DialogTitle>
            <DialogDescription>The decision is audit-logged with the reason above.</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" disabled={pendingAction !== null} onClick={() => setPendingConfirm(null)}>
              Cancel
            </Button>
            <Button variant={isDestructive ? "destructive" : "default"} disabled={pendingAction !== null} onClick={() => pendingConfirm && run(pendingConfirm)}>
              {pendingAction !== null ? <Loader2 className="size-4 animate-spin" /> : null}
              {pendingAction !== null ? "Working…" : pendingConfirm ? actionLabel(pendingConfirm) : "Confirm"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border p-3 text-center shadow-xs">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-lg font-bold tabular-nums">{value}</p>
    </div>
  );
}

function Row({ label, mono, children }: { label: string; mono?: boolean; children: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-2">
      <dt className="shrink-0 text-muted-foreground">{label}</dt>
      <dd className={`min-w-0 text-right ${mono ? "max-w-52 truncate font-mono text-xs leading-6" : ""}`}>{children}</dd>
    </div>
  );
}

function Section({ title, icon: Icon, children }: { title: string; icon: any; children: React.ReactNode }) {
  return (
    <Card className="@container/card">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Icon className="size-4" /> {title}
        </CardTitle>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

function EvidenceTable({ head, children }: { head: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="overflow-hidden rounded-lg border">
      <Table>
        <TableHeader className="bg-muted/50">
          <TableRow>{head}</TableRow>
        </TableHeader>
        <TableBody>{children}</TableBody>
      </Table>
    </div>
  );
}

function EmptyNote({ children }: { children: React.ReactNode }) {
  return <p className="text-sm text-muted-foreground">{children}</p>;
}

function DetailMap({ title, data }: { title: string; data: any }) {
  const entries = Array.isArray(data) ? data.map((v: any, i: number) => [i, v]) : Object.entries(data);
  return (
    <div className="rounded-lg border p-3 shadow-xs">
      <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{title}</p>
      <div className="space-y-1 text-xs">
        {entries.map(([k, v], i) => (
          <div key={i} className="flex justify-between gap-3">
            <span className="text-muted-foreground">{k}</span>
            <span className="text-right font-medium">{typeof v === "object" ? JSON.stringify(v) : String(v)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
