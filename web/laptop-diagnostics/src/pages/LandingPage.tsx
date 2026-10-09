import { useEffect, useRef, useState, type MouseEvent as ReactMouseEvent, type ReactNode } from 'react';
import { Link } from 'wouter';
import { motion, useReducedMotion, useScroll, useTransform } from 'framer-motion';
import Lenis from 'lenis';
import 'lenis/dist/lenis.css';
import {
  Activity,
  Keyboard,
  MousePointer2,
  Camera,
  Mic,
  Volume2,
  Monitor,
  Battery,
  Wifi,
  Smartphone,
  ArrowRight,
  Laptop,
  ShieldCheck,
  Database,
  Zap,
  Upload,
  WifiOff,
  UserX,
  HardDrive,
  LockKeyhole,
  type LucideIcon,
} from 'lucide-react';
import { AppTopbar } from '@/components/AppTopbar';
import { CreatorCredit } from '@/components/CreatorCredit';
import { Button } from '@/components/ui/button';
import diagnosticHero from '@/assets/diagnostic-hero-light-devices-v1.png';
import diagnosticHeroNight from '@/assets/diagnostic-hero-night-v2.png';

/* ─── Scrolling EKG trace ──────────────────────────
   Two identical 1440-wide patterns side-by-side (2880 total).
   CSS scrolls left by 50% (one pattern width) then loops seamlessly.
──────────────────────────────────────────────────── */
const ONE_CYCLE =
  'M0,30 L110,30 L120,30 L128,4 L135,56 L142,30 ' +
  'L260,30 L268,30 L272,37 L276,23 L280,30 ' +
  'L440,30 L450,30 L458,6 L465,54 L472,30 ' +
  'L640,30 L646,30 L650,35 L654,25 L658,30 ' +
  'L850,30 L860,30 L868,5 L876,55 L884,30 ' +
  'L1060,30 L1066,30 L1070,36 L1074,24 L1078,30 ' +
  'L1440,30';

function shiftPath(d: string, dx: number): string {
  return d.replace(/(-?\d+\.?\d*),(-?\d+\.?\d*)/g, (_, x, y) =>
    `${parseFloat(x) + dx},${y}`
  );
}

const TRACE_D = ONE_CYCLE + ' ' + shiftPath(ONE_CYCLE, 1440).replace('M1440,30', 'L1440,30');

function ScrollingTrace() {
  const shouldReduce = useReducedMotion();

  return (
    <div className="signal-trace w-full overflow-hidden" aria-hidden="true">
      <svg
        className={shouldReduce ? 'signal-trace-track is-static' : 'signal-trace-track'}
        viewBox="0 0 2880 56"
        preserveAspectRatio="none"
      >
        <path
          d={TRACE_D}
          fill="none"
          stroke="currentColor"
          strokeWidth="1.35"
          className="signal-trace-base text-primary"
          vectorEffect="non-scaling-stroke"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d={TRACE_D}
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          pathLength="100"
          className="signal-trace-highlight text-primary"
          vectorEffect="non-scaling-stroke"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    </div>
  );
}

function HeroBackdrop() {
  const shouldReduce = useReducedMotion();

  return (
    <div className="hero-backdrop" aria-hidden="true">
      <motion.div
        className="hero-backdrop-art"
        initial={shouldReduce ? false : { opacity: 0, scale: 1.025 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 1.1, ease: EASE }}
      >
        <img
          src={diagnosticHero}
          alt=""
          className="hero-backdrop-image hero-backdrop-image--light"
        />
        <img
          src={diagnosticHeroNight}
          alt=""
          className="hero-backdrop-image hero-backdrop-image--night"
        />
      </motion.div>
      <div className="hero-portrait-devices">
        <div className="hero-portrait-crop hero-portrait-crop--laptop">
          <img src={diagnosticHero} alt="" className="hero-portrait-image hero-backdrop-image--light" />
          <img src={diagnosticHeroNight} alt="" className="hero-portrait-image hero-backdrop-image--night" />
        </div>
        <div className="hero-portrait-crop hero-portrait-crop--desktop">
          <img src={diagnosticHero} alt="" className="hero-portrait-image hero-backdrop-image--light" />
          <img src={diagnosticHeroNight} alt="" className="hero-portrait-image hero-backdrop-image--night" />
        </div>
      </div>
      <div className="hero-backdrop-wash" />
    </div>
  );
}

const EASE = [0.22, 1, 0.36, 1] as const;

function AnimatedHeadline() {
  const shouldReduce = useReducedMotion();
  const lineReveal = shouldReduce
    ? {}
    : {
        initial: { opacity: 0, y: 24, filter: 'blur(12px)' },
        animate: { opacity: 1, y: 0, filter: 'blur(0px)' },
      };

  return (
    <h1
      className="hero-headline font-bold leading-[1.06] tracking-tight mb-6 text-foreground text-balance"
      style={{ fontFamily: 'var(--font-display)' }}
    >
      <motion.span
        className="block"
        {...lineReveal}
        transition={{ delay: 0.16, duration: 0.7, ease: EASE }}
      >
        Know your device.
      </motion.span>
      <motion.span
        className="block hero-gradient-text"
        {...lineReveal}
        transition={{ delay: 0.3, duration: 0.75, ease: EASE }}
      >
        Trust your hardware.
      </motion.span>
    </h1>
  );
}

const TYPEWRITER_COPY =
  'Nine hardware tests. No accounts, no plugins, no data collection. Open the page, run the tests, trust the results.';

function DiagnosticTypewriter() {
  const shouldReduce = useReducedMotion();
  const [typedText, setTypedText] = useState('');
  const [typingPhase, setTypingPhase] = useState<'typing' | 'holding' | 'deleting'>('typing');

  useEffect(() => {
    if (shouldReduce) {
      setTypedText(TYPEWRITER_COPY);
      return;
    }

    let timeout: ReturnType<typeof setTimeout>;

    if (typingPhase === 'typing') {
      if (typedText.length < TYPEWRITER_COPY.length) {
        timeout = setTimeout(
          () => setTypedText(TYPEWRITER_COPY.slice(0, typedText.length + 1)),
          24,
        );
      } else {
        timeout = setTimeout(() => setTypingPhase('holding'), 2200);
      }
    } else if (typingPhase === 'holding') {
      timeout = setTimeout(() => setTypingPhase('deleting'), 900);
    } else if (typedText.length > 0) {
      timeout = setTimeout(() => setTypedText(typedText.slice(0, -1)), 9);
    } else {
      timeout = setTimeout(() => setTypingPhase('typing'), 550);
    }

    return () => clearTimeout(timeout);
  }, [shouldReduce, typedText, typingPhase]);

  return (
    <div
      className="diagnostic-typewriter max-w-2xl mx-auto mb-10 text-left"
      aria-label={TYPEWRITER_COPY}
    >
      <div className="flex items-center gap-2 mb-2" aria-hidden="true">
        <span className="signal-status-dot" />
        <span className="font-mono text-[9px] tracking-[0.18em] text-primary uppercase">
          Local diagnostic message
        </span>
      </div>
      <p className="font-mono text-[0.8rem] md:text-[0.88rem] text-muted-foreground leading-relaxed min-h-[4.8em] md:min-h-[3.2em]" aria-hidden="true">
        <span className="text-primary mr-2">&gt;</span>
        {typedText}
        <span className="typing-cursor" />
      </p>
    </div>
  );
}

function AnimatedTestIcon({ moduleId }: { moduleId: string }) {
  const s = {
    viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor',
    strokeWidth: 1.5, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const,
    className: 'w-4 h-4',
  };

  switch (moduleId) {
    case 'keyboard':
      return (
        <svg {...s}>
          <rect x="2" y="4" width="20" height="16" rx="2" />
          <rect className="ti-el ti-k1" x="5" y="7" width="3.5" height="3" rx="0.5" strokeWidth="1.2" />
          <rect className="ti-el ti-k2" x="10.25" y="7" width="3.5" height="3" rx="0.5" strokeWidth="1.2" />
          <rect className="ti-el ti-k3" x="15.5" y="7" width="3.5" height="3" rx="0.5" strokeWidth="1.2" />
          <rect x="7" y="13" width="10" height="2.5" rx="0.5" strokeWidth="1.2" />
        </svg>
      );
    case 'mouse':
      return (
        <svg {...s}>
          <rect x="6" y="2" width="12" height="20" rx="6" />
          <line x1="12" y1="2" x2="12" y2="10" strokeWidth="1" />
          <rect className="ti-el ti-mscroll" x="10.5" y="5" width="3" height="4" rx="1.5" strokeWidth="1" />
        </svg>
      );
    case 'camera':
      return (
        <svg {...s}>
          <path d="M23 19a2 2 0 01-2 2H3a2 2 0 01-2-2V8a2 2 0 012-2h4l2-3h6l2 3h4a2 2 0 012 2z" />
          <circle cx="12" cy="13" r="4" />
          <circle className="ti-el ti-iris" cx="12" cy="13" r="1.5" strokeWidth="1" />
        </svg>
      );
    case 'microphone':
      return (
        <svg {...s}>
          <rect x="9" y="1" width="6" height="11" rx="3" />
          <path d="M5 10a7 7 0 0014 0" />
          <line x1="12" y1="17" x2="12" y2="22" />
          <line x1="8" y1="22" x2="16" y2="22" />
          <path className="ti-el ti-snd1" d="M4 4c-2 2.5-2 6 0 8.5" strokeWidth="1.2" opacity="0" />
          <path className="ti-el ti-snd2" d="M20 4c2 2.5 2 6 0 8.5" strokeWidth="1.2" opacity="0" />
        </svg>
      );
    case 'speaker':
      return (
        <svg {...s}>
          <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
          <path className="ti-el ti-arc1" d="M15.54 8.46a5 5 0 010 7.07" />
          <path className="ti-el ti-arc2" d="M19.07 4.93a10 10 0 010 14.14" />
        </svg>
      );
    case 'display':
      return (
        <svg {...s}>
          <rect x="2" y="3" width="20" height="14" rx="2" />
          <line x1="8" y1="21" x2="16" y2="21" />
          <line x1="12" y1="17" x2="12" y2="21" />
          <line className="ti-el ti-scanln" x1="4" y1="5" x2="20" y2="5" strokeWidth="2" opacity="0" />
        </svg>
      );
    case 'battery':
      return (
        <svg {...s}>
          <rect x="2" y="6" width="18" height="12" rx="2" />
          <line x1="23" y1="10" x2="23" y2="14" strokeWidth="2" />
          <rect className="ti-el ti-bfill" x="4" y="8.5" width="3" height="7" rx="1" fill="currentColor" stroke="none" opacity="0.4" />
          <path className="ti-el ti-bolt" d="M12 8.5l-2 3.5h4l-2 3.5" strokeWidth="1.5" fill="none" opacity="0" />
        </svg>
      );
    case 'network':
      return (
        <svg {...s}>
          <circle cx="12" cy="20" r="1.5" fill="currentColor" stroke="none" />
          <path className="ti-el ti-wf1" d="M8.5 16.5a5 5 0 017 0" />
          <path className="ti-el ti-wf2" d="M5 13a9 9 0 0114 0" />
          <path className="ti-el ti-wf3" d="M1.5 9.5a13 13 0 0121 0" />
        </svg>
      );
    case 'sensors':
      return (
        <svg {...s} className="w-4 h-4 ti-el ti-phone">
          <rect x="5" y="2" width="14" height="20" rx="2" />
          <line x1="12" y1="18" x2="12.01" y2="18" strokeWidth="2" />
        </svg>
      );
    default:
      return null;
  }
}

function SectionIntro({
  eyebrow,
  title,
  description,
  id,
  icon: Icon,
}: {
  eyebrow: string;
  title: string;
  description: string;
  id: string;
  icon: LucideIcon;
}) {
  const shouldReduce = useReducedMotion();
  return (
    <motion.div
      className="section-intro max-w-2xl mx-auto text-center mb-8 md:mb-10"
      initial={shouldReduce ? undefined : { opacity: 0, y: 40, filter: 'blur(10px)' }}
      whileInView={shouldReduce ? undefined : { opacity: 1, y: 0, filter: 'blur(0px)' }}
      viewport={{ once: false, amount: 0.3 }}
      transition={{ duration: 0.75, ease: EASE }}
    >
      <div className="section-intro-badge">
        <Icon className="w-3.5 h-3.5" strokeWidth={1.8} />
        <span>{eyebrow}</span>
      </div>
      <h2
        id={id}
        className="text-3xl md:text-[2.6rem] font-bold tracking-tight leading-[1.1] mb-4"
        style={{ fontFamily: 'var(--font-display)' }}
      >
        {title}
      </h2>
      <p className="text-[0.92rem] md:text-base text-muted-foreground leading-relaxed">
        {description}
      </p>
    </motion.div>
  );
}

function ScrollChapter({
  children,
  className = '',
  labelledBy,
  label,
}: {
  children: ReactNode;
  className?: string;
  labelledBy?: string;
  label?: string;
}) {
  const chapterRef = useRef<HTMLElement>(null);
  const shouldReduce = useReducedMotion();
  const { scrollYProgress } = useScroll({
    target: chapterRef,
    offset: ['start end', 'end start'],
  });
  const opacity = useTransform(
    scrollYProgress,
    [0, 0.06, 0.94, 1],
    [0.3, 1, 1, 0.3],
  );
  const y = useTransform(
    scrollYProgress,
    [0, 0.2, 0.8, 1],
    [56, 0, 0, -40],
  );
  const scale = useTransform(
    scrollYProgress,
    [0, 0.18, 0.82, 1],
    [0.97, 1, 1, 0.97],
  );

  return (
    <section
      ref={chapterRef}
      className={`landing-chapter ${className}`}
      aria-labelledby={labelledBy}
      aria-label={label}
    >
      <motion.div
        className="w-full"
        style={shouldReduce ? undefined : { opacity, y, scale }}
      >
        {children}
      </motion.div>
    </section>
  );
}

/* ─── Spec strip ────────────────────────────────── */
const trustSignals = [
  {
    label: 'LOCAL EXECUTION',
    value: '100%',
    icon: Laptop,
    description: 'Every diagnostic runs inside this browser tab on your device.',
    transition: 'Browser gate',
  },
  {
    label: 'PERMISSIONS',
    value: 'ON REQUEST',
    icon: ShieldCheck,
    description: 'Camera and microphone access starts only after you approve the browser prompt.',
    transition: 'Local process',
  },
  {
    label: 'DATA COLLECTED',
    value: '0 BYTES',
    icon: Database,
    description: 'No hardware profile, recording, image, or diagnostic report is collected.',
    transition: 'Direct result',
  },
  {
    label: 'RESULTS',
    value: 'INSTANT',
    icon: Zap,
    description: 'Results appear as your browser reports them, with no upload or processing queue.',
    transition: null,
  },
];

/* ─── Test modules ──────────────────────────────── */
const modules = [
  { id: 'keyboard',   num: 'T-01', label: 'KEYBOARD', title: 'Keyboard',        icon: Keyboard,     desc: 'Live QWERTY visualizer tracks every key, including modifiers and the numpad.' },
  { id: 'mouse',      num: 'T-02', label: 'MOUSE',    title: 'Mouse & Trackpad', icon: MousePointer2, desc: 'Canvas trail plots movement, clicks, scroll delta, and button mapping.' },
  { id: 'camera',     num: 'T-03', label: 'CAMERA',   title: 'Camera',           icon: Camera,       desc: 'Live preview with resolution readout and multi-source device switching.' },
  { id: 'microphone', num: 'T-04', label: 'MIC',      title: 'Microphone',       icon: Mic,          desc: 'Web Audio waveform oscilloscope and peak-level meter, updated in real time.' },
  { id: 'speaker',    num: 'T-05', label: 'SPEAKER',  title: 'Speaker & Audio',  icon: Volume2,      desc: 'Verify left channel, right channel, and stereo balance with generated tones.' },
  { id: 'display',    num: 'T-06', label: 'DISPLAY',  title: 'Display & Color',  icon: Monitor,      desc: 'Full-screen color patterns and sharpness grids to reveal dead or stuck pixels.' },
  { id: 'battery',    num: 'T-07', label: 'BATTERY',  title: 'Battery',          icon: Battery,      desc: 'Live charge percentage, charging state, and estimated time remaining.' },
  { id: 'network',    num: 'T-08', label: 'NETWORK',  title: 'Network',          icon: Wifi,         desc: 'Connection type, online status, and an in-browser download speed estimate.' },
  { id: 'sensors',    num: 'T-09', label: 'SENSORS',  title: 'Sensors & Touch',  icon: Smartphone,   desc: 'Gyroscope, accelerometer axes, and a multi-touch canvas tracking every contact.' },
];

/* ─── Privacy meter ─────────────────────────────── */
const privacyItems = [
  {
    label: 'DATA TRANSMITTED',
    value: '0 KB',
    icon: Upload,
    description: 'Test results, camera frames, and microphone audio never leave this browser.',
  },
  {
    label: 'EXTERNAL CALLS',
    value: '0',
    icon: WifiOff,
    description: 'Diagnostics do not call a CheckMyDevice API or remote analysis service.',
  },
  {
    label: 'ACCOUNTS REQUIRED',
    value: 'NONE',
    icon: UserX,
    description: 'No sign-up, email address, identity, or tracking profile is required.',
  },
  {
    label: 'STORAGE',
    value: 'localStorage ONLY',
    icon: HardDrive,
    description: 'Only your test statuses are saved, locally in this browser, until you clear them.',
  },
];

/* ─── Page ──────────────────────────────────────── */
export function LandingPage() {
  const shouldReduce = useReducedMotion();
  const lenisRef = useRef<Lenis | null>(null);

  useEffect(() => {
    if (shouldReduce) {
      lenisRef.current = null;
      return;
    }

    const lenis = new Lenis({
      autoRaf: true,
      duration: 1.6,
      smoothWheel: true,
      wheelMultiplier: 0.85,
      touchMultiplier: 1.2,
    });
    lenisRef.current = lenis;

    return () => {
      if (lenisRef.current === lenis) lenisRef.current = null;
      lenis.destroy();
    };
  }, [shouldReduce]);

  const fadeUp = (delay: number) =>
    shouldReduce
      ? {}
      : {
          initial: { opacity: 0, y: 14 },
          animate: { opacity: 1, y: 0 },
          transition: { delay, duration: 0.5, ease: EASE },
        };

  const handleBrandClick = (event: ReactMouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    if (lenisRef.current) {
      lenisRef.current.scrollTo(0, { duration: 1.2 });
      return;
    }
    window.scrollTo({ top: 0, behavior: shouldReduce ? 'auto' : 'smooth' });
  };

  return (
    <div id="landing-top" className="min-h-[100dvh] flex flex-col bg-background text-foreground font-sans antialiased selection:bg-primary/15 selection:text-primary">

      {/* ── Nav ────────────────────────────────── */}
      <AppTopbar
        contentWidth="max-w-6xl"
        brandHref="#landing-top"
        onBrandClick={handleBrandClick}
        themeToggleTestId="landing-theme-toggle"
        actions={(
          <Button asChild size="sm" className="h-8 px-4 text-sm font-medium" data-testid="landing-cta-nav">
            <Link href="/dashboard">Open Diagnostics</Link>
          </Button>
        )}
      />

      <main className="flex-1">
        {/* ── Hero ─────────────────────────────── */}
        <ScrollChapter className="hero-chapter relative overflow-hidden min-h-[calc(100dvh-56px)]" label="CheckMyDevice introduction">
          <HeroBackdrop />
          <div className="hero-content-safe container mx-auto max-w-4xl px-5 sm:px-6 text-center">
            {/* Headline with word animation */}
            <AnimatedHeadline />

            <DiagnosticTypewriter />

            <motion.div {...fadeUp(0.88)} className="flex flex-col sm:flex-row items-center justify-center gap-3 mb-14 md:mb-20">
              <Button
                asChild
                size="lg"
                className="gap-2 px-7 h-11 text-sm font-semibold w-full sm:w-auto"
                data-testid="landing-cta-hero"
              >
                <Link href="/dashboard">
                  Start Diagnostics <ArrowRight className="w-4 h-4" />
                </Link>
              </Button>
              <Button
                asChild
                size="lg"
                className="gap-2 px-7 h-11 text-sm font-medium w-full sm:w-auto bg-background border-2 border-border text-foreground hover:bg-secondary hover:border-primary/30 shadow-sm"
                data-testid="landing-results-btn"
              >
                <Link href="/results">View Results</Link>
              </Button>
            </motion.div>
          </div>

          <div className="hero-trace-wrap">
            <ScrollingTrace />
          </div>
        </ScrollChapter>

        {/* ── Test modules ─────────────────────── */}
        <ScrollChapter className="py-10 md:py-14" labelledBy="modules-heading">
          <div className="container mx-auto max-w-6xl px-6">
            <SectionIntro
              eyebrow="Test modules"
              title="Nine focused checks. One clear result."
              description="From keys and clicks to camera, audio, display, power, and sensors. Each check talks directly to your browser, never a remote testing service."
              id="modules-heading"
              icon={Activity}
            />

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {modules.map((mod, i) => {
                return (
                  <motion.div
                    key={mod.id}
                    initial={shouldReduce ? {} : { opacity: 0, y: 50, filter: 'blur(6px)' }}
                    whileInView={shouldReduce ? {} : { opacity: 1, y: 0, filter: 'blur(0px)' }}
                    viewport={{ once: false, amount: 0.1 }}
                    transition={{ delay: (i % 3) * 0.12, duration: 0.6, ease: EASE }}
                  >
                    <Link href={`/test/${mod.id}`} data-testid={`landing-feature-${mod.id}`}>
                      <div
                        className="group relative p-5 rounded-lg border border-border bg-card cursor-pointer overflow-hidden flex flex-col gap-4 transition-all duration-200 hover:border-primary/50 hover:shadow-md"
                        style={{ transitionTimingFunction: 'cubic-bezier(0.22, 1, 0.36, 1)' }}
                        onMouseEnter={e => { if (!shouldReduce) e.currentTarget.style.transform = 'translateY(-4px)'; }}
                        onMouseLeave={e => { e.currentTarget.style.transform = 'translateY(0)'; }}
                      >
                        {/* Header: ID + icon */}
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <span className="block w-1.5 h-1.5 rounded-full bg-muted-foreground/30 flex-shrink-0" aria-hidden="true" />
                            <span className="font-mono text-[10px] tracking-[0.16em] text-muted-foreground select-none">
                              {mod.num} {mod.label}
                            </span>
                          </div>
                          <div className="p-2 rounded-md bg-secondary text-muted-foreground group-hover:bg-primary group-hover:text-primary-foreground transition-all duration-200">
                            <AnimatedTestIcon moduleId={mod.id} />
                          </div>
                        </div>

                        {/* Title + desc */}
                        <div>
                          <h3
                            className="font-semibold text-[0.95rem] text-foreground group-hover:text-primary transition-colors duration-200 mb-1"
                            style={{ fontFamily: 'var(--font-display)' }}
                          >
                            {mod.title}
                          </h3>
                          <p className="text-[0.8rem] text-muted-foreground leading-relaxed">
                            {mod.desc}
                          </p>
                        </div>
                      </div>
                    </Link>
                  </motion.div>
                );
              })}
            </div>

            <div className="mt-8 flex justify-center">
              <Button asChild size="lg" className="gap-2 px-8 h-11 text-sm font-semibold" data-testid="landing-cta-bottom">
                <Link href="/dashboard">
                  Run all 9 tests <ArrowRight className="w-4 h-4" />
                </Link>
              </Button>
            </div>
          </div>
        </ScrollChapter>

        {/* ── Trust signals ─────────────────────── */}
        <ScrollChapter className="py-10 md:py-14 bg-card/30" labelledBy="trust-signals-heading">
          <div className="container mx-auto max-w-6xl px-6">
            <SectionIntro
              eyebrow="Local by design"
              title="One local path. Four promises."
              description="Follow a diagnostic from your device to its result: it runs here, asks before protected access, collects nothing, and reports without an upload queue."
              id="trust-signals-heading"
              icon={ShieldCheck}
            />

            <div className="trust-flow overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
              <div className="trust-flow-header flex flex-col gap-3 border-b border-border px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-7">
                <div className="flex items-center gap-2.5">
                  <span className="signal-status-dot" aria-hidden="true" />
                  <span className="font-mono text-[10px] tracking-[0.17em] text-primary uppercase">
                    Active local diagnostic path
                  </span>
                </div>
                <span className="font-mono text-[9px] tracking-[0.15em] text-muted-foreground uppercase">
                  4 guarantees / 0 uploads
                </span>
              </div>

              <ol className="trust-flow-track" aria-label="The local diagnostic process">
                {trustSignals.map((signal, index) => {
                  const Icon = signal.icon;
                  return (
                    <motion.li
                      key={signal.label}
                      className="trust-flow-step group outline-none"
                      tabIndex={0}
                      initial={shouldReduce ? {} : { opacity: 0, x: -24 }}
                      whileInView={shouldReduce ? {} : { opacity: 1, x: 0 }}
                      viewport={{ once: false, amount: 0.25 }}
                      transition={{ delay: index * 0.12, duration: 0.55, ease: EASE }}
                    >
                      <div className="trust-flow-node" aria-hidden="true">
                        <Icon className="h-5 w-5" strokeWidth={1.8} />
                        <span>{String(index + 1).padStart(2, '0')}</span>
                      </div>

                      {signal.transition && (
                        <div className="trust-flow-connector" aria-hidden="true">
                          <span>{signal.transition}</span>
                        </div>
                      )}

                      <div className="trust-flow-copy">
                        <p className="font-mono text-[10px] tracking-[0.16em] text-muted-foreground uppercase">
                          {signal.label}
                        </p>
                        <p className="trust-flow-value">{signal.value}</p>
                        <p className="text-[0.8rem] leading-relaxed text-muted-foreground">
                          {signal.description}
                        </p>
                      </div>

                      {index === trustSignals.length - 1 && (
                        <div className="trust-flow-terminal" aria-label="Local result ready">
                          <span className="trust-flow-terminal-led" aria-hidden="true" />
                          <span>
                            <strong>Local result ready</strong>
                            <small>No server handoff</small>
                          </span>
                        </div>
                      )}
                    </motion.li>
                  );
                })}
              </ol>

              <div className="trust-flow-footer flex items-start gap-3 border-t border-border px-5 py-4 sm:items-center sm:px-7">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary sm:mt-0" strokeWidth={1.8} />
                <p className="text-[0.76rem] leading-relaxed text-muted-foreground">
                  No account, remote analysis service, or upload step exists anywhere in this path.
                </p>
              </div>
            </div>
          </div>
        </ScrollChapter>

        {/* ── Privacy receipt ──────────────────── */}
        <ScrollChapter className="py-10 md:py-14 bg-card/20" labelledBy="privacy-heading">
          <div className="container mx-auto max-w-6xl px-6">
            <SectionIntro
              eyebrow="Privacy, itemized"
              title="Nothing hidden. Nothing uploaded."
              description="A readable receipt of every privacy safeguard active during your session, allowing you to verify the promise instead of simply trusting it."
              id="privacy-heading"
              icon={LockKeyhole}
            />

            <div className="privacy-receipt overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
              <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-5 px-6 py-7 md:px-8 border-b border-border">
                <div className="flex items-start gap-4">
                  <span className="grid place-items-center w-11 h-11 rounded-xl bg-primary text-primary-foreground shrink-0">
                    <LockKeyhole className="w-5 h-5" strokeWidth={1.8} />
                  </span>
                  <div>
                    <p className="font-mono text-[10px] tracking-[0.18em] text-primary uppercase mb-1">
                      Session privacy check
                    </p>
                    <h3
                      className="text-xl font-bold tracking-tight"
                      style={{ fontFamily: 'var(--font-display)' }}
                    >
                      Local session verified
                    </h3>
                  </div>
                </div>
                <div className="inline-flex items-center gap-2 self-start md:self-auto rounded-full border border-primary/20 bg-primary/5 px-3 py-1.5">
                  <span className="signal-status-dot" aria-hidden="true" />
                  <span className="font-mono text-[9px] tracking-[0.15em] text-primary uppercase">
                    4 safeguards active
                  </span>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
                {privacyItems.map((item, index) => {
                  const Icon = item.icon;
                  return (
                    <motion.article
                      key={item.label}
                      tabIndex={0}
                      className="privacy-item group relative p-6 outline-none"
                      initial={shouldReduce ? {} : { opacity: 0, y: 40 }}
                      whileInView={shouldReduce ? {} : { opacity: 1, y: 0 }}
                      whileHover={shouldReduce ? {} : { backgroundColor: 'hsl(var(--primary) / 0.045)' }}
                      whileFocus={shouldReduce ? {} : { backgroundColor: 'hsl(var(--primary) / 0.045)' }}
                      viewport={{ once: false, amount: 0.15 }}
                      transition={{ delay: index * 0.08, duration: 0.55, ease: EASE }}
                    >
                      <div className="flex items-center justify-between mb-6">
                        <Icon className="w-5 h-5 text-muted-foreground group-hover:text-primary group-focus:text-primary group-hover:-translate-y-0.5 transition-all" strokeWidth={1.6} />
                        <span className="font-mono text-[9px] tracking-[0.14em] text-status-pass uppercase">
                          Pass 0{index + 1}
                        </span>
                      </div>
                      <p className="font-mono text-[9px] tracking-[0.16em] text-muted-foreground uppercase mb-2">
                        {item.label}
                      </p>
                      <p
                        className="text-lg font-bold text-foreground mb-3 group-hover:text-primary group-focus:text-primary transition-colors"
                        style={{ fontFamily: 'var(--font-display)' }}
                      >
                        {item.value}
                      </p>
                      <p className="text-[0.76rem] leading-relaxed text-muted-foreground">
                        {item.description}
                      </p>
                    </motion.article>
                  );
                })}
              </div>
            </div>
          </div>
        </ScrollChapter>
      </main>

      {/* ── Footer ──────────────────────────────── */}
      <footer className="py-8 bg-background">
        <div className="container mx-auto max-w-6xl px-6 flex flex-col sm:flex-row items-center justify-between gap-4">
          <a href="#landing-top" onClick={handleBrandClick} className="flex items-center gap-2 transition-opacity hover:opacity-75 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/45" aria-label="Scroll to top">
            <Activity className="h-4 w-4 text-primary" strokeWidth={2} />
            <span className="font-display text-sm font-semibold tracking-tight text-foreground">CheckMyDevice</span>
          </a>
          <div className="flex flex-wrap items-center justify-center gap-x-2 gap-y-1 text-center sm:justify-end sm:text-right">
            <p className="text-xs text-muted-foreground">
              Some tests require browser permissions. Results may vary by browser and platform.
            </p>
            <span aria-hidden="true" className="hidden text-[10px] text-muted-foreground/35 sm:inline">/</span>
            <CreatorCredit />
          </div>
        </div>
      </footer>
    </div>
  );
}
