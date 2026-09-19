"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  Activity,
  AlertTriangle,
  ArrowUpRight,
  BadgeCheck,
  Flag,
  Mail,
  Package,
  ScrollText,
  ShieldAlert,
  Users,
  Wallet,
  TrendingUp,
  Sparkles,
  ShieldCheck as VerifiedIcon,
} from "lucide-react";
import { Area, AreaChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useFetch } from "@/lib/use-fetch";
import type { DashboardData } from "@/lib/types";
import { fmtDate, fmtNumber, money, riskLabel, timeAgo } from "@/lib/format";
import { Card, Chip, Button, Skeleton } from "@heroui/react";

const RISK_COLORS: Record<string, string> = { low: "#C6F135", medium: "#f59e0b", high: "#ef4444" };
const STATUS_STYLE: Record<string, string> = {
  approved: "#C6F135", published: "#C6F135", review: "#f59e0b", verifying: "#f59e0b", submitted: "#f59e0b",
  blocked: "#ef4444", draft: "#9ca3af", capturing: "#9ca3af", restricted: "#a78bfa", inspection_pending: "#0ea5e9",
};

function GreetingHeader({ onRefresh }: { onRefresh: () => void }) {
  const [greeting, setGreeting] = useState("Good morning");
  useEffect(() => {
    const h = new Date().getHours();
    if (h < 12) setGreeting("Good morning");
    else if (h < 17) setGreeting("Good afternoon");
    else setGreeting("Good evening");
  }, []);
  return (
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="text-[22px] font-semibold tracking-tight flex items-center gap-2">
          {greeting}, Admin
          <span className="inline-flex size-7 items-center justify-center rounded-full bg-accent text-accent-foreground">
            <Sparkles className="size-3.5" />
          </span>
        </h1>
        <p className="text-sm text-muted-foreground mt-1">Stay ahead with real-time trust & verification insights</p>
      </div>
      <div className="flex items-center gap-2">
        <Button variant="secondary" size="sm" onPress={onRefresh} className="rounded-full">
          Refresh
        </Button>
        <Link href="/queue">
          <Button variant="primary" size="sm" className="rounded-full">
            Open queue <ArrowUpRight className="size-3.5" />
          </Button>
        </Link>
      </div>
    </div>
  );
}

function KpiCard({ label, value, sub, icon: Icon, tone = "default" }: { label: string; value: React.ReactNode; sub?: string; icon: any; tone?: "lime" | "danger" | "info" | "default" | "success" | "warning" }) {
  const toneMap: Record<string,string> = {
    lime: "bg-accent text-accent-foreground",
    success: "bg-success text-success-foreground",
    danger: "bg-danger text-danger-foreground",
    warning: "bg-warning text-warning-foreground",
    info: "bg-accent text-accent-foreground",
    default: "bg-default text-default-foreground",
  };
  return (
    <Card className="relative overflow-hidden border-0 shadow-sm ring-1 ring-black/5 dark:ring-white/10 rounded-2xl">
      <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-black/5 to-transparent dark:via-white/10" />
      <Card.Header className="pb-2">
        <div className="flex items-start justify-between">
          <Card.Description className="text-[11px] font-semibold uppercase tracking-widest">{label}</Card.Description>
          <span className={`inline-flex size-9 items-center justify-center rounded-xl text-xs ${toneMap[tone]}`}>
            <Icon className="size-4" />
          </span>
        </div>
      </Card.Header>
      <Card.Content className="pt-0">
        <div className="text-2xl font-bold tracking-tight tabular-nums">{value}</div>
        {sub && <p className="text-xs text-muted-foreground mt-1">{sub}</p>}
      </Card.Content>
    </Card>
  );
}

export default function DashboardPage() {
  const { data, loading, error, reload } = useFetch<DashboardData>("/admin/dashboard");

  if (error) {
    return (
      <div className="space-y-4">
        <GreetingHeader onRefresh={reload} />
        <Card className="p-8 text-center"><p className="text-sm text-muted-foreground">{error.message}</p><Button onPress={reload} variant="secondary" className="mt-3">Retry</Button></Card>
      </div>
    );
  }

  if (loading || !data) {
    return (
      <div className="space-y-6">
        <div className="h-16 rounded-2xl bg-muted animate-pulse" />
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-28 rounded-2xl" />
          ))}
        </div>
      </div>
    );
  }

  const k = data.kpis;
  const trend = data.trend.map((t) => ({ day: fmtDate(t.date, { month: "short", day: "numeric" }), count: t.count }));
  const statusRows = Object.entries(data.status_distribution).sort((a, b) => b[1] - a[1]);
  const riskRows = Object.entries(data.risk_distribution) as [string, number][];
  const totalStatuses = statusRows.reduce((a, [, n]) => a + n, 0);

  return (
    <div className="space-y-6">
      <GreetingHeader onRefresh={reload} />

      {/* KPI bento */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard label="Total listings" value={fmtNumber(k.total_listings)} sub={`${k.new_today} today · ${k.new_7d} last 7d`} icon={Package} tone="lime" />
        <KpiCard label="Pending review" value={fmtNumber(k.pending_review)} sub="submitted · verifying · review" icon={ShieldAlert} tone="info" />
        <KpiCard label="High-risk" value={fmtNumber(k.high_risk)} sub={`${k.approval_rate}% approval rate`} icon={AlertTriangle} tone="danger" />
        <KpiCard label="Avg risk score" value={k.avg_risk > 0 ? k.avg_risk : "—"} sub={`over ${fmtNumber(data.risk_samples)} scores`} icon={Activity} tone="warning" />
        <KpiCard label="Users" value={fmtNumber(k.users.total)} sub={`${k.users.sellers} sellers · ${k.users.suspended} suspended`} icon={Users} tone="default" />
        <KpiCard label="Escrow held" value={money(k.escrow_held)} sub={`${money(k.escrow_in_review)} in review`} icon={Wallet} tone="success" />
        <KpiCard label="Reports" value={fmtNumber(k.reports_pending)} sub="pending moderation" icon={Flag} tone="danger" />
        <KpiCard label="Messages" value={fmtNumber(k.messages_new)} sub="new inbox" icon={Mail} tone="default" />
      </div>

      {/* Main charts — bento 7/5 */}
      <div className="grid gap-4 lg:grid-cols-12">
        <Card className="lg:col-span-7 rounded-2xl overflow-hidden">
          <Card.Header className="border-b">
            <div className="flex items-center justify-between">
              <div>
                <Card.Title className="text-[15px] font-semibold flex items-center gap-2"><TrendingUp className="size-4 text-muted-foreground" /> Listings created</Card.Title>
                <Card.Description>Last 14 days • {fmtNumber(k.total_listings)} total</Card.Description>
              </div>
              <Chip size="sm" variant="soft" color="success">Live</Chip>
            </div>
          </Card.Header>
          <Card.Content className="pt-4">
            <div className="h-[264px]">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={trend} margin={{ top: 4, right: 4, left: -18, bottom: 0 }}>
                  <defs>
                    <linearGradient id="limeFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#C6F135" stopOpacity={0.45} />
                      <stop offset="100%" stopColor="#C6F135" stopOpacity={0.02} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-border" vertical={false} />
                  <XAxis dataKey="day" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} interval="preserveStartEnd" minTickGap={24} />
                  <YAxis tick={{ fontSize: 11 }} tickLine={false} axisLine={false} allowDecimals={false} />
                  <Tooltip
                    contentStyle={{ borderRadius: 12, border: "1px solid var(--border)", background: "var(--popover)", fontSize: 12 }}
                    formatter={(v: any) => [v, "Listings"]}
                  />
                  <Area type="monotone" dataKey="count" stroke="#C6F135" strokeWidth={2.5} fill="url(#limeFill)" dot={false} activeDot={{ r: 4, fill: "#C6F135" }} />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </Card.Content>
        </Card>

        <Card className="lg:col-span-5 rounded-2xl overflow-hidden">
          <Card.Header className="border-b">
            <Card.Title className="text-[15px] font-semibold">Risk distribution</Card.Title>
            <Card.Description>{fmtNumber(data.risk_samples)} latest scores • balanced trust</Card.Description>
          </Card.Header>
          <Card.Content className="pt-4">
            <div className="h-44">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={riskRows.map(([k2, v]) => ({ name: k2, value: v }))} dataKey="value" nameKey="name" innerRadius={56} outerRadius={80} paddingAngle={3} strokeWidth={0}>
                    {riskRows.map(([k2]) => (
                      <Cell key={k2} fill={RISK_COLORS[k2] || "#9ca3af"} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={{ borderRadius: 12, border: "1px solid var(--border)", background: "var(--popover)", fontSize: 12 }} />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="mt-3 grid grid-cols-3 gap-2">
              {riskRows.map(([k2, v]) => (
                <div key={k2} className="rounded-xl bg-muted/50 p-2.5 text-center">
                  <span className="inline-flex size-2 rounded-full mb-1" style={{ background: RISK_COLORS[k2] }} />
                  <p className="text-xs capitalize font-medium">{k2}</p>
                  <p className="text-lg font-bold tabular-nums">{v}</p>
                </div>
              ))}
            </div>
          </Card.Content>
        </Card>
      </div>

      {/* Second row — status + flagged */}
      <div className="grid gap-4 lg:grid-cols-12">
        <Card className="lg:col-span-5 rounded-2xl">
          <Card.Header className="border-b">
            <Card.Title className="text-[15px] font-semibold">Status distribution</Card.Title>
            <Card.Description>{fmtNumber(k.total_listings)} total • live pipeline</Card.Description>
          </Card.Header>
          <Card.Content className="pt-4 space-y-3">
            {statusRows.length === 0 && <p className="text-sm text-muted-foreground">No listings yet.</p>}
            {statusRows.map(([s, n]) => (
              <div key={s} className="space-y-1.5">
                <div className="flex items-center justify-between text-xs">
                  <span className="capitalize font-medium">{s.replace(/_/g, " ")}</span>
                  <span className="font-mono text-muted-foreground">{n} · {totalStatuses ? Math.round((n / totalStatuses) * 100) : 0}%</span>
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-muted">
                  <div className="h-full rounded-full transition-all" style={{ width: totalStatuses ? `${(n / totalStatuses) * 100}%` : "0%", background: STATUS_STYLE[s] || "#9ca3af" }} />
                </div>
              </div>
            ))}
          </Card.Content>
        </Card>

        <Card className="lg:col-span-7 rounded-2xl overflow-hidden">
          <Card.Header className="border-b flex-row items-center justify-between">
            <div>
              <Card.Title className="text-[15px] font-semibold">Flagged queue</Card.Title>
              <Card.Description>Latest review / blocked decisions</Card.Description>
            </div>
            <Link href="/queue">
              <Button variant="secondary" size="sm" className="rounded-full">View queue</Button>
            </Link>
          </Card.Header>
          <Card.Content className="p-0">
            {data.recent_flagged.length === 0 ? (
              <p className="p-6 text-sm text-muted-foreground">Nothing flagged — the system is quiet.</p>
            ) : (
              <div className="divide-y">
                {data.recent_flagged.map((it: any, idx: number) => {
                  const label = riskLabel(it.risk?.adjusted_score);
                  return (
                    <Link key={`${it.listing?._id}-${it.decision?._id || it.risk?._id || idx}`} href={`/listings/${it.listing?._id}`} className="flex items-center gap-3 p-4 hover:bg-muted/50 transition-colors">
                      <div className="size-9 rounded-xl bg-accent text-accent-foreground grid place-items-center shrink-0 font-bold text-xs">{(it.listing?.title || "?")[0]}</div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium">{it.listing?.title || "Untitled listing"}</p>
                        <p className="text-xs text-muted-foreground truncate">{it.listing?.category} · {it.seller?.name || "unknown"} · {timeAgo(it.listing?.created_at)}</p>
                      </div>
                      <Chip size="sm" variant="soft" color={(label.band === "high" ? "danger" : label.band === "medium" ? "warning" : "success") as any}>{label.label}</Chip>
                    </Link>
                  );
                })}
              </div>
            )}
          </Card.Content>
        </Card>
      </div>

      {/* Activity */}
      <Card className="rounded-2xl">
        <Card.Header className="border-b">
          <Card.Title className="text-[15px] font-semibold flex items-center gap-2"><ScrollText className="size-4" /> Recent activity</Card.Title>
          <Card.Description>Latest admin audit events • {fmtDate(Date.now()/1000)}</Card.Description>
        </Card.Header>
        <Card.Content className="pt-4">
          {data.recent_activity.length === 0 ? (
            <p className="text-sm text-muted-foreground">No audit events yet.</p>
          ) : (
            <div className="space-y-1">
              {data.recent_activity.map((a: any) => (
                <div key={a._id} className="flex items-start gap-3 rounded-xl p-2 hover:bg-muted/50">
                  <span className="mt-1 size-2 rounded-full bg-accent shrink-0" />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm leading-none">{a.action}</p>
                    <p className="text-xs text-muted-foreground mt-1">{a.target_type ? `${a.target_type} ${a.target_id?.slice(0, 8)}… · ` : ""}{timeAgo(a.created_at)}</p>
                  </div>
                  <BadgeCheck className="size-4 text-muted-foreground/40 shrink-0 mt-0.5" />
                </div>
              ))}
            </div>
          )}
        </Card.Content>
      </Card>
    </div>
  );
}
