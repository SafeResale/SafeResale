import type { MouseEventHandler, ReactNode } from 'react';
import { Link } from 'wouter';
import { Activity, ChevronRight, Moon, Sun } from 'lucide-react';
import { useTheme } from 'next-themes';
import { Button } from '@/components/ui/button';

export interface AppTopbarBreadcrumb {
  label: string;
  href?: string;
}

interface AppTopbarProps {
  actions?: ReactNode;
  brandHref?: string;
  breadcrumbs?: AppTopbarBreadcrumb[];
  contentWidth?: string;
  onBrandClick?: MouseEventHandler<HTMLAnchorElement>;
  themeToggleTestId?: string;
}

export function AppTopbar({
  actions,
  brandHref = '/',
  breadcrumbs = [],
  contentWidth = 'max-w-6xl',
  onBrandClick,
  themeToggleTestId,
}: AppTopbarProps) {
  const { theme, setTheme } = useTheme();
  const brandClassName = 'group flex shrink-0 items-center gap-2 transition-opacity hover:opacity-75 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/45 focus-visible:ring-offset-4';
  const brandContents = (
    <>
      <Activity className="h-4 w-4 text-primary" strokeWidth={2} />
      <span className="hidden font-display text-sm font-semibold tracking-tight text-foreground sm:block">SafeResale</span>
    </>
  );

  return (
    <header className="sticky top-0 z-50 w-full border-b border-border bg-background/90 backdrop-blur-md">
      <div className={`container mx-auto flex h-14 items-center justify-between gap-4 px-6 ${contentWidth}`}>
        <div className="flex min-w-0 items-center gap-2">
          {onBrandClick ? (
            <a href={brandHref} onClick={onBrandClick} className={brandClassName} aria-label="Scroll to top">
              {brandContents}
            </a>
          ) : (
            <Link href={brandHref} className={brandClassName} aria-label="SafeResale home">
              {brandContents}
            </Link>
          )}

          {breadcrumbs.length > 0 && (
            <nav aria-label="Breadcrumb" className="hidden min-w-0 items-center gap-2 sm:flex">
              {breadcrumbs.map((crumb, index) => {
                const isCurrent = index === breadcrumbs.length - 1;
                return (
                  <span key={`${crumb.label}-${index}`} className="flex min-w-0 items-center gap-2">
                    <ChevronRight className="h-3.5 w-3.5 shrink-0 text-border" aria-hidden="true" />
                    {crumb.href ? (
                      <Link href={crumb.href} className="truncate text-xs text-muted-foreground transition-colors hover:text-foreground">
                        {crumb.label}
                      </Link>
                    ) : (
                      <span className="truncate text-xs font-medium text-foreground" aria-current={isCurrent ? 'page' : undefined}>
                        {crumb.label}
                      </span>
                    )}
                  </span>
                );
              })}
            </nav>
          )}
        </div>

        <div className="flex shrink-0 items-center gap-3">
          {actions}
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 text-muted-foreground hover:text-foreground"
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            aria-label="Toggle theme"
            data-testid={themeToggleTestId}
          >
            <Sun className="h-4 w-4 rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0" />
            <Moon className="absolute h-4 w-4 rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100" />
          </Button>
        </div>
      </div>
    </header>
  );
}
