import { AlertCircle, RefreshCw } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"

export function PageError({ message, onRetry }: { message?: string; onRetry?: () => void }) {
  return (
    <Alert className="mt-2">
      <AlertCircle className="size-4 shrink-0" />
      <div className="flex w-full items-center gap-3">
        <div className="flex-1">
          <AlertTitle>Something went wrong</AlertTitle>
          <AlertDescription className="text-muted-foreground">{message || "Failed to load data from the API."}</AlertDescription>
        </div>
        {onRetry && (
          <Button variant="outline" size="sm" className="shrink-0" onClick={onRetry}>
            <RefreshCw className="size-3.5" /> Retry
          </Button>
        )}
      </div>
    </Alert>
  )
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
}: {
  icon?: any
  title: string
  description?: string
  action?: React.ReactNode
}) {
  return (
    <Card className="flex flex-col items-center justify-center p-12 text-center">
      <CardContent className="flex flex-col items-center [&>div]:w-full">
        {Icon && (
          <div className="mb-4 flex size-12 items-center justify-center rounded-xl bg-muted">
            <Icon className="size-6 text-muted-foreground" />
          </div>
        )}
        <p className="text-sm font-semibold">{title}</p>
        {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
        {action && <div className="mt-4">{action}</div>}
      </CardContent>
    </Card>
  )
}

export { Card as EmptyCard } from "@/components/ui/card"