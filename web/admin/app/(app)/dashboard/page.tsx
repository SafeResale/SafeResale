"use client"

import Link from "next/link"
import { useEffect, useState } from "react"
import {
  Activity, AlertTriangle, ArrowUpRight, ArrowDownRight, BadgeCheck, Flag, Mail,
  Package, ScrollText, ShieldAlert, Users, Wallet, TrendingUp, Sparkles, RefreshCw,
} from "lucide-react"
import { Area, AreaChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts"
import { useFetch } from "@/lib/use-fetch"
import type { DashboardData } from "@/lib/types"
import { fmtDate, fmtNumber, money, riskLabel, timeAgo } from "@/lib/format"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { StatusBadge } from "@/components/status-badge"

const RISK_COLORS: Record<string, string> = { low: "#C6F135", medium: "#f59e0b", high: "#ef4444" }
const STATUS_STYLE: Record<string, string> = {
  approved: "#C6F135", published: "#C6F135", review: "#f59e0b", verifying: "#f59e0b", submitted: "#f59e0b",
  blocked: "#ef4444", draft: "#9ca3af", capturing: "#9ca3af", restricted: "#a78bfa", inspection_pending: "#0ea5e9",
}

function GreetingHeader({ onRefresh }: { onRefresh: () => void }) {
  const [greeting, setGreeting] = useState("Good morning")
  useEffect(() => {
    const h = new Date().getHours()
    if (h < 12) setGreeting("Good morning")
    else if (h < 17) setGreeting("Good afternoon")
    else setGreeting("Good evening")
  }, [])
  return (
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight flex items-center gap-2">
          {greeting}, Admin
          <span className="inline-flex size-7 items-center justify-center rounded-full bg-accent text-accent-foreground shadow-sm">
            <Sparkles className="size-3.5" />
          </span>
        </h1>
        <p className="text-sm text-muted-foreground mt-1">Stay ahead with real-time trust & verification insights</p>
      </div>
      <div className="flex items-center gap-2">
        <Button variant="outline" size="sm" className="rounded-full" onClick={onRefresh}>
          <RefreshCw className="size-3.5" /> Refresh
        </Button>
        <Link href="/queue">
          <Button size="sm" className="rounded-full bg-accent text-accent-foreground hover:bg-accent/90">
            Open queue <ArrowUpRight className="size-3.5" />
          </Button>
        </Link>
      </div>
    </div>
  )
}

function KpiCard({ label, value, sub, delta, icon: Icon, tone = "default" }: { label: string; value: React.ReactNode; sub?: string; delta?: string; icon: any; tone?: "lime" | "danger" | "info" | "default" | "success" | "warning" }) {
  const toneMap: Record<string, string> = {
    lime: "bg-accent text-accent-foreground ring-accent/20",
    success: "bg-success/10 text-success ring-success/20",
    danger: "bg-destructive/10 text-destructive ring-destructive/20",
    warning: "bg-warning/10 text-warning ring-warning/20",
    info: "bg-info/10 text-info ring-info/20",
    default: "bg-muted text-muted-foreground ring-black/5",
  }
  return (
    <Card className="relative overflow-hidden rounded-2xl transition-all hover:shadow-md">
      <CardHeader className="gap-1 p-4 pb-2">
        <div className="flex items-start justify-between">
          <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">{label}</p>
          <span className={`inline-flex size-8 items-center justify-center rounded-lg text-[11px] ring-1 ${toneMap[tone]}`}>
            <Icon className="size-4" />
          </span>
        </div>
      </CardHeader>
      <CardContent className="px-4 pb-4 pt-0">
        <div className="text-2xl font-bold tracking-tight tabular-nums">{value}</div>
        {sub && <p className="text-xs text-muted-foreground mt-0.5">{sub}</p>}
        {delta && (
          <p className="mt-1.5 flex items-center gap-1 text-[11px] font-medium text-success">
            <ArrowUpRight className="size-3" /> {delta}
          </p>
        )}
      </CardContent>
    </Card>
  )
}

export default function DashboardPage() {
  const { data, loading, error, reload } = useFetch<DashboardData>("/admin/dashboard")

  if (error) {
    return (
      <div className="space-y-4">
        <GreetingHeader onRefresh={reload} />
        <Card className="p-8 text-center">
          <CardContent className="space-y-3">
            <p className="text-sm text-muted-foreground">{error.message}</p>
            <Button onClick={reload} variant="outline">Retry</Button>
          </CardContent>
        </Card>
      </div>
    )
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
    )
  }

  const k = data.kpis
  const trend = data.trend.map((t) => ({ day: fmtDate(t.date, { month: "short", day: "numeric" }), count: t.count }))
  const statusRows = Object.entries(data.status_distribution).sort((a, b) => b[1] - a[1])
  const riskRows = Object.entries(data.risk_distribution) as [string, number][]
  const totalStatuses = statusRows.reduce((a, [, n]) => a + n, 0)

  return (
    <div className="space-y-6">
      <GreetingHeader onRefresh={reload} />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard label="Total listings" value={fmtNumber(k.total_listings)} delta={`${k.new_today} today`} sub={`${k.new_7d} last 7d`} icon={Package} tone="lime" />
        <KpiCard label="Pending review" value={fmtNumber(k.pending_review)} sub="submitted · verifying · review" icon={ShieldAlert} tone="info" />
        <KpiCard label="High-risk" value={fmtNumber(k.high_risk)} sub={`${k.approval_rate}% approval rate`} icon={AlertTriangle} tone="danger" />
        <KpiCard label="Avg risk score" value={k.avg_risk > 0 ? k.avg_risk : "—"} sub={`over ${fmtNumber(data.risk_samples)} scores`} icon={Activity} tone="warning" />
        <KpiCard label="Users" value={fmtNumber(k.users.total)} sub={`${k.users.sellers} sellers · ${k.users.suspended} suspended`} icon={Users} tone="default" />
        <KpiCard label="Escrow held" value={money(k.escrow_held)} sub={`${money(k.escrow_in_review)} in review`} icon={Wallet} tone="success" />
        <KpiCard label="Reports" value={fmtNumber(k.reports_pending)} sub="pending moderation" icon={Flag} tone="danger" />
        <KpiCard label="Messages" value={fmtNumber(k.messages_new)} sub="new inbox" icon={Mail} tone="default" />
      </div>

      <div className="grid gap-4 lg:grid-cols-12">
        <Card className="lg:col-span-7 rounded-2xl overflow-hidden">
          <CardHeader className="flex-row items-center justify-between border-b px-5 py-4">
            <div>
              <CardTitle className="text-sm font-semibold flex items-center gap-2"><TrendingUp className="size-4 text-muted-foreground" /> Listings created</CardTitle>
              <CardDescription className="text-xs mt-0.5">Last 14 days · {fmtNumber(k.total_listings)} total</CardDescription>
            </div>
            <StatusBadge tone="success" label="Live" dot />
          </CardHeader>
          <CardContent className="pt-4 px-5 pb-5">
            <div className="h-[264px]">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={trend} margin={{ top: 4, right: 4, left: -18, bottom: 0 }}>
                  <defs>
                    <linearGradient id="limeFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#C6F135" stopOpacity={0.4} />
                      <stop offset="100%" stopColor="#C6F135" stopOpacity={0.02} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                  <XAxis dataKey="day" tick={{ fontSize: 11, fill: "var(--muted-foreground)" }} tickLine={false} axisLine={false} interval="preserveStartEnd" minTickGap={24} />
                  <YAxis tick={{ fontSize: 11, fill: "var(--muted-foreground)" }} tickLine={false} axisLine={false} allowDecimals={false} />
                  <Tooltip
                    contentStyle={{ borderRadius: 12, border: "1px solid var(--border)", background: "var(--card)", fontSize: 12, boxShadow: "0 4px 12px rgba(0,0,0,0.08)" }}
                    formatter={(v: any) => [v, "Listings"]}
                  />
                  <Area type="monotone" dataKey="count" stroke="#C6F135" strokeWidth={2.5} fill="url(#limeFill)" dot={false} activeDot={{ r: 4, fill: "#C6F135", strokeWidth: 2, stroke: "#fff" }} />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        <Card className="lg:col-span-5 rounded-2xl overflow-hidden">
          <CardHeader className="border-b px-5 py-4">
            <CardTitle className="text-sm font-semibold">Risk distribution</CardTitle>
            <CardDescription className="text-xs mt-0.5">{fmtNumber(data.risk_samples)} latest scores · balanced trust</CardDescription>
          </CardHeader>
          <CardContent className="pt-4 px-5 pb-5">
            <div className="h-44">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={riskRows.map(([k2, v]) => ({ name: k2, value: v }))} dataKey="value" nameKey="name" innerRadius={56} outerRadius={80} paddingAngle={3} strokeWidth={0}>
                    {riskRows.map(([k2]) => (
                      <Cell key={k2} fill={RISK_COLORS[k2] || "#9ca3af"} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={{ borderRadius: 12, border: "1px solid var(--border)", background: "var(--card)", fontSize: 12, boxShadow: "0 4px 12px rgba(0,0,0,0.08)" }} />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="mt-3 grid grid-cols-3 gap-2">
              {riskRows.map(([k2, v]) => (
                <div key={k2} className="rounded-xl bg-muted/50 p-2.5 text-center">
                  <span className="mx-auto mb-1 block size-2 rounded-full" style={{ background: RISK_COLORS[k2] }} />
                  <p className="text-xs capitalize font-medium text-foreground">{k2}</p>
                  <p className="text-lg font-bold tabular-nums text-foreground">{v}</p>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-12">
        <Card className="lg:col-span-5 rounded-2xl">
          <CardHeader className="border-b px-5 py-4">
            <CardTitle className="text-sm font-semibold">Status distribution</CardTitle>
            <CardDescription className="text-xs mt-0.5">{fmtNumber(k.total_listings)} total · live pipeline</CardDescription>
          </CardHeader>
          <CardContent className="pt-4 px-5 pb-5 space-y-3">
            {statusRows.length === 0 && <p className="text-sm text-muted-foreground">No listings yet.</p>}
            {statusRows.map(([s, n]) => (
              <div key={s} className="space-y-1.5">
                <div className="flex items-center justify-between text-xs">
                  <span className="capitalize font-medium text-foreground">{s.replace(/_/g, " ")}</span>
                  <span className="font-mono text-muted-foreground">{n} · {totalStatuses ? Math.round((n / totalStatuses) * 100) : 0}%</span>
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-muted">
                  <div className="h-full rounded-full transition-all" style={{ width: totalStatuses ? `${(n / totalStatuses) * 100}%` : "0%", background: STATUS_STYLE[s] || "#9ca3af" }} />
                </div>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card className="lg:col-span-7 rounded-2xl overflow-hidden">
          <CardHeader className="flex-row items-center justify-between border-b px-5 py-4">
            <div>
              <CardTitle className="text-sm font-semibold">Flagged queue</CardTitle>
              <CardDescription className="text-xs mt-0.5">Latest review / blocked decisions</CardDescription>
            </div>
            <Link href="/queue"><Button variant="outline" size="sm" className="rounded-full">View queue</Button></Link>
          </CardHeader>
          <CardContent className="p-0">
            {data.recent_flagged.length === 0 ? (
              <p className="p-6 text-sm text-muted-foreground">Nothing flagged — the system is quiet.</p>
            ) : (
              <div className="divide-y divide-border">
                {data.recent_flagged.map((it: any, idx: number) => {
                  const label = riskLabel(it.risk?.adjusted_score)
                  return (
                    <Link key={`${it.listing?._id}-${it.decision?._id || it.risk?._id || idx}`} href={`/listings/${it.listing?._id}`} className="flex items-center gap-3 px-5 py-3.5 hover:bg-muted/50 transition-colors">
                      <div className="size-9 rounded-xl bg-accent/15 text-accent-foreground grid place-items-center shrink-0 font-bold text-xs">{(it.listing?.title || "?")[0]}</div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-foreground">{it.listing?.title || "Untitled listing"}</p>
                        <p className="text-xs text-muted-foreground truncate">{it.listing?.category} · {it.seller?.name || "unknown"} · {timeAgo(it.listing?.created_at)}</p>
                      </div>
                      <StatusBadge tone={label.band === "high" ? "danger" : label.band === "medium" ? "warning" : "success"} label={label.label} />
                    </Link>
                  )
                })}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <Card className="rounded-2xl">
        <CardHeader className="border-b px-5 py-4">
          <CardTitle className="text-sm font-semibold flex items-center gap-2"><ScrollText className="size-4 text-muted-foreground" /> Recent activity</CardTitle>
          <CardDescription className="text-xs mt-0.5">Latest admin audit events · {fmtDate(Date.now() / 1000)}</CardDescription>
        </CardHeader>
        <CardContent className="pt-4 px-5 pb-5">
          {data.recent_activity.length === 0 ? (
            <p className="text-sm text-muted-foreground">No audit events yet.</p>
          ) : (
            <div className="space-y-1">
              {data.recent_activity.map((a: any) => (
                <div key={a._id} className="flex items-start gap-3 rounded-xl p-2.5 hover:bg-muted/50 transition-colors">
                  <span className="mt-1.5 size-2 rounded-full bg-accent shrink-0" />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm leading-none text-foreground">{a.action}</p>
                    <p className="text-xs text-muted-foreground mt-1">{a.target_type ? `${a.target_type} ${a.target_id?.slice(0, 8)}… · ` : ""}{timeAgo(a.created_at)}</p>
                  </div>
                  <BadgeCheck className="size-4 text-muted-foreground/40 shrink-0 mt-0.5" />
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}