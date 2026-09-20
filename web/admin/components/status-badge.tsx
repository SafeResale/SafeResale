import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"

type Tone = "success" | "warning" | "danger" | "neutral" | "lime" | "outline" | "info"

const TONE_CLASS: Record<Tone, string> = {
  success: "bg-success/15 text-success border-success/30 hover:bg-success/15",
  warning: "bg-warning/15 text-warning border-warning/30 hover:bg-warning/15",
  danger: "bg-destructive/15 text-destructive border-destructive/30 hover:bg-destructive/15",
  info: "bg-info/15 text-info border-info/30 hover:bg-info/15",
  neutral: "bg-muted text-muted-foreground border-border hover:bg-muted/80",
  lime: "bg-accent text-accent-foreground border-accent/40 hover:bg-accent/90",
  outline: "border-border text-foreground hover:bg-muted/50",
}

export function StatusBadge({
  tone = "neutral",
  label,
  className,
  dot,
}: {
  tone?: Tone
  label?: React.ReactNode
  className?: string
  dot?: boolean
}) {
  return (
    <Badge variant="outline" className={cn("gap-1 px-2.5 py-0.5 font-medium", TONE_CLASS[tone], className)}>
      {dot && <span className="size-1.5 rounded-full bg-current" />}
      {label ?? "—"}
    </Badge>
  )
}

export const statusTone: Record<string, Tone> = {
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
}

export const riskTone: Record<string, Tone> = {
  low: "success",
  medium: "warning",
  high: "danger",
}