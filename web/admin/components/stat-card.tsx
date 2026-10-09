import { Card, CardHeader, CardTitle } from "@/components/ui/card"
import { cn } from "@/lib/utils"
import type { LucideIcon } from "lucide-react"

export function StatCard({
  label,
  value,
  sub,
  icon: Icon,
  tone = "default",
  trend,
}: {
  label: string
  value: React.ReactNode
  sub?: React.ReactNode
  icon?: LucideIcon
  tone?: "default" | "lime" | "danger" | "info" | "success" | "warning"
  trend?: string
}) {
  return (
    <Card className="group relative overflow-hidden border-0 ring-1 ring-black/[0.06] dark:ring-white/[0.08] shadow-[0_1px_2px_rgba(0,0,0,0.04),0_4px_12px_rgba(0,0,0,0.04)] hover:shadow-[0_4px_16px_rgba(0,0,0,0.08)] transition-all rounded-2xl gap-0">
      <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-black/[0.06] to-transparent dark:via-white/[0.06]" />
      <CardHeader className="gap-1 p-5 pb-3">
        <div className="flex items-start justify-between gap-3">
          <CardTitle className="min-w-0 space-y-1.5 text-left font-normal tracking-normal">
            <p className="text-[11px] font-semibold uppercase tracking-widest text-muted-foreground">{label}</p>
            <p className="text-2xl font-semibold tracking-tight tabular-nums leading-none text-card-foreground">{value}</p>
            {sub && <p className="text-xs leading-none text-muted-foreground">{sub}</p>}
            {trend && (
              <span className={cn("inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium mt-1", tone === "danger" && "bg-destructive/10 text-destructive", tone === "success" && "bg-success/10 text-success", tone === "warning" && "bg-warning/10 text-warning", tone === "info" && "bg-info/10 text-info", tone === "default" && "bg-muted text-muted-foreground")}>
                {trend}
              </span>
            )}
          </CardTitle>
          {Icon && (
            <span className={cn("flex size-10 shrink-0 items-center justify-center rounded-xl ring-1", tone === "lime" && "bg-accent text-accent-foreground ring-accent/20", tone === "success" && "bg-success/10 text-success ring-success/20", tone === "danger" && "bg-destructive/10 text-destructive ring-destructive/20", tone === "warning" && "bg-warning/10 text-warning ring-warning/20", tone === "info" && "bg-info/10 text-info ring-info/20", tone === "default" && "bg-muted text-muted-foreground ring-black/5")}>
              <Icon className="size-[18px]" />
            </span>
          )}
        </div>
      </CardHeader>
    </Card>
  )
}