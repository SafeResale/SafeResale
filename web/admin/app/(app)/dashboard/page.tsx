"use client";

import Link from "next/link";
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
} from "lucide-react";
import { Area, AreaChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useFetch } from "@/lib/use-fetch";
import type { DashboardData } from "@/lib/types";
import { fmtDate, fmtNumber, money, riskLabel, timeAgo } from "@/lib/format";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/page-header";
import { PageError } from "@/components/error-state";
import { StatCard } from "@/components/stat-card";
import { StatusBadge, riskTone } from "@/components/status-badge";
import { Skeleton } from "@/components/ui/skeleton";

const RISK_COLORS: Record<string, string> = { low: "#3b9e6d", medium: "#d99b2b", high: "#d64545" };
const STATUS_STYLE: Record<string, string> = {
  approved: "#3b9e6d", published: "#3b9e6d", review: "#d99b2b", verifying: "#d99b2b", submitted: "#d99b2b",
  blocked: "#d64545", draft: "#8a94a0", capturing: "#8a94a0", restricted: "#b06ad9", inspection_pending: "#3aa0c4",
};

export default function DashboardPage() {
  const { data, loading, error, reload } = useFetch<DashboardData>("/admin/dashboard");

  if (error) {
    return (
      <div>
        <PageHeader title="Dashboard" description="Verification Trust Engine — at a glance" />
        <PageError message={error.message} onRetry={reload} />
      </div>
    );
  }

  if (loading || !data) {
    return (
      <div>
        <PageHeader title="Dashboard" description="Verification Trust Engine — at a glance" />
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
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
    <div>
      <PageHeader
        title="Dashboard"
        description="Verification Trust Engine — at a glance"
        actions={
          <Button asChild>
            <Link href="/queue">
              <ShieldAlert className="size-4" /> Open queue
            </Link>
          </Button>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Total listings" value={fmtNumber(k.total_listings)} sub={`${k.new_today} today · ${k.new_7d} last 7d`} icon={Package} />
        <StatCard label="Pending review" value={fmtNumber(k.pending_review)} sub="submitted · verifying · review" icon={ShieldAlert} tone="info" />
        <StatCard label="High-risk (blocked)" value={fmtNumber(k.high_risk)} sub={`${k.approval_rate}% approval rate`} icon={AlertTriangle} tone="danger" />
        <StatCard label="Avg risk score" value={k.avg_risk > 0 ? k.avg_risk : "—"} sub={`over ${fmtNumber(data.risk_samples)} latest scores`} icon={Activity} />
        <StatCard label="Users" value={fmtNumber(k.users.total)} sub={`${k.users.sellers} sellers · ${k.users.suspended} suspended`} icon={Users} />
        <StatCard label="Escrow held" value={money(k.escrow_held)} sub={`${money(k.escrow_in_review)} in review`} icon={Wallet} tone="info" />
        <StatCard label="Pending reports" value={fmtNumber(k.reports_pending)} sub="content & user reports" icon={Flag} tone="danger" />
        <StatCard label="New messages" value={fmtNumber(k.messages_new)} sub="contact form inbox" icon={Mail} />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="text-base">Listings created</CardTitle>
            <CardDescription>Last 14 days</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={trend} margin={{ top: 4, right: 4, left: -18, bottom: 0 }}>
                  <defs>
                    <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="var(--chart-1)" stopOpacity={0.45} />
                      <stop offset="100%" stopColor="var(--chart-1)" stopOpacity={0.02} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-border" vertical={false} />
                  <XAxis dataKey="day" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} interval="preserveStartEnd" minTickGap={24} />
                  <YAxis tick={{ fontSize: 11 }} tickLine={false} axisLine={false} allowDecimals={false} />
                  <Tooltip
                    contentStyle={{ borderRadius: 8, border: "1px solid var(--border)", background: "var(--popover)", fontSize: 12 }}
                    formatter={(v: any) => [v, "Listings"]}
                  />
                  <Area type="monotone" dataKey="count" stroke="var(--chart-1)" strokeWidth={2} fill="url(#trendFill)" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Risk distribution</CardTitle>
            <CardDescription>{fmtNumber(data.risk_samples)} latest scores</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="h-44">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={riskRows.map(([k2, v]) => ({ name: k2, value: v }))} dataKey="value" nameKey="name" innerRadius={48} outerRadius={72} paddingAngle={2} strokeWidth={0}>
                    {riskRows.map(([k2]) => (
                      <Cell key={k2} fill={RISK_COLORS[k2] || "#8a94a0"} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={{ borderRadius: 8, border: "1px solid var(--border)", background: "var(--popover)", fontSize: 12 }} />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="mt-2 space-y-1.5">
              {riskRows.map(([k2, v]) => (
                <div key={k2} className="flex items-center justify-between text-xs">
                  <span className="flex items-center gap-1.5 capitalize">
                    <span className="size-2 rounded-full" style={{ background: RISK_COLORS[k2] }} />
                    {k2}
                  </span>
                  <span className="font-medium tabular-nums">{v}</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Status distribution</CardTitle>
            <CardDescription>{fmtNumber(k.total_listings)} total listings</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            {statusRows.length === 0 && <p className="text-sm text-muted-foreground">No listings yet.</p>}
            {statusRows.map(([s, n]) => (
              <div key={s} className="space-y-1">
                <div className="flex items-center justify-between text-xs">
                  <span className="capitalize">{s.replace(/_/g, " ")}</span>
                  <span className="font-medium tabular-nums">{n} · {totalStatuses ? Math.round((n / totalStatuses) * 100) : 0}%</span>
                </div>
                <div className="h-1.5 overflow-hidden rounded-full bg-muted">
                  <div className="h-full rounded-full" style={{ width: totalStatuses ? `${(n / totalStatuses) * 100}%` : "0%", background: STATUS_STYLE[s] || "var(--muted-foreground)" }} />
                </div>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <div>
              <CardTitle className="text-base">Recent flagged activity</CardTitle>
              <CardDescription>Latest review / blocked decisions</CardDescription>
            </div>
            <Button asChild variant="outline" size="sm">
              <Link href="/queue">
                View queue <ArrowUpRight className="size-3.5" />
              </Link>
            </Button>
          </CardHeader>
          <CardContent className="space-y-2">
            {data.recent_flagged.length === 0 && <p className="text-sm text-muted-foreground">Nothing flagged recently — the system is quiet.</p>}
            {data.recent_flagged.map((it) => {
              const label = riskLabel(it.risk?.adjusted_score);
              return (
                <Link key={it.listing?._id} href={`/listings/${it.listing?._id}`}>
                  <div className="flex items-center gap-3 rounded-lg border bg-card p-3">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{it.listing?.title || "Untitled listing"}</p>
                      <p className="text-xs text-muted-foreground">
                        {it.listing?.category} · {it.seller?.name || "unknown seller"} · {timeAgo(it.listing?.created_at)}
                      </p>
                    </div>
                    {it.risk_band && <StatusBadge tone={riskTone[it.risk_band]} label={`${label.label} risk`} />}
                    <StatusBadge tone={it.decision?.status === "blocked" ? "danger" : "warning"} label={it.decision?.status || "—"} />
                  </div>
                </Link>
              );
            })}
          </CardContent>
        </Card>
      </div>

      <div className="mt-6">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Recent activity</CardTitle>
            <CardDescription>Latest admin audit events</CardDescription>
          </CardHeader>
          <CardContent className="space-y-1">
            {data.recent_activity.length === 0 && <p className="text-sm text-muted-foreground">No audit events recorded yet.</p>}
            {data.recent_activity.map((a) => (
              <div key={a._id} className="flex items-start gap-3 py-1.5 text-sm">
                <ScrollText className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                <div className="min-w-0 flex-1">
                  <p className="break-words">{a.action}</p>
                  <p className="text-xs text-muted-foreground">{a.target_type ? `${a.target_type}${a.target_id ? ` ${a.target_id.slice(0, 8)}…` : ""} · ` : ""}{timeAgo(a.created_at)}{a.ip ? ` · ${a.ip}` : ""}</p>
                </div>
                <BadgeCheck className="size-4 text-muted-foreground/50" />
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}