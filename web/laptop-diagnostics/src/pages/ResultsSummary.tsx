import { useEffect, useState } from 'react';
import { Link } from 'wouter';
import { motion } from 'framer-motion';
import { ArrowLeft, RefreshCw, CheckCircle2, ShieldCheck, Laptop } from 'lucide-react';
import { useTestContext, type TestId, type TestStatus } from '@/context/TestContext';
import { usePairing, getApiBase } from '@/context/PairingContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import confetti from 'canvas-confetti';

const testMeta: Record<string, { num: string; label: string; title: string }> = {
  keyboard:   { num: 'T-01', label: 'KEYBOARD',  title: 'Keyboard' },
  mouse:      { num: 'T-02', label: 'MOUSE',     title: 'Mouse & Trackpad' },
  camera:     { num: 'T-03', label: 'CAMERA',    title: 'Camera' },
  microphone: { num: 'T-04', label: 'MIC',       title: 'Microphone' },
  speaker:    { num: 'T-05', label: 'SPEAKER',   title: 'Speaker' },
  display:    { num: 'T-06', label: 'DISPLAY',   title: 'Display' },
  battery:    { num: 'T-07', label: 'BATTERY',   title: 'Battery' },
  network:    { num: 'T-08', label: 'NETWORK',   title: 'Network' },
  sensors:    { num: 'T-09', label: 'SENSORS',   title: 'Sensors' },
};

const contractIdMap: Record<string, string> = {
  mouse: 'trackpad',
  network: 'wifi',
};

// Laptop contract expects 10 ids; browser cannot probe bluetooth/ports, so
// report them as unsupported (not penalized). Sensors have no laptop id and
// are omitted (backend ignores unknown ids).

const STATUS_CONFIG = {
  working:     { label: 'PASS',     color: 'var(--color-status-pass)', ledColor: 'var(--color-status-pass)' },
  issue:       { label: 'FAIL',     color: 'var(--color-status-fail)', ledColor: 'var(--color-status-fail)' },
  unsupported: { label: 'N/A',      color: 'var(--color-status-idle)', ledColor: 'var(--color-status-idle)' },
  untested:    { label: 'UNTESTED', color: 'var(--color-status-idle)', ledColor: 'var(--color-status-idle)' },
} as const;

function syncStatus(status: TestStatus): string {
  if (status === 'working') return 'passed';
  if (status === 'issue') return 'failed';
  if (status === 'unsupported') return 'unsupported';
  return 'skipped';
}

export function ResultsSummary() {
  const { results, resetAll } = useTestContext();
  const { token: pairToken, sessionId, listingId: pairListingId, isPaired, submitResults } = usePairing();

  const [listingId, setListingId] = useState(pairListingId || localStorage.getItem('saferesale-listing-id') || '');
  const [token, setToken] = useState(pairToken || '');
  const [syncResult, setSyncResult] = useState<{ score: number } | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [syncError, setSyncError] = useState<string | null>(null);

  useEffect(() => {
    if (pairListingId) setListingId(pairListingId);
    if (pairToken) setToken(pairToken);
  }, [pairListingId, pairToken]);

  const total = Object.keys(results).length;
  const passed = Object.values(results).filter(v => v === 'working').length;
  const failed = Object.values(results).filter(v => v === 'issue').length;
  const unsupported = Object.values(results).filter(v => v === 'unsupported').length;
  const untested = Object.values(results).filter(v => v === 'untested').length;
  const tested = total - untested;
  const score = tested > 0 ? Math.round((passed / (tested - unsupported || tested)) * 100) : 0;

  useEffect(() => {
    if (passed > 0 && failed === 0 && untested === 0) {
      const end = Date.now() + 3000;
      const frame = () => {
        confetti({ particleCount: 4, angle: 60,  spread: 55, origin: { x: 0 }, colors: ['#0F8B8D', '#2FA84F'] });
        confetti({ particleCount: 4, angle: 120, spread: 55, origin: { x: 1 }, colors: ['#0F8B8D', '#2FA84F'] });
        if (Date.now() < end) requestAnimationFrame(frame);
      };
      frame();
    }
  }, [passed, failed, untested]);

  const radius = 58;
  const circ = 2 * Math.PI * radius;
  const dashOffset = circ - (score / 100) * circ;

  const stats = [
    { key: 'PASS',      value: passed,      color: 'var(--color-status-pass)' },
    { key: 'FAIL',      value: failed,      color: 'var(--color-status-fail)' },
    { key: 'N/A',       value: unsupported, color: 'var(--color-status-idle)' },
    { key: 'UNTESTED',  value: untested,    color: 'var(--color-status-idle)' },
  ];

  const prepareTestsPayload = () => {
    const now = new Date().toISOString();
    const tests: { id: string; status: string; value: any; unit: any; simulated: boolean; meta: any }[] = [];
    for (const [id, status] of Object.entries(results)) {
      if (id === 'sensors') continue;
      const mapped = contractIdMap[id] ?? id;
      let outStatus = syncStatus(status as TestStatus);
      if ((mapped === 'camera' || mapped === 'microphone') && status === 'issue') {
        outStatus = 'permission_required';
      }
      const value = status === 'working' ? 'verified' : status === 'issue' ? 'failed' : status === 'unsupported' ? 'unsupported' : null;
      tests.push({ id: mapped, status: outStatus, value, unit: null, simulated: false, meta: { measured_at: now } });
    }
    // bluetooth/ports cannot be probed from a browser tab -> unsupported (not penalized)
    for (const extra of ['bluetooth', 'ports']) {
      if (!tests.some((t) => t.id === extra)) {
        tests.push({ id: extra, status: 'unsupported', value: 'unsupported', unit: null, simulated: false, meta: { reason: 'not testable from browser' } });
      }
    }
    return tests;
  };

  const syncViaPairing = async () => {
    setSyncing(true);
    setSyncError(null);
    setSyncResult(null);
    try {
      const tests = prepareTestsPayload();
      const res = await submitResults(tests);
      setSyncResult({ score: res.score });
      confetti({ particleCount: 50, spread: 70, origin: { y: 0.6 } });
    } catch (err: any) {
      setSyncError(err?.message ?? 'Sync failed');
    } finally {
      setSyncing(false);
    }
  };

  const syncLegacy = async () => {
    if (!listingId) { setSyncError('Enter listing ID first (from seller draft)'); return; }
    const apiBase = getApiBase();
    setSyncing(true);
    setSyncError(null);
    setSyncResult(null);
    try {
      const tests = prepareTestsPayload();
      const body = {
        device: { model: navigator.userAgent.slice(0, 60), os_version: navigator.platform, sdk: 0 },
        category: 'laptop',
        skipped: tests.length === 0,
        tests,
      };
      const res = await fetch(`${apiBase}/listings/${listingId}/run-diagnostics`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify(body),
      });
      if (!res.ok) { setSyncError(`Server ${res.status}: ${res.statusText}`); return; }
      const j = await res.json();
      const s = Math.round(j.diagnostic_score ?? j.scored?.score ?? 0);
      setSyncResult({ score: s });
      localStorage.setItem('saferesale-listing-id', listingId);
      confetti({ particleCount: 50, spread: 70, origin: { y: 0.6 } });
    } catch (err: any) {
      setSyncError(err?.message ?? 'Sync failed');
    } finally {
      setSyncing(false);
    }
  };

  return (
    <>
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex flex-col gap-8 max-w-4xl mx-auto w-full"
      >
        <div className="flex items-start gap-3 pb-5 border-b border-border">
          <Button variant="ghost" size="icon" className="shrink-0 mt-0.5 h-8 w-8 text-muted-foreground hover:text-foreground" asChild>
            <Link href="/dashboard"><ArrowLeft className="w-4 h-4" /></Link>
          </Button>
          <div className="flex-1">
            <span className="font-mono text-[10px] tracking-[0.16em] text-muted-foreground uppercase block mb-0.5">
              DIAGNOSTICS REPORT
            </span>
            <h1 className="text-2xl font-bold tracking-tight" style={{ fontFamily: 'var(--font-display)' }}>
              Results Summary
            </h1>
            <p className="text-sm text-muted-foreground mt-0.5">
              Generated {new Date().toLocaleString()}
            </p>
          </div>
          <Button variant="outline" size="sm" onClick={resetAll} className="h-8 px-3 text-xs font-medium gap-1.5 shrink-0 mt-0.5">
            <RefreshCw className="w-3 h-3" /> Reset All
          </Button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-[auto_1fr] gap-6 items-center p-6 rounded-lg border border-border bg-card">
          <div className="flex flex-col items-center gap-3">
            <div className="relative flex items-center justify-center" style={{ width: 128, height: 128 }}>
              <svg width="128" height="128" viewBox="0 0 128 128" className="-rotate-90" aria-hidden="true">
                <circle cx="64" cy="64" r={radius} stroke="currentColor" strokeWidth="8" fill="transparent" className="text-border" />
                <circle cx="64" cy="64" r={radius} stroke="currentColor" strokeWidth="8" fill="transparent" strokeDasharray={circ} strokeDashoffset={dashOffset} className="text-primary transition-all duration-1000 ease-out" strokeLinecap="round" />
              </svg>
              <div className="absolute inset-0 flex flex-col items-center justify-center">
                <span className="font-mono text-3xl font-bold text-foreground leading-none">{score}</span>
                <span className="font-mono text-[9px] tracking-widest text-muted-foreground uppercase mt-0.5">SCORE</span>
              </div>
            </div>
          </div>

          <div className="flex flex-col gap-4">
            <div>
              <p className="text-sm text-muted-foreground leading-relaxed">
                {untested > 0 ? `${untested} test${untested > 1 ? 's' : ''} still pending. Run them for a complete report.` : failed > 0 ? `Issues detected in ${failed} component${failed > 1 ? 's' : ''}. Review each test for details.` : 'All supported components passed successfully.'}
              </p>
            </div>
            <div className="grid grid-cols-4 divide-x divide-border border border-border rounded-lg overflow-hidden">
              {stats.map(stat => (
                <div key={stat.key} className="flex flex-col items-center py-4 px-3 bg-background">
                  <span className="font-mono text-2xl font-bold leading-none" style={{ color: stat.value > 0 ? stat.color : 'hsl(var(--muted-foreground))' }}>{stat.value}</span>
                  <span className="font-mono text-[9px] tracking-[0.15em] text-muted-foreground uppercase mt-1.5">{stat.key}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="rounded-lg border border-border overflow-hidden">
          <div className="grid grid-cols-[auto_1fr_auto_auto] gap-4 px-5 py-3 border-b border-border bg-secondary/40">
            <span className="font-mono text-[10px] tracking-[0.16em] text-muted-foreground uppercase col-span-2">Module</span>
            <span className="font-mono text-[10px] tracking-[0.16em] text-muted-foreground uppercase hidden sm:block">Status</span>
            <span className="font-mono text-[10px] tracking-[0.16em] text-muted-foreground uppercase">Action</span>
          </div>
          <div className="divide-y divide-border">
            {Object.entries(results).map(([key, status]) => {
              const meta = testMeta[key];
              const cfg = STATUS_CONFIG[status as keyof typeof STATUS_CONFIG] ?? STATUS_CONFIG.untested;
              if (!meta) return null;
              return (
                <div key={key} className="grid grid-cols-[auto_1fr_auto_auto] gap-4 items-center px-5 py-3.5 hover:bg-secondary/30 transition-colors">
                  <div className="flex items-center gap-2 flex-shrink-0">
                    <span className="block w-1.5 h-1.5 rounded-full flex-shrink-0" style={{ background: cfg.ledColor }} aria-hidden="true" />
                    <span className="font-mono text-[9px] tracking-[0.16em] text-muted-foreground uppercase hidden sm:block select-none">{meta.num}</span>
                  </div>
                  <span className="font-semibold text-sm text-foreground" style={{ fontFamily: 'var(--font-display)' }}>{meta.title}</span>
                  <span className="font-mono text-[10px] tracking-[0.14em] uppercase font-medium hidden sm:block" style={{ color: cfg.color }}>{cfg.label}</span>
                  <Button variant="outline" size="sm" className="h-7 px-3 text-xs font-mono tracking-wider" asChild>
                    <Link href={`/test/${key}`}>REVIEW</Link>
                  </Button>
                </div>
              );
            })}
          </div>
        </div>
      </motion.div>

      {/* Sync section */}
      <div className="rounded-lg border border-border bg-card p-6 mt-6 max-w-4xl mx-auto w-full">
        {isPaired && sessionId ? (
          <div className="flex flex-col gap-4">
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <div className="flex items-center gap-2.5">
                <ShieldCheck className="w-5 h-5 text-emerald-500" />
                <div>
                  <h2 className="text-base font-bold text-foreground">Sync to SafeResale Listing</h2>
                  <p className="text-xs text-muted-foreground">
                    Connected to Listing {listingId ? ` #${listingId.slice(-6)}` : ''} via pairing token.
                  </p>
                </div>
              </div>
              <span className="font-mono text-xs bg-emerald-500/10 text-emerald-600 border border-emerald-500/30 px-2.5 py-1 rounded-full font-semibold">
                Session Active
              </span>
            </div>

            <p className="text-xs text-muted-foreground leading-relaxed">
              Click below to sync your verified laptop diagnostic report directly to your SafeResale listing.
              Your mobile app will instantly detect the test results and advance to the final score screen.
            </p>

            <div className="flex items-center gap-3 pt-2">
              <Button onClick={syncViaPairing} disabled={syncing} size="lg" className="h-11 px-6 font-semibold gap-2">
                <RefreshCw className={`h-4 w-4 ${syncing ? 'animate-spin' : ''}`} />
                {syncing ? 'Syncing to SafeResale…' : 'Sync Diagnostic Score'}
              </Button>
            </div>

            {syncError && <p className="font-mono text-xs text-destructive mt-1">{syncError}</p>}

            {syncResult && (
              <div className="mt-2 flex items-start gap-3 rounded-md border border-emerald-500/30 bg-emerald-500/10 p-4">
                <CheckCircle2 className="w-5 h-5 text-emerald-500 shrink-0 mt-0.5" />
                <div>
                  <h4 className="font-bold text-sm text-emerald-600">
                    Diagnostic Score Synced: {syncResult.score}/100
                  </h4>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    Your listing is now updated! You can return to your SafeResale mobile app to finalize your listing.
                  </p>
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            <h2 className="panel-label">Sync to SafeResale (Manual Entry)</h2>
            <div className="grid grid-cols-1 sm:grid-cols-[1fr_1fr_auto] gap-3 items-end">
              <div className="flex flex-col gap-1.5">
                <label className="font-mono text-[10px] tracking-wider text-muted-foreground uppercase">Listing ID</label>
                <Input value={listingId} onChange={(e) => setListingId(e.target.value)} placeholder="From seller draft" />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="font-mono text-[10px] tracking-wider text-muted-foreground uppercase">Access Token / Bearer</label>
                <Input type="password" value={token} onChange={(e) => setToken(e.target.value)} placeholder="Bearer token" />
              </div>
              <Button onClick={syncLegacy} disabled={syncing} className="h-10 gap-2 font-semibold">
                <RefreshCw className="h-4 w-4 shrink-0" /> {syncing ? 'Syncing…' : 'Sync Report'}
              </Button>
            </div>
            {syncError && <p className="mt-2 font-mono text-xs text-destructive">{syncError}</p>}
            {syncResult && (
              <div className="mt-2 flex items-center gap-2 rounded-md border border-emerald-500/30 bg-emerald-500/10 px-3 py-2">
                <span className="font-mono text-sm font-bold text-emerald-600">Diagnostic score: {syncResult.score}/100</span>
                <span className="text-xs text-muted-foreground">Badge updates on compute-risk</span>
              </div>
            )}
          </div>
        )}

        <p className="mt-4 font-mono text-[9px] uppercase tracking-wider text-muted-foreground">
          Streams never leave this tab. No upload of screenshots, camera, or mic data.
        </p>
      </div>
    </>
  );
}
