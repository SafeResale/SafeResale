import * as React from "react"
import * as RadixProgress from "@radix-ui/react-progress"
import { cn } from "@/lib/utils"

const Progress = React.forwardRef<
  React.ElementRef<typeof RadixProgress.Root>,
  React.ComponentPropsWithoutRef<typeof RadixProgress.Root>
>(({ className, value, ...props }, ref) => (
  <RadixProgress.Root
    ref={ref}
    className={cn(
      "relative h-2 w-full overflow-hidden rounded-full bg-secondary",
      className
    )}
    {...props}
  >
    <RadixProgress.Indicator
      className="h-full w-full flex-1 bg-primary transition-all"
      style={{ transform: `translateX(-${100 - (value || 0)}%)` }}
    />
  </RadixProgress.Root>
))
Progress.displayName = "Progress"

export { Progress }
