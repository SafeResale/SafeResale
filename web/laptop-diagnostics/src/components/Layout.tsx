import type { ReactNode } from 'react';
import { useLocation } from 'wouter';
import { useTestContext } from '@/context/TestContext';
import { AppTopbar, type AppTopbarBreadcrumb } from '@/components/AppTopbar';
import { Progress } from '@/components/ui/progress';

const testNames: Record<string, string> = {
  keyboard: 'Keyboard',
  mouse: 'Mouse & Trackpad',
  camera: 'Camera',
  microphone: 'Microphone',
  speaker: 'Speaker',
  display: 'Display',
  battery: 'Battery',
  network: 'Network',
  sensors: 'Sensors',
};

export function Layout({ children }: { children: ReactNode }) {
  const [location] = useLocation();
  const { results } = useTestContext();

  const completedTests = Object.values(results).filter((status) => status !== 'untested').length;
  const totalTests = Object.keys(results).length;
  const progressPercent = (completedTests / totalTests) * 100;
  const isTestPage = location.startsWith('/test/');
  const contentWidth = isTestPage ? 'max-w-[90rem]' : 'max-w-5xl';
  const topbarWidth = isTestPage ? 'max-w-[90rem]' : 'max-w-6xl';
  const testSlug = isTestPage ? location.replace('/test/', '') : null;

  let breadcrumbs: AppTopbarBreadcrumb[] = [];
  if (location === '/dashboard') {
    breadcrumbs = [{ label: 'Dashboard' }];
  } else if (location === '/results') {
    breadcrumbs = [{ label: 'Dashboard', href: '/dashboard' }, { label: 'Report' }];
  } else if (testSlug) {
    breadcrumbs = [
      { label: 'Dashboard', href: '/dashboard' },
      { label: testNames[testSlug] ?? testSlug },
    ];
  }

  const progressReadout = (
    <div className="hidden w-28 flex-col items-end gap-1 md:flex">
      <Progress value={progressPercent} className="h-[2px] bg-secondary" />
      <span className="select-none font-mono text-[9px] tracking-[0.1em] text-muted-foreground">
        {completedTests}/{totalTests} TESTS
      </span>
    </div>
  );

  return (
    <div className="flex min-h-[100dvh] flex-col font-sans selection:bg-primary/15 selection:text-primary">
      <AppTopbar contentWidth={topbarWidth} breadcrumbs={breadcrumbs} actions={progressReadout} />

      <main className={`container mx-auto flex flex-1 flex-col px-6 py-8 ${contentWidth}`}>
        {children}
      </main>

      <footer className="border-t border-border py-4">
        <div className="container mx-auto flex max-w-5xl flex-wrap items-center justify-center gap-x-2 gap-y-1 px-6 text-center">
          <p className="font-mono text-[9px] uppercase tracking-[0.18em] text-muted-foreground">
            All processing is local. No data is transmitted. Permissions are requested only when needed.
          </p>
          <span aria-hidden="true" className="hidden text-[9px] text-muted-foreground/35 sm:inline">/</span>
          <span className="text-[10px] text-muted-foreground/60">SafeResale — Privacy-first hardware diagnostics</span>
        </div>
      </footer>
    </div>
  );
}
