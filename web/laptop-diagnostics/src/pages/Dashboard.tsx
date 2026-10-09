import { Link } from "wouter";
import { motion } from "framer-motion";
import {
  Keyboard,
  MousePointer2,
  Camera,
  Mic,
  Volume2,
  Monitor,
  Battery,
  Wifi,
  Smartphone,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { useTestContext, TestId, TestStatus } from "@/context/TestContext";

const tests = [
  { id: 'keyboard',   num: 'T-01', label: 'KEYBOARD',  title: 'Keyboard',        description: 'Layouts, keys, modifiers',       icon: Keyboard,     path: '/test/keyboard' },
  { id: 'mouse',      num: 'T-02', label: 'MOUSE',     title: 'Mouse & Trackpad', description: 'Clicks, scroll, movement',      icon: MousePointer2, path: '/test/mouse' },
  { id: 'camera',     num: 'T-03', label: 'CAMERA',    title: 'Camera',           description: 'Video feed, resolution',        icon: Camera,       path: '/test/camera' },
  { id: 'microphone', num: 'T-04', label: 'MIC',       title: 'Microphone',       description: 'Audio input, levels',           icon: Mic,          path: '/test/microphone' },
  { id: 'speaker',    num: 'T-05', label: 'SPEAKER',   title: 'Speaker',          description: 'Stereo output, volume',         icon: Volume2,      path: '/test/speaker' },
  { id: 'display',    num: 'T-06', label: 'DISPLAY',   title: 'Display',          description: 'Colors, dead pixels, sharpness', icon: Monitor,     path: '/test/display' },
  { id: 'battery',    num: 'T-07', label: 'BATTERY',   title: 'Battery',          description: 'Health, charging status',       icon: Battery,      path: '/test/battery' },
  { id: 'network',    num: 'T-08', label: 'NETWORK',   title: 'Network',          description: 'Connection, speed',             icon: Wifi,         path: '/test/network' },
  { id: 'sensors',    num: 'T-09', label: 'SENSORS',   title: 'Sensors',          description: 'Motion, orientation, touch',    icon: Smartphone,   path: '/test/sensors' },
] as const;

/* Status LED dot */
function StatusLED({ status }: { status: TestStatus }) {
  const color = {
    working:     'var(--color-status-pass)',
    issue:       'var(--color-status-warn)',
    unsupported: 'var(--color-status-idle)',
    untested:    'var(--color-status-idle)',
  }[status];

  const pulse = status === 'working';

  return (
    <span className="relative flex h-2 w-2 flex-shrink-0" aria-hidden="true">
      {pulse && (
        <span
          className="absolute inline-flex h-full w-full rounded-full opacity-75"
          style={{ background: color, animation: 'led-pulse 2s ease-in-out infinite' }}
        />
      )}
      <span className="relative inline-flex h-2 w-2 rounded-full" style={{ background: color }} />
    </span>
  );
}

/* Status label badge */
function StatusLabel({ status }: { status: TestStatus }) {
  const map = {
    working:     { text: 'PASS',       color: 'var(--color-status-pass)' },
    issue:       { text: 'ISSUE',      color: 'var(--color-status-warn)' },
    unsupported: { text: 'N/A',        color: 'var(--color-status-idle)' },
    untested:    { text: 'UNTESTED',   color: 'var(--color-status-idle)' },
  };
  const { text, color } = map[status];
  return (
    <span
      className="font-mono text-[9px] tracking-[0.15em] font-medium select-none"
      style={{ color }}
    >
      {text}
    </span>
  );
}

const EASE = [0.22, 1, 0.36, 1] as const;

export function Dashboard() {
  const { results, resetAll } = useTestContext();

  const completedTests = Object.values(results).filter(s => s !== 'untested').length;
  const totalTests = Object.keys(results).length;
  const progressPercent = (completedTests / totalTests) * 100;

  return (
    <div className="flex flex-col gap-10">
      {/* Header */}
      <div className="flex flex-col gap-5 pb-8 border-b border-border">
        <div className="flex flex-col md:flex-row md:items-start justify-between gap-5">
          <div className="max-w-lg">
            <h1
              className="text-3xl font-bold tracking-tight mb-2 text-foreground"
              style={{ fontFamily: 'var(--font-display)' }}
            >
              Hardware Diagnostics
            </h1>
            <p className="text-muted-foreground text-[0.9rem] leading-relaxed">
              Verify every input, output, and sensor on your device. Fast, local, and precise.
            </p>
          </div>
          <div className="flex items-center gap-2.5 shrink-0">
            <Button
              variant="outline"
              size="sm"
              onClick={resetAll}
              className="h-8 px-3 text-xs font-medium"
            >
              Reset All
            </Button>
            <Button asChild size="sm" className="h-8 px-3 text-xs font-medium">
              <Link href="/results">View Report</Link>
            </Button>
          </div>
        </div>

        {/* Progress readout */}
        <div className="flex flex-col gap-1.5">
          <div className="flex justify-between items-center">
            <span className="font-mono text-[10px] tracking-[0.14em] text-muted-foreground uppercase">
              Progress
            </span>
            <span className="font-mono text-[10px] tracking-[0.14em] text-primary">
              {completedTests}/{totalTests} · {Math.round(progressPercent)}%
            </span>
          </div>
          <Progress value={progressPercent} className="h-[3px] bg-secondary" />
        </div>
      </div>

      {/* Test grid */}
      <motion.div
        className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3"
        initial="hidden"
        animate="show"
        variants={{
          hidden: {},
          show: { transition: { staggerChildren: 0.05 } },
        }}
      >
        {tests.map((test) => {
          const Icon = test.icon;
          const status = results[test.id as TestId];

          return (
            <motion.div
              key={test.id}
              variants={{
                hidden: { opacity: 0, y: 12 },
                show:   { opacity: 1, y: 0, transition: { duration: 0.4, ease: EASE } },
              }}
            >
              <Link href={test.path}>
                <div
                  className="group relative p-5 rounded-lg border border-border bg-card cursor-pointer h-full flex flex-col gap-4 transition-all duration-200 hover:border-primary/50 hover:shadow-md"
                  style={{ transitionTimingFunction: 'cubic-bezier(0.22, 1, 0.36, 1)' }}
                  onMouseEnter={(e) => (e.currentTarget.style.transform = 'translateY(-2px)')}
                  onMouseLeave={(e) => (e.currentTarget.style.transform = 'translateY(0)')}
                >
                  {/* Card header */}
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <StatusLED status={status} />
                      <span className="font-mono text-[9px] tracking-[0.16em] text-muted-foreground select-none">
                        {test.num} {test.label}
                      </span>
                    </div>
                    <StatusLabel status={status} />
                  </div>

                  {/* Card body */}
                  <div className="flex items-center gap-3">
                    <div
                      className="p-2.5 rounded-md bg-secondary text-muted-foreground group-hover:bg-primary group-hover:text-primary-foreground transition-all duration-200 flex-shrink-0"
                    >
                      <Icon className="w-5 h-5" strokeWidth={1.5} />
                    </div>
                    <div>
                      <h3
                        className="font-semibold text-sm text-foreground group-hover:text-primary transition-colors duration-200 leading-tight mb-0.5"
                        style={{ fontFamily: 'var(--font-display)' }}
                      >
                        {test.title}
                      </h3>
                      <p className="text-[0.75rem] text-muted-foreground leading-snug">
                        {test.description}
                      </p>
                    </div>
                  </div>
                </div>
              </Link>
            </motion.div>
          );
        })}
      </motion.div>
    </div>
  );
}
