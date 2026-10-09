"use client"

import Link from "next/link"
import * as React from "react"
import { TrendingDown, TrendingUp } from "lucide-react"
import { Area, AreaChart, CartesianGrid, XAxis } from "recharts"

import { useIsMobile } from "@/hooks/use-mobile"
import { useFetch } from "@/lib/use-fetch"
import type { DashboardData } from "@/lib/types"
import { fmtNumber, money, riskLabel, timeAgo } from "@/lib/format"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  type ChartConfig,
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
} from "@/components/ui/chart"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { StatusBadge } from "@/components/status-badge"

// ---------------------------------------------------------------------------
// SectionCards — template exact structure, wired to SafeResale kpis
// ---------------------------------------------------------------------------
function SectionCards({ kpis }: { kpis: DashboardData["kpis"] }) {
  return (
    <div className="*:data-[slot=card]:from-primary/5 *:data-[slot=card]:to-card dark:*:data-[slot=card]:bg-card *:data-[slot=card]:bg-gradient-to-t *:data-[slot=card]:shadow-xs grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <Card className="@container/card">
        <CardHeader>
          <CardDescription>Total Listings</CardDescription>
          <CardTitle className="text-2xl font-semibold tabular-nums @[250px]/card:text-3xl">
            {fmtNumber(kpis.total_listings)}
          </CardTitle>
          <CardAction>
            <Badge variant="outline">
              <TrendingUp />
              +{fmtNumber(kpis.new_today)} today
            </Badge>
          </CardAction>
        </CardHeader>
        <CardFooter className="flex-col items-start gap-1.5 text-sm">
          <div className="line-clamp-1 flex gap-2 font-medium">
            Last 7 days growth <TrendingUp className="size-4" />
          </div>
          <div className="text-muted-foreground">Last 7d: {fmtNumber(kpis.new_7d)} listings</div>
        </CardFooter>
      </Card>

      <Card className="@container/card">
        <CardHeader>
          <CardDescription>Pending Review</CardDescription>
          <CardTitle className="text-2xl font-semibold tabular-nums @[250px]/card:text-3xl">
            {fmtNumber(kpis.pending_review)}
          </CardTitle>
          <CardAction>
            <Badge variant="outline">
              {kpis.pending_review > 0 ? <TrendingDown /> : <TrendingUp />}
              {kpis.pending_review > 0 ? "Needs attention" : "All clear"}
            </Badge>
          </CardAction>
        </CardHeader>
        <CardFooter className="flex-col items-start gap-1.5 text-sm">
          <div className="line-clamp-1 flex gap-2 font-medium">
            {kpis.pending_review > 0 ? "Attention required" : "Queue clear"}{" "}
            {kpis.pending_review > 0 ? <TrendingDown className="size-4" /> : <TrendingUp className="size-4" />}
          </div>
          <div className="text-muted-foreground">Approved rate {kpis.approval_rate}%</div>
        </CardFooter>
      </Card>

      <Card className="@container/card">
        <CardHeader>
          <CardDescription>High-Risk</CardDescription>
          <CardTitle className="text-2xl font-semibold tabular-nums @[250px]/card:text-3xl">
            {fmtNumber(kpis.high_risk)}
          </CardTitle>
          <CardAction>
            <Badge variant="outline">
              <TrendingDown />
              Warning
            </Badge>
          </CardAction>
        </CardHeader>
        <CardFooter className="flex-col items-start gap-1.5 text-sm">
          <div className="line-clamp-1 flex gap-2 font-medium">
            Risk monitoring <TrendingDown className="size-4" />
          </div>
          <div className="text-muted-foreground">Avg risk {kpis.avg_risk ?? "—"}</div>
        </CardFooter>
      </Card>

      <Card className="@container/card">
        <CardHeader>
          <CardDescription>Escrow Held</CardDescription>
          <CardTitle className="text-2xl font-semibold tabular-nums @[250px]/card:text-3xl">
            {money(kpis.escrow_held)}
          </CardTitle>
          <CardAction>
            <Badge variant="outline">
              <TrendingUp />+{money(kpis.escrow_in_review)} in review
            </Badge>
          </CardAction>
        </CardHeader>
        <CardFooter className="flex-col items-start gap-1.5 text-sm">
          <div className="line-clamp-1 flex gap-2 font-medium">
            Secure funds <TrendingUp className="size-4" />
          </div>
          <div className="text-muted-foreground">
            {fmtNumber(kpis.users.total)} users · {fmtNumber(kpis.users.sellers)} sellers
          </div>
        </CardFooter>
      </Card>
    </div>
  )
}

// ---------------------------------------------------------------------------
// ChartAreaInteractive — template styling, fed by data.trend (14 days)
// ---------------------------------------------------------------------------
const chartConfig = {
  listings: {
    label: "Listings",
    color: "var(--primary)",
  },
} satisfies ChartConfig

function ChartAreaInteractive({ trend }: { trend: DashboardData["trend"] }) {
  const isMobile = useIsMobile()
  const [timeRange, setTimeRange] = React.useState("14d")

  React.useEffect(() => {
    if (isMobile) setTimeRange("7d")
  }, [isMobile])

  // trend: { date: number (unix sec), count: number }[]  -> { date: YYYY-MM-DD, listings: number }
  const chartData = React.useMemo(() => {
    return trend.map((t) => {
      const d = new Date(t.date * 1000)
      const iso = d.toISOString().slice(0, 10)
      return { date: iso, listings: t.count }
    })
  }, [trend])

  const filteredData = React.useMemo(() => {
    if (timeRange === "7d") return chartData.slice(-7)
    if (timeRange === "30d") return chartData // only 14 available, show all
    return chartData // 14d
  }, [chartData, timeRange])

  return (
    <Card className="@container/card">
      <CardHeader>
        <CardTitle>Listings Created</CardTitle>
        <CardDescription>
          <span className="hidden @[540px]/card:block">Total for the last 14 days</span>
          <span className="@[540px]/card:hidden">Last 14 days</span>
        </CardDescription>
        <CardAction>
          <Select value={timeRange} onValueChange={setTimeRange}>
            <SelectTrigger
              className="flex w-40 **:data-[slot=select-value]:block **:data-[slot=select-value]:truncate @[767px]/card:hidden sm:flex"
              aria-label="Select a value"
            >
              <SelectValue placeholder="Last 14 days" />
            </SelectTrigger>
            <SelectContent className="rounded-xl">
              <SelectItem value="14d" className="rounded-lg">
                Last 14 days
              </SelectItem>
              <SelectItem value="7d" className="rounded-lg">
                Last 7 days
              </SelectItem>
              <SelectItem value="30d" className="rounded-lg">
                Last 30 days
              </SelectItem>
            </SelectContent>
          </Select>
        </CardAction>
      </CardHeader>
      <CardContent className="px-2 pt-4 sm:px-6 sm:pt-6">
        <ChartContainer config={chartConfig} className="aspect-auto h-[250px] w-full">
          <AreaChart data={filteredData}>
            <defs>
              <linearGradient id="fillListings" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--color-listings)" stopOpacity={1.0} />
                <stop offset="95%" stopColor="var(--color-listings)" stopOpacity={0.1} />
              </linearGradient>
            </defs>
            <CartesianGrid vertical={false} />
            <XAxis
              dataKey="date"
              tickLine={false}
              axisLine={false}
              tickMargin={8}
              minTickGap={32}
              tickFormatter={(value) => {
                const date = new Date(value)
                return date.toLocaleDateString("en-US", { month: "short", day: "numeric" })
              }}
            />
            <ChartTooltip
              cursor={false}
              content={
                <ChartTooltipContent
                  labelFormatter={(value) => {
                    return new Date(value as string).toLocaleDateString("en-US", {
                      month: "short",
                      day: "numeric",
                    })
                  }}
                  indicator="dot"
                />
              }
            />
            <Area dataKey="listings" type="natural" fill="url(#fillListings)" stroke="var(--color-listings)" />
          </AreaChart>
        </ChartContainer>
      </CardContent>
    </Card>
  )
}

// ---------------------------------------------------------------------------
// DataTable — template shell (4 tabs) wired to SafeResale data
// Outline: flagged queue table, Past Performance: risk distribution bars
// Key Personnel: status distribution bars, Focus Documents: recent activity
// ---------------------------------------------------------------------------
const RISK_COLORS: Record<string, string> = { low: "#C6F135", medium: "#f59e0b", high: "#ef4444" }
const STATUS_COLORS: Record<string, string> = {
  approved: "#C6F135",
  published: "#C6F135",
  review: "#f59e0b",
  verifying: "#f59e0b",
  submitted: "#f59e0b",
  blocked: "#ef4444",
  draft: "#9ca3af",
  capturing: "#9ca3af",
  restricted: "#a78bfa",
  inspection_pending: "#0ea5e9",
}

function DataTableSection({ data }: { data: DashboardData }) {
  const riskRows = Object.entries(data.risk_distribution) as [string, number][]
  const riskTotal = riskRows.reduce((a, [, n]) => a + n, 0) || 1
  const statusRows = Object.entries(data.status_distribution).sort((a, b) => b[1] - a[1])
  const statusTotal = statusRows.reduce((a, [, n]) => a + n, 0) || 1
  const flaggedCount = data.recent_flagged.length
  const activityCount = data.recent_activity.length

  return (
    <Tabs defaultValue="outline" className="w-full flex-col justify-start gap-6">
      <div className="flex items-center justify-between px-4 lg:px-6 flex-wrap gap-3">
        <TabsList className="**:data-[slot=badge]:bg-muted-foreground/30 hidden **:data-[slot=badge]:size-5 **:data-[slot=badge]:rounded-full **:data-[slot=badge]:px-1 sm:flex">
          <TabsTrigger value="outline" className="cursor-pointer">
            Outline {flaggedCount ? <Badge variant="secondary">{flaggedCount}</Badge> : null}
          </TabsTrigger>
          <TabsTrigger value="past-performance" className="cursor-pointer">
            Past Performance <Badge variant="secondary">{riskRows.length}</Badge>
          </TabsTrigger>
          <TabsTrigger value="key-personnel" className="cursor-pointer">
            Key Personnel <Badge variant="secondary">{statusRows.length}</Badge>
          </TabsTrigger>
          <TabsTrigger value="focus-documents" className="cursor-pointer">
            Focus Documents {activityCount ? <Badge variant="secondary">{activityCount}</Badge> : null}
          </TabsTrigger>
        </TabsList>

        {/* mobile select */}
        <Select defaultValue="outline">
          <SelectTrigger className="flex w-fit sm:hidden" aria-label="Select a view">
            <SelectValue placeholder="Select a view" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="outline">Outline</SelectItem>
            <SelectItem value="past-performance">Past Performance</SelectItem>
            <SelectItem value="key-personnel">Key Personnel</SelectItem>
            <SelectItem value="focus-documents">Focus Documents</SelectItem>
          </SelectContent>
        </Select>

        <div className="flex items-center gap-2">
          <Link href="/queue">
            <Button variant="outline" size="sm">
              View queue
            </Button>
          </Link>
        </div>
      </div>

      {/* Outline — Flagged queue table */}
      <TabsContent value="outline" className="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6">
        <div className="overflow-hidden rounded-lg border">
          <Table>
            <TableHeader className="bg-muted sticky top-0 z-10">
              <TableRow>
                <TableHead>Title</TableHead>
                <TableHead>Category</TableHead>
                <TableHead>Seller</TableHead>
                <TableHead>Risk</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {flaggedCount === 0 ? (
                <TableRow>
                  <TableCell colSpan={5} className="h-24 text-center text-muted-foreground">
                    Nothing flagged — the system is quiet.
                  </TableCell>
                </TableRow>
              ) : (
                data.recent_flagged.map((it, idx) => {
                  const label = riskLabel(it.risk?.adjusted_score)
                  const tone = label.band === "high" ? "danger" : label.band === "medium" ? "warning" : "success"
                  return (
                    <TableRow key={`${it.listing?._id}-${it.decision?._id || it.risk?._id || idx}`}>
                      <TableCell>
                        <Link href={`/listings/${it.listing?._id}`} className="font-medium hover:underline">
                          {it.listing?.title || "Untitled listing"}
                        </Link>
                        <div className="text-xs text-muted-foreground">{timeAgo(it.listing?.created_at)}</div>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className="text-muted-foreground px-1.5">
                          {it.listing?.category || "—"}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-sm">{it.seller?.name || it.seller?.email || "unknown"}</TableCell>
                      <TableCell>
                        <StatusBadge tone={tone as any} label={label.label} />
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className="text-muted-foreground px-1.5 capitalize">
                          {it.decision?.status || it.listing?.status || "—"}
                        </Badge>
                      </TableCell>
                    </TableRow>
                  )
                })
              )}
            </TableBody>
          </Table>
        </div>
        <div className="flex items-center justify-between px-4">
          <div className="text-muted-foreground hidden flex-1 text-sm lg:flex">
            {flaggedCount} flagged item(s)
          </div>
        </div>
      </TabsContent>

      {/* Past Performance — Risk distribution */}
      <TabsContent value="past-performance" className="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6">
        <Card>
          <CardHeader>
            <CardTitle>Risk distribution</CardTitle>
            <CardDescription>
              {fmtNumber(data.risk_samples)} latest scores · balanced trust
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {riskRows.length === 0 ? (
              <p className="text-sm text-muted-foreground">No risk data yet.</p>
            ) : (
              riskRows.map(([k, v]) => (
                <div key={k} className="space-y-1.5">
                  <div className="flex items-center justify-between text-xs">
                    <span className="capitalize font-medium">{k}</span>
                    <span className="font-mono text-muted-foreground">
                      {v} · {Math.round((v / riskTotal) * 100)}%
                    </span>
                  </div>
                  <div className="h-2 overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full transition-all"
                      style={{ width: `${(v / riskTotal) * 100}%`, background: RISK_COLORS[k] || "#9ca3af" }}
                    />
                  </div>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </TabsContent>

      {/* Key Personnel — Status distribution */}
      <TabsContent value="key-personnel" className="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6">
        <Card>
          <CardHeader>
            <CardTitle>Status distribution</CardTitle>
            <CardDescription>
              {fmtNumber(data.kpis.total_listings)} total · live pipeline
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {statusRows.length === 0 ? (
              <p className="text-sm text-muted-foreground">No listings yet.</p>
            ) : (
              statusRows.map(([s, n]) => (
                <div key={s} className="space-y-1.5">
                  <div className="flex items-center justify-between text-xs">
                    <span className="capitalize font-medium">{s.replace(/_/g, " ")}</span>
                    <span className="font-mono text-muted-foreground">
                      {n} · {Math.round((n / statusTotal) * 100)}%
                    </span>
                  </div>
                  <div className="h-2 overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full transition-all"
                      style={{ width: `${(n / statusTotal) * 100}%`, background: STATUS_COLORS[s] || "#9ca3af" }}
                    />
                  </div>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </TabsContent>

      {/* Focus Documents — Recent activity */}
      <TabsContent value="focus-documents" className="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6">
        <Card>
          <CardHeader>
            <CardTitle>Recent activity</CardTitle>
            <CardDescription>Latest admin audit events</CardDescription>
          </CardHeader>
          <CardContent>
            {activityCount === 0 ? (
              <p className="text-sm text-muted-foreground">No audit events yet.</p>
            ) : (
              <div className="space-y-1">
                {data.recent_activity.map((a) => (
                  <div key={a._id} className="flex items-start gap-3 rounded-xl p-2.5 hover:bg-muted/50 transition-colors">
                    <span className="mt-1.5 size-2 rounded-full bg-primary shrink-0" />
                    <div className="min-w-0 flex-1">
                      <p className="text-sm leading-none">{a.action}</p>
                      <p className="text-xs text-muted-foreground mt-1">
                        {a.target_type ? `${a.target_type} ${a.target_id?.slice(0, 8)}… · ` : ""}
                        {timeAgo(a.created_at)}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </TabsContent>
    </Tabs>
  )
}

// ---------------------------------------------------------------------------
// Page — template structure: title + SectionCards + Chart + DataTable
// ---------------------------------------------------------------------------
export default function DashboardPage() {
  const { data, loading, error, reload } = useFetch<DashboardData>("/admin/dashboard")

  if (error) {
    return (
      <>
        <div className="px-4 lg:px-6">
          <div className="flex flex-col gap-2">
            <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
            <p className="text-muted-foreground">Welcome to your admin dashboard</p>
          </div>
        </div>
        <div className="@container/main px-4 lg:px-6">
          <Card className="p-8 text-center">
            <CardContent className="space-y-3">
              <p className="text-sm text-muted-foreground">{error.message}</p>
              <Button onClick={reload} variant="outline">
                Retry
              </Button>
            </CardContent>
          </Card>
        </div>
      </>
    )
  }

  if (loading || !data) {
    return (
      <>
        <div className="px-4 lg:px-6">
          <div className="flex flex-col gap-2">
            <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
            <p className="text-muted-foreground">Welcome to your admin dashboard</p>
          </div>
        </div>
        <div className="@container/main px-4 lg:px-6 space-y-6">
          <div className="*:data-[slot=card]:from-primary/5 *:data-[slot=card]:to-card dark:*:data-[slot=card]:bg-card *:data-[slot=card]:bg-gradient-to-t *:data-[slot=card]:shadow-xs grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-32 rounded-xl" />
            ))}
          </div>
          <Skeleton className="h-[340px] rounded-xl" />
        </div>
        <div className="@container/main px-4 lg:px-6">
          <Skeleton className="h-[320px] rounded-xl" />
        </div>
      </>
    )
  }

  return (
    <>
      {/* Page Title and Description */}
      <div className="px-4 lg:px-6">
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
          <p className="text-muted-foreground">Welcome to your admin dashboard</p>
        </div>
      </div>

      <div className="@container/main px-4 lg:px-6 space-y-6">
        <SectionCards kpis={data.kpis} />
        <ChartAreaInteractive trend={data.trend} />
      </div>
      <div className="@container/main">
        <DataTableSection data={data} />
      </div>
    </>
  )
}
