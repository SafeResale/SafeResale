import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import type { LucideIcon } from "lucide-react";

export function StatCard({
  label,
  value,
  sub,
  icon: Icon,
  tone = "default",
}: {
  label: string;
  value: React.ReactNode;
  sub?: React.ReactNode;
  icon?: LucideIcon;
  tone?: "default" | "lime" | "danger" | "info";
}) {
  return (
    <Card className="overflow-hidden">
      <CardContent className="p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0 space-y-1">
            <p className="text-sm text-muted-foreground">{label}</p>
            <p className="text-2xl font-bold tracking-tight tabular-nums">{value}</p>
            {sub && <p className="text-xs text-muted-foreground">{sub}</p>}
          </div>
          {Icon && (
            <span
              className={cn(
                "flex size-9 shrink-0 items-center justify-center rounded-lg",
                tone === "lime" && "bg-primary text-primary-foreground",
                tone === "danger" && "bg-destructive/10 text-destructive",
                tone === "info" && "bg-info/10 text-info",
                tone === "default" && "bg-muted text-muted-foreground",
              )}
            >
              <Icon className="size-4.5" />
            </span>
          )}
        </div>
      </CardContent>
    </Card>
  );
}