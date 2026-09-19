import { cn } from "@/lib/utils"
import type * as React from "react"
import { Card } from "@heroui/react"

export function DashboardCard({
  className,
  ...props
}: React.ComponentProps<typeof Card>) {
  return (
    <Card
      className={cn("rounded-none bg-background shadow-none ring-0 h-full", className)}
      {...props}
    />
  )
}
