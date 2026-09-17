import { cn } from "@/lib/utils";
import { Badge, badgeVariants } from "@/components/ui/badge";

type Tone = "success" | "warning" | "danger" | "neutral" | "lime" | "outline" | "info";

const TONE_CLASS: Record<Tone, string> = {
  success: "border-transparent bg-success/15 text-success",
  warning: "border-transparent bg-warning/15 text-warning",
  danger: "border-transparent bg-danger/15 text-danger",
  info: "border-transparent bg-info/15 text-info",
  neutral: "border-transparent bg-muted text-muted-foreground",
  lime: "border-transparent bg-primary text-primary-foreground",
  outline: "",
};

export function StatusBadge({
  tone = "neutral",
  label,
  className,
  dot,
}: {
  tone?: Tone;
  label?: React.ReactNode;
  className?: string;
  dot?: boolean;
}) {
  return (
    <Badge variant="outline" className={cn(TONE_CLASS[tone], "font-medium", className)}>
      {dot && <span className="mr-1 size-1.5 rounded-full bg-current" />}
      {label ?? "—"}
    </Badge>
  );
}

export const statusTone: Record<string, Tone> = {
  // PRD 13-marketplace-v2 §4 tokens: success=approved/live, warning=review-needed, danger=blocked, info=verified/info
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

export const riskTone: Record<string, Tone> = {
  low: "success",
  medium: "warning",
  high: "danger",
};

export { badgeVariants };