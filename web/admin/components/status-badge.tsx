import { Chip } from "@heroui/react"

type Tone = "success" | "warning" | "danger" | "neutral" | "lime" | "outline" | "info"

const TONE_COLOR: Record<Tone, string> = {
  success: "success",
  warning: "warning",
  danger: "danger",
  info: "info",
  neutral: "default",
  lime: "accent",
  outline: "default",
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
    <Chip size="sm" variant="soft" color={TONE_COLOR[tone] as any} className={className}>
      {dot && <span className="mr-1 size-1.5 rounded-full bg-current" />}
      {label ?? "—"}
    </Chip>
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
