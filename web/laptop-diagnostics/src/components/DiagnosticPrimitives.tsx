import type { ReactNode } from 'react';
import { Activity } from 'lucide-react';
import type { TestStatus } from '@/context/TestContext';
import { cn } from '@/lib/utils';

export function PanelHeading({
  label,
  description,
  trailing,
  className,
}: {
  label: string;
  description?: string;
  trailing?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('flex items-start justify-between gap-4', className)}>
      <div>
        <h2 className="panel-label">{label}</h2>
        {description && <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{description}</p>}
      </div>
      {trailing}
    </div>
  );
}

export function MetricTile({
  label,
  value,
  detail,
  accent = false,
}: {
  label: string;
  value: ReactNode;
  detail?: string;
  accent?: boolean;
}) {
  return (
    <div className="metric-tile min-w-0 p-3 sm:p-4">
      <span className="spec-item block">{label}</span>
      <div className={cn('readout-value mt-2 break-words text-base tabular-nums sm:text-xl', accent && 'text-primary')}>{value}</div>
      {detail && <p className="mt-1 text-xs text-muted-foreground">{detail}</p>}
    </div>
  );
}

export function TestStatusBadge({ status }: { status: TestStatus }) {
  const label = status === 'working' ? 'Working' : status === 'issue' ? 'Issue' : status === 'unsupported' ? 'Unsupported' : 'Untested';
  const classes = status === 'working'
    ? 'border-status-pass/25 bg-status-pass/10 text-status-pass'
    : status === 'issue'
      ? 'border-status-warn/25 bg-status-warn/10 text-status-warn'
      : 'border-border bg-secondary/60 text-status-idle';

  return (
    <span className={cn('inline-flex items-center gap-2 rounded-md border px-2.5 py-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.12em]', classes)}>
      <span className="h-1.5 w-1.5 rounded-full bg-current" />
      {label}
    </span>
  );
}

export function WaitingReadout({ title, detail }: { title: string; detail: string }) {
  return (
    <div className="relative z-10 flex flex-col items-center gap-3 text-center">
      <Activity className="h-6 w-6 text-primary/55" />
      <div>
        <p className="font-mono text-xs font-medium uppercase tracking-wide text-foreground">{title}</p>
        <p className="mt-1 text-xs text-muted-foreground">{detail}</p>
      </div>
    </div>
  );
}
