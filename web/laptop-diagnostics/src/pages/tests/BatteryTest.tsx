import { useCallback, useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { Battery as BatteryIcon, BatteryCharging, BatteryWarning, Clock3, PlugZap, RefreshCw, Zap } from 'lucide-react';
import { useTestContext } from '@/context/TestContext';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { TestPageHeader } from '@/components/TestPageHeader';
import { MetricTile, PanelHeading, TestStatusBadge, WaitingReadout } from '@/components/DiagnosticPrimitives';

type BatteryState = { level: number; charging: boolean; chargingTime: number; dischargingTime: number };
type BatteryManagerLike = BatteryState & {
  addEventListener: (name: string, listener: () => void) => void;
  removeEventListener: (name: string, listener: () => void) => void;
};

const BATTERY_EVENTS = ['levelchange', 'chargingchange', 'chargingtimechange', 'dischargingtimechange'] as const;
const BATTERY_POLL_INTERVAL_MS = 1000;

function sameBatteryState(left: BatteryState | null, right: BatteryState) {
  return left?.level === right.level
    && left.charging === right.charging
    && left.chargingTime === right.chargingTime
    && left.dischargingTime === right.dischargingTime;
}

export function BatteryTest() {
  const { results, setResult } = useTestContext();
  const [batteryState, setBatteryState] = useState<BatteryState | null>(null);
  const [supported, setSupported] = useState<boolean | null>(null);
  const [isMonitoring, setIsMonitoring] = useState(false);
  const [monitorError, setMonitorError] = useState<string | null>(null);
  const [lastCheckedAt, setLastCheckedAt] = useState<number | null>(null);
  const [lastChangedAt, setLastChangedAt] = useState<number | null>(null);
  const batteryManagerRef = useRef<BatteryManagerLike | null>(null);
  const lastSnapshotRef = useRef<BatteryState | null>(null);

  const readBatteryInfo = useCallback((manager: BatteryManagerLike) => {
    const nextState: BatteryState = {
      level: manager.level,
      charging: manager.charging,
      chargingTime: manager.chargingTime,
      dischargingTime: manager.dischargingTime,
    };
    const checkedAt = Date.now();

    if (!sameBatteryState(lastSnapshotRef.current, nextState)) {
      lastSnapshotRef.current = nextState;
      setBatteryState(nextState);
      setLastChangedAt(checkedAt);
    }
    setLastCheckedAt(checkedAt);
  }, []);

  useEffect(() => {
    const navigatorWithBattery = navigator as Navigator & { getBattery?: () => Promise<BatteryManagerLike> };
    if (!navigatorWithBattery.getBattery) {
      setSupported(false);
      setResult('battery', 'unsupported');
      return;
    }

    setSupported(true);
    if (!isMonitoring) return;

    let active = true;
    let manager: BatteryManagerLike | null = null;
    let pollTimer: number | null = null;

    const refresh = () => {
      if (manager && active) readBatteryInfo(manager);
    };
    const refreshWhenVisible = () => {
      if (document.visibilityState === 'visible') refresh();
    };

    setMonitorError(null);
    navigatorWithBattery.getBattery()
      .then((nextManager) => {
        if (!active) return;
        manager = nextManager;
        batteryManagerRef.current = nextManager;
        refresh();
        BATTERY_EVENTS.forEach((eventName) => nextManager.addEventListener(eventName, refresh));
        window.addEventListener('focus', refresh);
        window.addEventListener('pageshow', refresh);
        document.addEventListener('visibilitychange', refreshWhenVisible);
        pollTimer = window.setInterval(refresh, BATTERY_POLL_INTERVAL_MS);
        setResult('battery', 'working');
      })
      .catch((caught) => {
        if (!active) return;
        const message = caught instanceof Error ? caught.message : 'Battery telemetry could not be started';
        setMonitorError(message);
        setIsMonitoring(false);
        setResult('battery', 'issue');
      });

    return () => {
      active = false;
      if (pollTimer !== null) window.clearInterval(pollTimer);
      if (manager) BATTERY_EVENTS.forEach((eventName) => manager?.removeEventListener(eventName, refresh));
      window.removeEventListener('focus', refresh);
      window.removeEventListener('pageshow', refresh);
      document.removeEventListener('visibilitychange', refreshWhenVisible);
      if (batteryManagerRef.current === manager) batteryManagerRef.current = null;
    };
  }, [isMonitoring, readBatteryInfo, setResult]);

  const undoTest = () => {
    setIsMonitoring(false);
    setBatteryState(null);
    setMonitorError(null);
    setLastCheckedAt(null);
    setLastChangedAt(null);
    lastSnapshotRef.current = null;
  };

  const startMonitoring = () => {
    lastSnapshotRef.current = null;
    setIsMonitoring(true);
  };

  const refreshNow = () => {
    const manager = batteryManagerRef.current;
    if (manager) readBatteryInfo(manager);
  };

  const formatTime = (seconds: number) => {
    if (!Number.isFinite(seconds)) return 'Calculating';
    if (seconds === 0) return '0 min';
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    return hours > 0 ? `${hours}h ${minutes}m` : `${minutes} min`;
  };

  const percent = batteryState ? Math.round(batteryState.level * 100) : 0;
  const estimate = batteryState ? formatTime(batteryState.charging ? batteryState.chargingTime : batteryState.dischargingTime) : 'N/A';
  const levelColor = percent <= 20 ? 'bg-status-warn' : batteryState?.charging ? 'bg-status-pass' : 'bg-primary';
  const secondsSinceChange = lastChangedAt && lastCheckedAt ? Math.max(0, Math.floor((lastCheckedAt - lastChangedAt) / 1000)) : null;
  const freshnessDetail = lastCheckedAt ? 'Checked less than 1s ago' : 'Waiting for first reading';

  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="test-page mx-auto flex w-full max-w-[90rem] flex-col">
      <TestPageHeader
        testId="T-07"
        testKey="battery"
        title="Battery"
        description="Monitor browser-reported charge level, power source, and remaining time in real time."
        canUndoTest={isMonitoring && batteryState !== null}
        onUndoTest={undoTest}
        showActions={supported !== false}
      />

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(19rem,1fr)]">
        <Card className="instrument-panel">
          <CardContent className="p-5 sm:p-6">
            <PanelHeading label="Live power" description="Events and one-second checks keep browser telemetry synchronized." className="mb-5" />
            <div className="live-readout relative flex min-h-[340px] items-center justify-center overflow-hidden p-6">
              {supported === false ? (
                <div className="relative z-10 flex max-w-md flex-col items-center gap-5 text-center">
                  <div className="test-hero-icon border-status-idle/25 bg-status-idle/10 text-status-idle"><BatteryWarning className="h-8 w-8" /></div>
                  <div><h3 className="font-display text-lg font-bold">Battery API unavailable</h3><p className="mt-2 text-sm leading-relaxed text-muted-foreground">This browser does not expose live battery data. Other diagnostics remain available.</p></div>
                </div>
              ) : monitorError ? (
                <div className="relative z-10 flex max-w-md flex-col items-center gap-5 text-center">
                  <div className="test-hero-icon border-status-warn/25 bg-status-warn/10 text-status-warn"><BatteryWarning className="h-8 w-8" /></div>
                  <div><h3 className="font-display text-lg font-bold">Battery monitor interrupted</h3><p className="mt-2 text-sm leading-relaxed text-muted-foreground">{monitorError}</p></div>
                  <Button variant="outline" onClick={startMonitoring} className="h-11 gap-2 font-semibold"><RefreshCw className="h-4 w-4" /> Retry Monitor</Button>
                </div>
              ) : batteryState ? (
                <div className="relative z-10 w-full max-w-xl">
                  <div className="flex flex-col items-center text-center">
                    <div className={`mb-4 flex h-16 w-16 items-center justify-center rounded-full ${batteryState.charging ? 'bg-status-pass/12 text-status-pass' : 'bg-primary/10 text-primary'}`}>{batteryState.charging ? <BatteryCharging className="h-8 w-8" /> : <BatteryIcon className="h-8 w-8" />}</div>
                    <span className="spec-item">Current charge</span>
                    <div className="readout-value mt-2 text-7xl tracking-[-0.07em]">{percent}<span className="ml-1 text-2xl text-muted-foreground">%</span></div>
                    <p className="mt-2 flex items-center gap-2 text-sm font-semibold text-muted-foreground">{batteryState.charging ? <><PlugZap className="h-4 w-4 text-status-pass" /> Browser reports AC power</> : <><BatteryIcon className="h-4 w-4" /> Browser reports battery power</>}</p>
                  </div>
                  <div className="mt-8 rounded-xl border border-border/80 bg-card/70 p-3 shadow-sm">
                    <div className="relative h-12 overflow-hidden rounded-md bg-secondary"><div className={`h-full rounded-md transition-[width] duration-700 ${levelColor}`} style={{ width: `${Math.max(3, percent)}%` }} />{batteryState.charging && <Zap className="absolute left-1/2 top-1/2 h-5 w-5 -translate-x-1/2 -translate-y-1/2 text-white" />}</div>
                  </div>
                </div>
              ) : !isMonitoring ? (
                <div className="relative z-10 flex flex-col items-center gap-5 text-center">
                  <WaitingReadout title="Monitor stopped" detail="Start the monitor to read live battery telemetry" />
                  <Button onClick={startMonitoring} className="h-11 gap-2 font-semibold"><BatteryIcon className="h-4 w-4" /> Start Battery Monitor</Button>
                </div>
              ) : <WaitingReadout title="Reading battery" detail="Waiting for browser telemetry" />}
            </div>
            <div className="mt-5 grid grid-cols-3 gap-3">
              <MetricTile label="Level" value={batteryState ? `${percent}%` : 'N/A'} accent={Boolean(batteryState)} />
              <MetricTile label="Source" value={batteryState ? batteryState.charging ? 'AC reported' : 'Battery' : 'N/A'} />
              <MetricTile label="Estimate" value={estimate} />
            </div>
          </CardContent>
        </Card>

        <div className="flex flex-col gap-5">
          <Card className="instrument-panel">
            <CardContent className="p-5">
              <PanelHeading label="Power telemetry" description="Live browser readings" className="mb-5" />
              {batteryState ? (
                <div className="grid grid-cols-2 gap-3">
                  <MetricTile label="Charge" value={`${percent}%`} accent />
                  <MetricTile label="State" value={batteryState.charging ? 'Charging reported' : 'Discharging'} />
                  <MetricTile label={batteryState.charging ? 'Until full' : 'Remaining'} value={estimate} />
                  <MetricTile label="Monitor" value="Live" detail={freshnessDetail} />
                </div>
              ) : <div className="live-readout flex min-h-32 items-center justify-center p-4"><WaitingReadout title={supported === false ? 'Unsupported' : isMonitoring ? 'Loading' : 'Stopped'} detail={supported === false ? 'No battery telemetry' : isMonitoring ? 'Fetching power state' : 'Monitoring is paused'} /></div>}
            </CardContent>
          </Card>

          <Card className="instrument-panel">
            <CardContent className="p-5">
              <PanelHeading label="Power event" className="mb-4" />
              <div className="space-y-3">
                <div className="flex items-start gap-3 rounded-lg border border-primary/15 bg-primary/6 p-4"><Clock3 className="mt-0.5 h-4 w-4 shrink-0 text-primary" /><div><p className="text-xs leading-relaxed text-muted-foreground">Listening for power events and checking the current BatteryManager values every second.</p>{secondsSinceChange !== null && <p className="mt-2 font-mono text-[9px] uppercase tracking-wider text-primary">Last reported change {secondsSinceChange}s ago</p>}</div></div>
                <Button variant="outline" onClick={refreshNow} disabled={!batteryState} className="h-10 w-full gap-2 font-semibold"><RefreshCw className="h-4 w-4" /> Check Now</Button>
                <p className="text-[11px] leading-relaxed text-muted-foreground">Battery source is reported by the browser and operating system. Some browsers return “charging” when they cannot determine the actual power source.</p>
              </div>
            </CardContent>
          </Card>

          <Card className="instrument-panel"><CardContent className="flex items-center justify-between p-5"><span className="panel-label">Result</span><TestStatusBadge status={results.battery} /></CardContent></Card>
        </div>
      </div>
    </motion.div>
  );
}
