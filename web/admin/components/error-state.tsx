import { AlertCircle, RefreshCw } from "lucide-react"
import { Alert, Button, Card } from "@heroui/react"

export function PageError({ message, onRetry }: { message?: string; onRetry?: () => void }) {
  return (
    <Alert color="danger" className="mt-2">
      <div className="flex items-center gap-3">
        <AlertCircle className="size-4 shrink-0" />
        <div className="flex-1">
          <p className="text-sm font-semibold">Something went wrong</p>
          <p className="text-sm text-muted-foreground">{message || "Failed to load data from the API."}</p>
        </div>
        {onRetry && (
          <Button variant="outline" size="sm" className="shrink-0" onPress={onRetry}>
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
      {Icon && (
        <div className="mb-4 flex size-12 items-center justify-center rounded-xl bg-muted">
          <Icon className="size-6 text-muted-foreground" />
        </div>
      )}
      <p className="text-sm font-semibold">{title}</p>
      {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </Card>
  )
}

export { Card as EmptyCard } from "@heroui/react"
