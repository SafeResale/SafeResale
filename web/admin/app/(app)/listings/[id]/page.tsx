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
  ScrollText,
  ShieldCheck,
  Stethoscope,
  TriangleAlert,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { post } from "@/lib/api";
import { useFetch, runMutation } from "@/lib/use-fetch";
import { capitalize, fmtDate, money, riskLabel, timeAgo } from "@/lib/format";
import { PageHeader } from "@/components/page-header";
import { PageError } from "@/components/error-state";
import {
  Button,
  Card,
  Chip,
  Label,
  Skeleton,
  Spinner,
  Surface,
  Table,
  TextArea,
  AlertDialog,
} from "@heroui/react";

// Chip color matches HeroUI v3: accent is SafeResale lime (#C6F135 → oklch 0.8967 0.2039 122.4)
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
  active: "success",
  approved: "success",
  published: "success",
  released: "success",
  verified: "success",
  resolved: "success",
  ok: "success",
  live: "success",
  review_passed: "warning",
  warn: "warning",
  held: "info",
  pending: "warning",
  review: "warning",
  in_review: "warning",
  verifying: "warning",
  submitted: "warning",
  new: "warning",
  inspection_pending: "info",
  capturing: "neutral",
  draft: "neutral",
  expired: "neutral",
  archived: "neutral",
  dismissed: "neutral",
  refunded: "neutral",
  deactivated: "neutral",
  sold: "info",
  paid: "info",
  shipped: "info",
  delivered: "info",
  unverified: "neutral",
  suspended: "danger",
  blocked: "danger",
  restricted: "danger",
  rejected: "danger",
  disputed: "danger",
  read: "info",
};

const riskToneMap: Record<string, string> = {
  low: "success",
  medium: "warning",
  high: "danger",
};

function chipColorForStatus(status?: string): ChipColor {
  const tone = status ? statusToneMap[status] || "neutral" : "neutral";
  return toneToChipColor[tone] || "default";
}

function chipColorForRiskBand(band: string | null): ChipColor {
  if (!band) return "default";
  const tone = riskToneMap[band] || "neutral";
  return toneToChipColor[tone] || "default";
}

// SafeResale condition → HeroUI Chip color. Good/excellent → lime accent, fair → warning, poor → danger
function chipColorForCondition(grade: unknown): ChipColor {
  const s = String(grade ?? "").toLowerCase();
  if (/excellent|good|like ?new|pass|immaculate/.test(s)) return "accent";
  if (/fair|moderate|average/.test(s)) return "warning";
  if (/poor|fail|worn|damaged/.test(s)) return "danger";
  return "default";
}

function chipVariantForCondition(grade: unknown): "soft" | "secondary" {
  const s = String(grade ?? "").toLowerCase();
  if (/excellent|good|like ?new|pass|immaculate/.test(s)) return "soft";
  return "soft";
}

const ACTIONS: { key: string; label: string; variant: "primary" | "secondary" | "danger" }[] = [
  { key: "approve", label: "Approve", variant: "primary" },
  { key: "warn", label: "Warn", variant: "secondary" },
  { key: "block", label: "Block", variant: "danger" },
  { key: "request_inspection", label: "Request inspection", variant: "secondary" },
  { key: "suspend_seller", label: "Suspend seller", variant: "danger" },
];

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
  danger: "bg-danger",
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
      <div>
        <PageHeader title="Listing detail" description={id} />
        <PageError message={error.message} onRetry={reload} />
      </div>
    );
  }

  if (loading || !data) {
    return (
      <div>
        <PageHeader title="Listing detail" description={id} />
        <div className="space-y-4">
          <Skeleton className="h-40 rounded-2xl" />
          <Skeleton className="h-64 rounded-2xl" />
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
    <div>
      <PageHeader
        title="Listing detail"
        description={id}
        actions={
          <Button variant="secondary" size="sm" onPress={() => router.push("/listings")}>
            <ArrowLeft className="size-4" /> Back to listings
          </Button>
        }
      />

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="space-y-6 xl:col-span-2">
          <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
            <Card.Header className="flex-row flex-wrap items-start justify-between gap-3 space-y-0">
              <div className="space-y-1">
                <Card.Title className="text-xl">{listing.title || "Untitled listing"}</Card.Title>
                <Card.Description className="flex flex-wrap items-center gap-x-3">
                  <span>{listing.category}</span>
                  <span className="font-medium text-foreground">{money(listing.price, listing.currency)}</span>
                  <span>{typeof listing.condition === "object" ? JSON.stringify(listing.condition) : String(listing.condition ?? "—")}</span>
                </Card.Description>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                {conditionGrade && (
                  <Chip color={chipColorForCondition(conditionGrade)} variant={chipVariantForCondition(conditionGrade)} size="sm">
                    {String(conditionGrade)} condition
                  </Chip>
                )}
                {risk.band && (
                  <Chip color={chipColorForRiskBand(risk.band)} variant="soft" size="sm">
                    {risk.label} risk
                  </Chip>
                )}
                <Chip color={chipColorForStatus(listing.status)} variant="soft" size="sm">
                  {listing.status || "—"}
                </Chip>
              </div>
            </Card.Header>
            <Card.Content className="px-6 pb-6">
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
                        <Link
                          href={`/users/${seller?.id || listing.seller_id}`}
                          className="text-sm font-medium underline-offset-4 hover:underline"
                        >
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
                  <Chip color="success" variant="soft" size="sm">
                    SHA256 sealed
                  </Chip>
                </Row>
                {(listing.notes_field || listing.notes) && <Row label="Notes">{listing.notes_field || listing.notes}</Row>}
              </dl>
            </Card.Content>
          </Card>

          <Section title="Images & tamper evidence" icon={Fingerprint}>
            {!evidence?.images?.length && <EmptyNote>No images received for this listing.</EmptyNote>}
            <div className="grid gap-3 sm:grid-cols-2">
              {evidence?.images?.map((img: any) => (
                <figure key={img._id} className="overflow-hidden rounded-2xl border bg-card ring-1 ring-black/5 dark:ring-white/10">
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
                    <Chip
                      color={img.quality?.passed === false ? "danger" : "success"}
                      variant="soft"
                      size="sm"
                    >
                      {img.quality?.passed === false ? "quality fail" : "sealed"}
                    </Chip>
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
                    <Table.Column isRowHeader>Score</Table.Column>
                    <Table.Column>Badge</Table.Column>
                    <Table.Column>Calculated</Table.Column>
                  </>
                }
              >
                {evidence.risk_history.map((r: any) => (
                  <Table.Row key={r._id} id={r._id}>
                    <Table.Cell className="font-medium tabular-nums">{r.adjusted_score ?? r.raw_score ?? "—"}</Table.Cell>
                    <Table.Cell>
                      <Chip color={chipColorForRiskBand(riskLabel(r.adjusted_score).band)} variant="soft" size="sm">
                        {r.badge || riskLabel(r.adjusted_score).label}
                      </Chip>
                    </Table.Cell>
                    <Table.Cell className="text-xs text-muted-foreground">{fmtDate(r.created_at)}</Table.Cell>
                  </Table.Row>
                ))}
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
                    <Table.Column isRowHeader>Status</Table.Column>
                    <Table.Column>Reason</Table.Column>
                    <Table.Column>When</Table.Column>
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
                    <Table.Row key={d._id} id={d._id}>
                      <Table.Cell>
                        <Chip color={chipColorForStatus(d.status)} variant="soft" size="sm">
                          {d.status || "—"}
                        </Chip>
                      </Table.Cell>
                      <Table.Cell>
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
                      </Table.Cell>
                      <Table.Cell className="text-xs text-muted-foreground">{fmtDate(d.created_at)}</Table.Cell>
                    </Table.Row>
                  );
                })}
              </EvidenceTable>
            )}
          </Section>

          <Section title="Detections" icon={FileSearch}>
            {evidence?.detections?.length ? (
              <EvidenceTable
                head={
                  <>
                    <Table.Column isRowHeader>Type</Table.Column>
                    <Table.Column>Confidence</Table.Column>
                    <Table.Column>Label</Table.Column>
                  </>
                }
              >
                {evidence.detections.map((d: any) => (
                  <Table.Row key={d._id} id={d._id}>
                    <Table.Cell className="text-sm">{d.det_type || d.class || d.technique || "—"}</Table.Cell>
                    <Table.Cell className="tabular-nums">{d.confidence != null ? `${(d.confidence * 100).toFixed(0)}%` : "—"}</Table.Cell>
                    <Table.Cell className="text-xs text-muted-foreground">{d.label || "—"}</Table.Cell>
                  </Table.Row>
                ))}
              </EvidenceTable>
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
                    <Surface key={d._id} variant="default" className="rounded-2xl border p-3 ring-1 ring-black/5 dark:ring-white/10">
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
                        <Chip color={failed ? "danger" : "success"} variant="soft" size="sm">
                          {failed ? "fail" : "pass"}
                          {d.score != null ? ` · ${d.score}` : ""}
                        </Chip>
                      </div>
                      <div className="space-y-1">
                        {(d.tests || []).map((t: any, i: number) => {
                          const ok = !(t.passed === false || t.status === "failed");
                          return (
                            <div key={`${t.id}-${i}`} className="flex items-center justify-between gap-2 text-xs">
                              <span className="flex min-w-0 items-center gap-1.5">
                                <span className={cn("size-1.5 shrink-0 rounded-full", ok ? "bg-success" : "bg-danger")} />
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
                    </Surface>
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
                    <Surface key={c._id} variant="default" className="rounded-2xl border p-3 ring-1 ring-black/5 dark:ring-white/10">
                      <div className="mb-1 flex items-center justify-between">
                        <span className="text-sm font-medium">Predicted condition</span>
                        {grade ? (
                          <Chip color={chipColorForCondition(grade)} variant="soft" size="sm">
                            {String(grade)}
                          </Chip>
                        ) : (
                          <span className="text-xs text-muted-foreground">—</span>
                        )}
                      </div>
                      {top && <p className="text-xs text-muted-foreground">confidence {(Number(top[1]) * 100).toFixed(0)}%</p>}
                      {c.model_version && (
                        <p className="mt-1 font-mono text-[11px] text-muted-foreground">
                          model {c.model_version}
                          {c.simulated ? " · simulated" : ""}
                        </p>
                      )}
                    </Surface>
                  );
                })}
              </div>
            ) : (
              <EmptyNote>No condition prediction recorded for this listing.</EmptyNote>
            )}
          </Section>

          <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
            <Card.Header>
              <Card.Title className="flex items-center gap-2 text-base">
                <ScrollText className="size-4" /> Audit trail
              </Card.Title>
              <Card.Description>Actions taken against this listing</Card.Description>
            </Card.Header>
            <Card.Content className="space-y-1 px-6 pb-6">
              {!evidence?.audit_trail?.length && <EmptyNote>No audit entries yet.</EmptyNote>}
              {evidence?.audit_trail?.map((a: any) => (
                <div key={a._id} className="flex items-start gap-3 py-1.5 text-sm">
                  <span className="mt-1 size-1.5 shrink-0 rounded-full bg-muted-foreground/60" />
                  <div className="min-w-0 flex-1">
                    <p>{a.action}</p>
                    <p className="text-xs text-muted-foreground">
                      {timeAgo(a.created_at)}
                      {a.detail ? ` · ${typeof a.detail === "string" ? a.detail : JSON.stringify(a.detail)}` : ""}
                    </p>
                  </div>
                </div>
              ))}
            </Card.Content>
          </Card>
        </div>

        <div className="space-y-6">
          <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
            <Card.Header>
              <Card.Title className="text-base">Moderation action</Card.Title>
              <Card.Description>Every decision is audit-logged with a reason.</Card.Description>
            </Card.Header>
            <Card.Content className="space-y-3 px-6 pb-6">
              <div className="space-y-1.5">
                <Label htmlFor="reason">Reason</Label>
                <TextArea
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
                    variant={a.variant}
                    className="w-full"
                    isDisabled={pendingAction !== null}
                    onPress={() => setPendingConfirm(a.key)}
                  >
                    {pendingAction === a.key ? <Spinner className="size-4" /> : <StatusIcon action={a.key} />}
                    {a.label}
                  </Button>
                ))}
              </div>
              <p className="text-xs text-muted-foreground">Approve publishes the listing; Block and Suspend seller move it to restricted state.</p>
            </Card.Content>
          </Card>

          <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
            <Card.Header>
              <Card.Title className="flex items-center gap-2 text-base">
                <ImageIcon className="size-4" /> Evidence counts
              </Card.Title>
            </Card.Header>
            <Card.Content className="grid grid-cols-3 gap-3 px-6 pb-6 text-sm">
              <Info label="Images" value={String(evidence?.images?.length ?? 0)} />
              <Info label="Scores" value={String(evidence?.risk_history?.length ?? 0)} />
              <Info label="Detections" value={String(evidence?.detections?.length ?? 0)} />
              <Info label="Diagnostics" value={String(evidence?.diagnostics?.length ?? 0)} />
              <Info label="Decisions" value={String(evidence?.decisions?.length ?? 0)} />
              <Info label="Audit events" value={String(evidence?.audit_trail?.length ?? 0)} />
            </Card.Content>
          </Card>
        </div>
      </div>

      <AlertDialog>
        <AlertDialog.Backdrop
          isOpen={pendingConfirm !== null}
          onOpenChange={(open) => {
            if (!open) setPendingConfirm(null);
          }}
          variant="blur"
        >
          <AlertDialog.Container>
            <AlertDialog.Dialog className="sm:max-w-[420px]">
              <AlertDialog.CloseTrigger isDisabled={pendingAction !== null} />
              <AlertDialog.Header>
                <AlertDialog.Icon status={isDestructive ? "danger" : "accent"} />
                <AlertDialog.Heading>{pendingConfirm ? `Run "${actionLabel(pendingConfirm)}" on this listing?` : ""}</AlertDialog.Heading>
              </AlertDialog.Header>
              <AlertDialog.Body>
                <p className="text-sm text-muted-foreground">The decision is audit-logged with the reason above.</p>
              </AlertDialog.Body>
              <AlertDialog.Footer>
                <Button variant="tertiary" slot="close" isDisabled={pendingAction !== null} onPress={() => setPendingConfirm(null)}>
                  Cancel
                </Button>
                <Button
                  variant={isDestructive ? "danger" : "primary"}
                  isDisabled={pendingAction !== null}
                  onPress={() => pendingConfirm && run(pendingConfirm)}
                >
                  {pendingAction !== null ? <Spinner className="size-4" /> : null}
                  {pendingAction !== null ? "Working…" : pendingConfirm ? actionLabel(pendingConfirm) : "Confirm"}
                </Button>
              </AlertDialog.Footer>
            </AlertDialog.Dialog>
          </AlertDialog.Container>
        </AlertDialog.Backdrop>
      </AlertDialog>
    </div>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <Surface variant="default" className="rounded-2xl border p-3 text-center ring-1 ring-black/5 dark:ring-white/10">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-lg font-bold tabular-nums">{value}</p>
    </Surface>
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
    <Card variant="default" className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
      <Card.Header>
        <Card.Title className="flex items-center gap-2 text-base">
          <Icon className="size-4" /> {title}
        </Card.Title>
      </Card.Header>
      <Card.Content className="space-y-3 px-6 pb-6">{children}</Card.Content>
    </Card>
  );
}

function EvidenceTable({ head, children }: { head: React.ReactNode; children: React.ReactNode }) {
  return (
    <Table>
      <Table.ScrollContainer>
        <Table.Content aria-label="Evidence table" className="min-w-[480px]">
          <Table.Header>{head}</Table.Header>
          <Table.Body>{children}</Table.Body>
        </Table.Content>
      </Table.ScrollContainer>
    </Table>
  );
}

function EmptyNote({ children }: { children: React.ReactNode }) {
  return <p className="text-sm text-muted-foreground">{children}</p>;
}

function DetailMap({ title, data }: { title: string; data: any }) {
  const entries = Array.isArray(data) ? data.map((v: any, i: number) => [i, v]) : Object.entries(data);
  return (
    <Surface variant="default" className="rounded-2xl border p-3 ring-1 ring-black/5 dark:ring-white/10">
      <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{title}</p>
      <div className="space-y-1 text-xs">
        {entries.map(([k, v], i) => (
          <div key={i} className="flex justify-between gap-3">
            <span className="text-muted-foreground">{k}</span>
            <span className="text-right font-medium">{typeof v === "object" ? JSON.stringify(v) : String(v)}</span>
          </div>
        ))}
      </div>
    </Surface>
  );
}
