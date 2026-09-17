import { AlertCircle, RefreshCw } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";

export function PageError({ message, onRetry }: { message?: string; onRetry?: () => void }) {
  return (
    <Alert variant="destructive" className="mt-2">
      <AlertCircle className="size-4" />
      <AlertTitle>Something went wrong</AlertTitle>
      <AlertDescription className="flex items-center gap-3">
        <span className="min-w-0 break-words">{message || "Failed to load data from the API."}</span>
        {onRetry && (
          <Button variant="outline" size="sm" className="shrink-0" onClick={onRetry}>
            <RefreshCw className="size-3.5" /> Retry
          </Button>
        )}
      </AlertDescription>
    </Alert>
  );
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
}: {
  icon?: any;
  title: string;
  description?: string;
  action?: React.ReactNode;
}) {
  return (
    <Card_>
      <Empty>
        <EmptyHeader>
          <EmptyMedia variant="icon" className="size-12">
            {Icon && <Icon className="size-6" />}
          </EmptyMedia>
          <EmptyTitle>{title}</EmptyTitle>
          {description && <EmptyDescription>{description}</EmptyDescription>}
        </EmptyHeader>
        {action}
      </Empty>
    </Card_>
  );
}

function Card_({ children }: { children: React.ReactNode }) {
  return <div className="rounded-lg border bg-card">{children}</div>;
}

export { Card as EmptyCard } from "@/components/ui/card";