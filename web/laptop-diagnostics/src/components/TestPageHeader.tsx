import { ArrowLeft, Check, RotateCcw, TriangleAlert, Undo2 } from 'lucide-react';
import { Link } from 'wouter';
import { Button } from '@/components/ui/button';
import { useTestContext, type TestId } from '@/context/TestContext';
import { cn } from '@/lib/utils';

type TestPageHeaderProps = {
  testId: string;
  testKey: TestId;
  title: string;
  description: string;
  canUndoTest: boolean;
  onUndoTest: () => void;
  showActions?: boolean;
};

export function TestPageHeader({
  testId,
  testKey,
  title,
  description,
  canUndoTest,
  onUndoTest,
  showActions = true,
}: TestPageHeaderProps) {
  const { results, setResult, resetResult } = useTestContext();
  const status = results[testKey];

  return (
    <header className="relative mb-7 overflow-hidden rounded-xl border border-border/80 bg-card/75 px-5 py-5 shadow-sm backdrop-blur-sm sm:px-6">
      <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-primary/55 to-transparent" />
      <div className="flex flex-col gap-5 sm:flex-row sm:items-center">
        <div className="flex min-w-0 flex-1 items-start gap-4">
          <Button
            variant="outline"
            size="icon"
            className="mt-0.5 h-9 w-9 shrink-0 border-border/80 bg-background/70 text-muted-foreground shadow-sm hover:border-primary/30 hover:bg-primary/5 hover:text-primary"
            asChild
          >
            <Link href="/dashboard" aria-label="Back to diagnostics">
              <ArrowLeft className="h-4 w-4 shrink-0" />
            </Link>
          </Button>

          <div className="min-w-0 flex-1">
            <div className="mb-1.5 flex items-center gap-2">
              <span className="spec-item text-primary">{testId}</span>
              <span className="h-1 w-1 rounded-full bg-primary/50" />
              <span className="spec-item">Diagnostic module</span>
            </div>
            <h1
              className="text-2xl font-bold tracking-[-0.035em] text-foreground sm:text-[1.75rem]"
              style={{ fontFamily: 'var(--font-display)' }}
            >
              {title}
            </h1>
            <p className="mt-1 text-sm leading-relaxed text-muted-foreground">{description}</p>
          </div>
        </div>

        <div className="flex shrink-0 flex-col gap-2 sm:items-end">
          <div className="flex flex-wrap gap-2 sm:justify-end">
            {canUndoTest && (
              <Button
                variant="outline"
                size="sm"
                className="h-10 gap-2 border-primary/25 bg-primary/5 px-3 font-semibold text-primary hover:bg-primary/10 hover:text-primary"
                onClick={onUndoTest}
              >
                <Undo2 className="h-4 w-4 shrink-0" />
                Undo Test
              </Button>
            )}
            <Button
              variant="outline"
              size="sm"
              className="h-10 gap-2 border-border/80 bg-background/60 px-3 font-semibold text-muted-foreground hover:border-primary/25 hover:bg-primary/5 hover:text-primary"
              onClick={() => resetResult(testKey)}
              disabled={status === 'untested'}
            >
              <RotateCcw className="h-4 w-4 shrink-0" />
              Clear Result
            </Button>
            {showActions && (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  className={cn(
                    'h-10 gap-2 px-4 font-semibold shadow-sm',
                    status === 'issue'
                      ? 'border-status-warn/45 bg-status-warn/10 text-status-warn'
                      : 'border-status-warn/35 bg-background/60 text-status-warn hover:bg-status-warn/10 hover:text-status-warn',
                  )}
                  onClick={() => setResult(testKey, 'issue')}
                >
                  <TriangleAlert className="h-4 w-4 shrink-0" />
                  {status === 'issue' ? 'Issue Marked' : 'Mark Issue'}
                </Button>
                <Button
                  size="sm"
                  className="h-11 gap-2 border border-status-pass/30 bg-status-pass px-5 text-base font-bold text-white shadow-md shadow-status-pass/20 ring-2 ring-status-pass/10 hover:bg-status-pass/90"
                  onClick={() => setResult(testKey, 'working')}
                >
                  <Check className="h-5 w-5 shrink-0" />
                  {status === 'working' ? 'Working Saved' : 'Mark Working'}
                </Button>
              </>
            )}
          </div>
          <span className="font-mono text-[9px] uppercase tracking-[0.12em] text-muted-foreground">
            Test activity and saved result reset separately
          </span>
        </div>
      </div>
    </header>
  );
}
