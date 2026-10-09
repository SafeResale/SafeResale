import { cn } from "@/lib/utils"
import { Card } from "@/components/ui/card"

export function DashboardCard({
  className,
  ...props
}: React.ComponentProps<typeof Card>) {
  return (
    <Card
      className={cn("rounded-none bg-background shadow-none ring-0 gap-4", className)}
      {...props}
    />
  )
}