import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { AlertTriangle, Check, CheckCircle2, Maximize2, MonitorUp, X } from 'lucide-react';
import { useTestContext } from '@/context/TestContext';
import { Card, CardContent } from '@/components/ui/card';
import { TestPageHeader } from '@/components/TestPageHeader';
import { PanelHeading, TestStatusBadge } from '@/components/DiagnosticPrimitives';

const testPatterns = [
  { id: 'red', name: 'Red', purpose: 'Stuck pixels', bg: '#FF0000' },
  { id: 'green', name: 'Green', purpose: 'Subpixel check', bg: '#00C040' },
  { id: 'blue', name: 'Blue', purpose: 'Subpixel check', bg: '#0050FF' },
  { id: 'white', name: 'White', purpose: 'Backlight bleed', bg: '#FFFFFF' },
  { id: 'black', name: 'Black', purpose: 'Light leakage', bg: '#000000' },
  { id: 'gradient', name: 'Gradient', purpose: 'Color banding', bg: 'linear-gradient(to right, #000000, #ffffff)', isGradient: true },
  { id: 'grid', name: 'Sharpness', purpose: 'Pixel geometry', bg: '#fff', isPattern: true, patternStyle: { backgroundImage: 'linear-gradient(#000 1px, transparent 1px), linear-gradient(90deg, #000 1px, transparent 1px)', backgroundSize: '20px 20px', backgroundColor: '#fff' } },
  { id: 'checker', name: 'Checkerboard', purpose: 'Contrast detail', bg: '#fff', isPattern: true, patternStyle: { backgroundImage: 'repeating-linear-gradient(45deg, #000 25%, transparent 25%, transparent 75%, #000 75%, #000), repeating-linear-gradient(45deg, #000 25%, #fff 25%, #fff 75%, #000 75%, #000)', backgroundPosition: '0 0, 10px 10px', backgroundSize: '20px 20px' } },
];

type Pattern = (typeof testPatterns)[number];

export function DisplayTest() {
  const { results, setResult } = useTestContext();
  const [activePatternId, setActivePatternId] = useState<string | null>(null);
  const [testedPatterns, setTestedPatterns] = useState<Set<string>>(new Set());
  const activePattern = testPatterns.find((pattern) => pattern.id === activePatternId) ?? null;
  const progress = Math.round((testedPatterns.size / testPatterns.length) * 100);

  const enterFullscreen = async (pattern: Pattern) => {
    setTestedPatterns((previous) => new Set(previous).add(pattern.id));
    setActivePatternId(pattern.id);
    try {
      if (document.documentElement.requestFullscreen) await document.documentElement.requestFullscreen();
      else if ((document.documentElement as HTMLElement & { webkitRequestFullscreen?: () => Promise<void> }).webkitRequestFullscreen) await (document.documentElement as HTMLElement & { webkitRequestFullscreen: () => Promise<void> }).webkitRequestFullscreen();
    } catch { /* the overlay still works when fullscreen is unavailable */ }
  };

  const exitFullscreen = async () => {
    setActivePatternId(null);
    try {
      if (document.fullscreenElement && document.exitFullscreen) await document.exitFullscreen();
      else if ((document as Document & { webkitFullscreenElement?: Element; webkitExitFullscreen?: () => Promise<void> }).webkitFullscreenElement) await (document as Document & { webkitExitFullscreen: () => Promise<void> }).webkitExitFullscreen();
    } catch { /* already exited */ }
  };

  const undoTest = () => {
    setTestedPatterns(new Set());
    void exitFullscreen();
  };

  useEffect(() => {
    const handleFullscreenChange = () => {
      if (!document.fullscreenElement && !(document as Document & { webkitFullscreenElement?: Element }).webkitFullscreenElement) setActivePatternId(null);
    };
    document.addEventListener('fullscreenchange', handleFullscreenChange);
    document.addEventListener('webkitfullscreenchange', handleFullscreenChange);
    return () => { document.removeEventListener('fullscreenchange', handleFullscreenChange); document.removeEventListener('webkitfullscreenchange', handleFullscreenChange); };
  }, []);

  return (
    <>
      <AnimatePresence>
        {activePattern && (
          <motion.div key="display-overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 z-[9999]" style={activePattern.isPattern ? activePattern.patternStyle : activePattern.isGradient ? { background: activePattern.bg } : { backgroundColor: activePattern.bg }} onClick={exitFullscreen}>
            <div className="absolute left-1/2 top-6 flex -translate-x-1/2 items-center gap-3 rounded-lg border border-white/20 bg-black/65 p-2 text-white shadow-lg backdrop-blur-md" onClick={(event) => event.stopPropagation()}>
              <button className="flex items-center gap-2 rounded-md px-4 py-2 text-sm font-semibold hover:bg-white/10" onClick={() => { setResult('display', 'working'); exitFullscreen(); }}><CheckCircle2 className="h-4 w-4 text-status-pass" /> Looks good</button>
              <button className="flex items-center gap-2 rounded-md px-4 py-2 text-sm font-semibold hover:bg-white/10" onClick={() => { setResult('display', 'issue'); exitFullscreen(); }}><AlertTriangle className="h-4 w-4 text-status-warn" /> Issue found</button>
            </div>
            <button aria-label="Close fullscreen pattern" className="absolute right-6 top-6 flex h-11 w-11 items-center justify-center rounded-lg border border-white/20 bg-black/65 text-white shadow-lg backdrop-blur-md" onClick={(event) => { event.stopPropagation(); exitFullscreen(); }}><X className="h-5 w-5" /></button>
            <div className="absolute bottom-7 left-1/2 -translate-x-1/2 rounded-md border border-white/20 bg-black/65 px-4 py-2 font-mono text-[10px] uppercase tracking-wider text-white backdrop-blur-md">{activePattern.name} · click anywhere to exit</div>
          </motion.div>
        )}
      </AnimatePresence>

      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="test-page mx-auto flex w-full max-w-[90rem] flex-col">
        <TestPageHeader testId="T-06" testKey="display" title="Display" description="Inspect solid colors, gradients, contrast, and pixel sharpness." canUndoTest={testedPatterns.size > 0 || activePatternId !== null} onUndoTest={undoTest} />

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(19rem,1fr)]">
          <Card className="instrument-panel">
            <CardContent className="p-5 sm:p-6">
              <PanelHeading label="Pattern deck" description="Open each card fullscreen and inspect the entire panel." className="mb-5" />
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                {testPatterns.map((pattern) => {
                  const tested = testedPatterns.has(pattern.id);
                  return (
                    <button key={pattern.id} onClick={() => enterFullscreen(pattern)} className="metric-tile group overflow-hidden p-2 text-left transition hover:border-primary/35 hover:shadow-md focus-visible:outline-none">
                      <div className="relative flex aspect-[4/3] items-center justify-center overflow-hidden rounded-md border border-border/70" style={pattern.isPattern ? pattern.patternStyle : pattern.isGradient ? { background: pattern.bg } : { backgroundColor: pattern.bg }}>
                        <div className="absolute inset-0 bg-black/0 transition group-hover:bg-black/10" />
                        <span className="relative z-10 flex h-9 w-9 scale-90 items-center justify-center rounded-md bg-black/55 text-white opacity-0 backdrop-blur-sm transition group-hover:scale-100 group-hover:opacity-100"><Maximize2 className="h-4 w-4" /></span>
                        {tested && <span className="absolute right-2 top-2 z-10 flex h-6 w-6 items-center justify-center rounded-full bg-status-pass text-white shadow-sm"><Check className="h-3.5 w-3.5" /></span>}
                      </div>
                      <div className="px-1 pb-1 pt-3"><div className="flex items-center justify-between gap-2"><span className="text-sm font-semibold">{pattern.name}</span><span className="font-mono text-[9px] text-muted-foreground">{tested ? 'VIEWED' : 'OPEN'}</span></div><p className="mt-1 text-[11px] text-muted-foreground">{pattern.purpose}</p></div>
                    </button>
                  );
                })}
              </div>
            </CardContent>
          </Card>

          <div className="flex flex-col gap-5">
            <Card className="instrument-panel">
              <CardContent className="p-5">
                <PanelHeading label="Coverage" description="Patterns inspected" className="mb-5" />
                <div className="live-readout p-5">
                  <div className="relative z-10 flex items-end justify-between"><MonitorUp className="h-6 w-6 text-primary" /><div className="readout-value text-4xl text-primary">{testedPatterns.size}<span className="text-base text-muted-foreground"> / {testPatterns.length}</span></div></div>
                  <div className="relative z-10 mt-5 h-2 overflow-hidden rounded-full bg-secondary"><div className="h-full rounded-full bg-primary transition-[width] duration-300" style={{ width: `${progress}%` }} /></div>
                  <p className="relative z-10 mt-3 font-mono text-[10px] text-muted-foreground">{progress}% COMPLETE</p>
                </div>
              </CardContent>
            </Card>

            <Card className="instrument-panel">
              <CardContent className="p-5">
                <PanelHeading label="Review guide" className="mb-4" />
                <ol className="space-y-4 text-sm text-muted-foreground">
                  <li className="flex gap-3"><span className="font-mono text-xs text-primary">01</span><span>Scan corners and edges for stuck or dead pixels.</span></li>
                  <li className="flex gap-3"><span className="font-mono text-xs text-primary">02</span><span>Check gradients for visible bands or abrupt steps.</span></li>
                  <li className="flex gap-3"><span className="font-mono text-xs text-primary">03</span><span>Confirm grid lines stay crisp and evenly spaced.</span></li>
                </ol>
              </CardContent>
            </Card>

            <Card className="instrument-panel"><CardContent className="flex items-center justify-between p-5"><span className="panel-label">Result</span><TestStatusBadge status={results.display} /></CardContent></Card>
          </div>
        </div>
      </motion.div>
    </>
  );
}