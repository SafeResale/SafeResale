import { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { Activity, Download, Gauge, Globe2, Radio, RotateCcw, Timer, Wifi, WifiOff } from 'lucide-react';
import { useTestContext } from '@/context/TestContext';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { TestPageHeader } from '@/components/TestPageHeader';
import { MetricTile, PanelHeading, TestStatusBadge } from '@/components/DiagnosticPrimitives';

type ConnectionInfo = { type?: string; effectiveType?: string; downlink?: number; rtt?: number; saveData?: boolean };
type NetworkConnectionLike = ConnectionInfo & { addEventListener: (name: string, listener: () => void) => void; removeEventListener: (name: string, listener: () => void) => void };
type SpeedPhase = 'idle' | 'latency' | 'download' | 'validation' | 'complete';
type MeasurementRoute = 'direct' | 'same-origin';
type SpeedTestState = {
  status: 'idle' | 'running' | 'done' | 'error';
  phase: SpeedPhase;
  progress: number;
  mbps: number | null;
  liveMbps: number | null;
  latencyMs: number | null;
  jitterMs: number | null;
  transferredBytes: number;
  elapsedMs: number;
  samples: number;
  route: MeasurementRoute | null;
  error?: string;
};

type DownloadMeasurement = { bytes: number; durationMs: number; mbps: number };

const DIRECT_DOWNLOAD_ENDPOINT = 'https://speed.cloudflare.com/__down';
const SAME_ORIGIN_MEASUREMENT_PREFIX = '/network-measurement';
const PROXY_DOWNLOAD_SIZES = [100_000, 500_000, 1_000_000, 2_000_000, 5_000_000, 10_000_000, 20_000_000] as const;
const LATENCY_SAMPLE_COUNT = 6;
const MIN_TEST_DURATION_MS = 6000;
const MIN_DOWNLOAD_DURATION_MS = 5000;
const MAX_DOWNLOAD_BYTES = 100_000_000;
const MIN_REQUEST_BYTES = 100_000;
const MAX_REQUEST_BYTES = 20_000_000;

const initialSpeedTest: SpeedTestState = {
  status: 'idle',
  phase: 'idle',
  progress: 0,
  mbps: null,
  liveMbps: null,
  latencyMs: null,
  jitterMs: null,
  transferredBytes: 0,
  elapsedMs: 0,
  samples: 0,
  route: null,
};

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError';
}

function closestProxyDownloadSize(requestedBytes: number) {
  return PROXY_DOWNLOAD_SIZES.reduce((closest, size) => (
    Math.abs(size - requestedBytes) < Math.abs(closest - requestedBytes) ? size : closest
  ));
}

function percentile(values: number[], fraction: number) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.min(sorted.length - 1, Math.floor((sorted.length - 1) * fraction))];
}

function averageAbsoluteDifference(values: number[]) {
  if (values.length < 2) return 0;
  let total = 0;
  for (let index = 1; index < values.length; index += 1) total += Math.abs(values[index] - values[index - 1]);
  return total / (values.length - 1);
}

function wait(milliseconds: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    const timer = window.setTimeout(() => {
      signal.removeEventListener('abort', handleAbort);
      resolve();
    }, milliseconds);
    const handleAbort = () => {
      window.clearTimeout(timer);
      reject(new DOMException('The speed test was cancelled', 'AbortError'));
    };
    signal.addEventListener('abort', handleAbort, { once: true });
  });
}

export function NetworkTest() {
  const { results, setResult } = useTestContext();
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [connectionInfo, setConnectionInfo] = useState<ConnectionInfo | null>(null);
  const [speedTest, setSpeedTest] = useState<SpeedTestState>(initialSpeedTest);
  const speedTestControllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => {
      setIsOnline(false);
      if (speedTestControllerRef.current) {
        speedTestControllerRef.current.abort();
        speedTestControllerRef.current = null;
        setSpeedTest((previous) => previous.status === 'running'
          ? { ...previous, status: 'error', error: 'The device went offline during the measurement' }
          : previous);
      }
    };
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    const navigatorWithConnection = navigator as Navigator & { connection?: NetworkConnectionLike; mozConnection?: NetworkConnectionLike; webkitConnection?: NetworkConnectionLike };
    const connection = navigatorWithConnection.connection || navigatorWithConnection.mozConnection || navigatorWithConnection.webkitConnection;
    const updateConnection = () => {
      if (connection) setConnectionInfo({ type: connection.type, effectiveType: connection.effectiveType, downlink: connection.downlink, rtt: connection.rtt, saveData: connection.saveData });
    };
    if (connection) {
      updateConnection();
      connection.addEventListener('change', updateConnection);
    }
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
      connection?.removeEventListener('change', updateConnection);
    };
  }, []);

  const runSpeedTest = async () => {
    speedTestControllerRef.current?.abort();
    const controller = new AbortController();
    speedTestControllerRef.current = controller;
    const signal = controller.signal;
    const testStartedAt = performance.now();
    const latencySamples: number[] = [];
    const downloadSamples: DownloadMeasurement[] = [];
    const measurementId = crypto.randomUUID();
    let transferredBytes = 0;
    let measurementRoute: MeasurementRoute = 'direct';

    setSpeedTest({ ...initialSpeedTest, status: 'running', phase: 'latency', route: measurementRoute });

    const updateElapsed = () => performance.now() - testStartedAt;
    const measurementUrl = (requestedBytes: number, route: MeasurementRoute) => {
      const requestId = crypto.randomUUID();
      if (route === 'direct') {
        return `${DIRECT_DOWNLOAD_ENDPOINT}?bytes=${requestedBytes}&measId=${measurementId}&r=${requestId}`;
      }
      const path = requestedBytes === 0 ? 'latency' : closestProxyDownloadSize(requestedBytes);
      return `${SAME_ORIGIN_MEASUREMENT_PREFIX}/${path}?measId=${measurementId}&r=${requestId}`;
    };
    const useSameOriginRoute = () => {
      measurementRoute = 'same-origin';
      setSpeedTest((previous) => ({ ...previous, route: measurementRoute, liveMbps: null }));
    };
    const fetchMeasurement = async (requestedBytes: number) => {
      const request = async (route: MeasurementRoute) => {
        const response = await fetch(measurementUrl(requestedBytes, route), { cache: 'no-store', signal });
        if (!response.ok) throw new Error(`The measurement service returned HTTP ${response.status}`);
        if (!response.headers.get('content-type')?.includes('application/octet-stream')) {
          throw new Error('The measurement service returned an unexpected response');
        }
        return response;
      };

      const requestSameOrigin = async () => {
        try {
          return await request('same-origin');
        } catch (error) {
          if (isAbortError(error)) throw error;
          await wait(150, signal);
          return request('same-origin');
        }
      };

      if (measurementRoute === 'same-origin') return requestSameOrigin();
      try {
        return await request('direct');
      } catch (error) {
        if (isAbortError(error)) throw error;
        useSameOriginRoute();
        return requestSameOrigin();
      }
    };
    const measureLatency = async () => {
      const startedAt = performance.now();
      const response = await fetchMeasurement(0);
      await response.arrayBuffer();
      return performance.now() - startedAt;
    };

    const measureDownload = async (requestedBytes: number) => {
      const readSample = async () => {
        const startedAt = performance.now();
        const response = await fetchMeasurement(requestedBytes);
        const reader = response.body?.getReader();
        if (!reader) throw new Error('Streaming downloads are unavailable in this browser');
        let receivedBytes = 0;
        let lastUiUpdate = startedAt;

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          receivedBytes += value.byteLength;
          const now = performance.now();
          if (now - lastUiUpdate >= 100) {
            const durationMs = Math.max(1, now - startedAt);
            const liveMbps = (receivedBytes * 8) / durationMs / 1000;
            setSpeedTest((previous) => ({
              ...previous,
              liveMbps: Number(liveMbps.toFixed(1)),
              transferredBytes: transferredBytes + receivedBytes,
              elapsedMs: updateElapsed(),
            }));
            lastUiUpdate = now;
          }
        }

        if (receivedBytes === 0) throw new Error('The measurement service returned an empty download sample');
        const durationMs = Math.max(1, performance.now() - startedAt);
        return {
          bytes: receivedBytes,
          durationMs,
          mbps: (receivedBytes * 8) / durationMs / 1000,
        };
      };

      try {
        return await readSample();
      } catch (error) {
        if (isAbortError(error)) throw error;
        if (measurementRoute === 'direct') useSameOriginRoute();
        await wait(150, signal);
        return readSample();
      }
    };

    try {
      for (let index = 0; index < LATENCY_SAMPLE_COUNT; index += 1) {
        const latency = await measureLatency();
        latencySamples.push(latency);
        const medianLatency = percentile(latencySamples, 0.5);
        setSpeedTest((previous) => ({
          ...previous,
          progress: Math.round(((index + 1) / LATENCY_SAMPLE_COUNT) * 20),
          latencyMs: Number(medianLatency.toFixed(0)),
          jitterMs: Number(averageAbsoluteDifference(latencySamples).toFixed(0)),
          elapsedMs: updateElapsed(),
        }));
        if (index < LATENCY_SAMPLE_COUNT - 1) await wait(80, signal);
      }

      const downloadStartedAt = performance.now();
      let nextRequestBytes = MIN_REQUEST_BYTES;
      setSpeedTest((previous) => ({ ...previous, phase: 'download', progress: 20 }));

      while (
        (performance.now() - downloadStartedAt < MIN_DOWNLOAD_DURATION_MS || downloadSamples.length < 4)
        && transferredBytes < MAX_DOWNLOAD_BYTES
      ) {
        const remainingBudget = MAX_DOWNLOAD_BYTES - transferredBytes;
        const requestBytes = Math.min(nextRequestBytes, remainingBudget);
        const measurement = await measureDownload(requestBytes);
        downloadSamples.push(measurement);
        transferredBytes += measurement.bytes;

        const downloadElapsed = performance.now() - downloadStartedAt;
        const durationProgress = downloadElapsed / MIN_DOWNLOAD_DURATION_MS;
        const budgetProgress = transferredBytes / MAX_DOWNLOAD_BYTES;
        const progress = 20 + Math.round(Math.min(1, Math.max(durationProgress, budgetProgress)) * 68);
        const targetBytes = Math.round((measurement.mbps * 1_000_000 / 8) * 1.2 / 100_000) * 100_000;
        nextRequestBytes = Math.max(MIN_REQUEST_BYTES, Math.min(MAX_REQUEST_BYTES, targetBytes));

        const eligibleSamples = downloadSamples.filter((sample) => sample.durationMs >= 150);
        const resultPool = eligibleSamples.length > 0 ? eligibleSamples : downloadSamples;
        const currentMbps = percentile(resultPool.map((sample) => sample.mbps), 0.9);
        setSpeedTest((previous) => ({
          ...previous,
          progress,
          liveMbps: Number(measurement.mbps.toFixed(1)),
          mbps: Number(currentMbps.toFixed(1)),
          transferredBytes,
          elapsedMs: updateElapsed(),
          samples: downloadSamples.length,
        }));
      }

      setSpeedTest((previous) => ({ ...previous, phase: 'validation', progress: Math.max(90, previous.progress) }));
      while (updateElapsed() < MIN_TEST_DURATION_MS) {
        const latency = await measureLatency();
        latencySamples.push(latency);
        const remaining = Math.max(0, MIN_TEST_DURATION_MS - updateElapsed());
        const validationProgress = 90 + Math.round((1 - remaining / MIN_TEST_DURATION_MS) * 9);
        setSpeedTest((previous) => ({
          ...previous,
          progress: Math.max(previous.progress, validationProgress),
          latencyMs: Number(percentile(latencySamples, 0.5).toFixed(0)),
          jitterMs: Number(averageAbsoluteDifference(latencySamples).toFixed(0)),
          elapsedMs: updateElapsed(),
        }));
        if (updateElapsed() < MIN_TEST_DURATION_MS) await wait(180, signal);
      }

      if (downloadSamples.length === 0) throw new Error('No download samples were completed');
      const eligibleSamples = downloadSamples.filter((sample) => sample.durationMs >= 150);
      const resultPool = eligibleSamples.length > 0 ? eligibleSamples : downloadSamples;
      const finalMbps = percentile(resultPool.map((sample) => sample.mbps), 0.9);
      const finalLatency = percentile(latencySamples, 0.5);
      const finalJitter = averageAbsoluteDifference(latencySamples);

      setSpeedTest({
        status: 'done',
        phase: 'complete',
        progress: 100,
        mbps: Number(finalMbps.toFixed(1)),
        liveMbps: null,
        latencyMs: Number(finalLatency.toFixed(0)),
        jitterMs: Number(finalJitter.toFixed(0)),
        transferredBytes,
        elapsedMs: updateElapsed(),
        samples: downloadSamples.length,
        route: measurementRoute,
      });
      setResult('network', 'working');
    } catch (caught) {
      if (signal.aborted) return;
      setSpeedTest((previous) => ({
        ...previous,
        status: 'error',
        error: caught instanceof TypeError || (caught instanceof Error && caught.message === 'Failed to fetch')
          ? 'The browser blocked both measurement routes. Check privacy shields or content blockers, then retry.'
          : caught instanceof Error ? caught.message : 'The network measurement failed',
        elapsedMs: updateElapsed(),
      }));
      setResult('network', navigator.onLine ? 'unsupported' : 'issue');
    } finally {
      if (speedTestControllerRef.current === controller) speedTestControllerRef.current = null;
    }
  };

  useEffect(() => () => speedTestControllerRef.current?.abort(), []);

  const undoTest = () => {
    speedTestControllerRef.current?.abort();
    speedTestControllerRef.current = null;
    setSpeedTest(initialSpeedTest);
  };

  const effectiveType = connectionInfo?.effectiveType?.toUpperCase() || 'N/A';
  const measuredLatency = speedTest.latencyMs != null ? `${speedTest.latencyMs} ms` : null;
  const browserLatency = connectionInfo?.rtt != null ? `${connectionInfo.rtt} ms est.` : 'N/A';
  const reachability = speedTest.status === 'done' ? 'Verified' : speedTest.status === 'running' ? 'Testing' : speedTest.status === 'error' ? 'Failed' : 'Not tested';
  const measurementRouteLabel = speedTest.route === 'same-origin' ? 'Same-site relay' : speedTest.route === 'direct' ? 'Direct' : 'Not started';
  const phaseLabel = speedTest.phase === 'latency'
    ? 'Measuring idle latency'
    : speedTest.phase === 'download'
      ? 'Measuring sustained download'
      : 'Validating connection consistency';

  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="test-page mx-auto flex w-full max-w-[90rem] flex-col">
      <TestPageHeader testId="T-08" testKey="network" title="Network" description="Measure real latency and sustained download throughput against Cloudflare's edge network." canUndoTest={speedTest.status !== 'idle'} onUndoTest={undoTest} />

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(19rem,1fr)]">
        <Card className="instrument-panel">
          <CardContent className="p-5 sm:p-6">
            <PanelHeading label="Connection monitor" description="Browser state plus verified external reachability." className="mb-5" />
            <div className="live-readout relative flex min-h-[340px] items-center justify-center overflow-hidden p-6">
              <div className="relative z-10 flex w-full max-w-xl flex-col items-center text-center">
                <div className={`flex h-20 w-20 items-center justify-center rounded-full border ${isOnline ? 'border-status-pass/20 bg-status-pass/10 text-status-pass' : 'border-status-warn/20 bg-status-warn/10 text-status-warn'}`}>{isOnline ? <Wifi className="h-9 w-9" /> : <WifiOff className="h-9 w-9" />}</div>
                <span className="spec-item mt-6">Browser network state</span>
                <div className="readout-value mt-2 text-5xl tracking-[-0.05em]">{isOnline ? 'Online' : 'Offline'}</div>
                <p className="mt-2 text-sm text-muted-foreground">{isOnline ? 'A network interface is available. Run the measurement to verify internet reachability.' : 'The browser reports no active network interface.'}</p>
                <div className="mt-7 flex items-center gap-2 rounded-md border border-border/80 bg-card/75 px-4 py-2 font-mono text-[10px] uppercase tracking-wider shadow-sm">
                  <span className={`h-2 w-2 rounded-full ${speedTest.status === 'done' ? 'bg-status-pass' : speedTest.status === 'error' ? 'bg-status-warn' : speedTest.status === 'running' ? 'bg-primary [animation:led-pulse_2s_ease-in-out_infinite]' : 'bg-status-idle'}`} />
                  Cloudflare reachability: {reachability}
                </div>
              </div>
            </div>
            <div className="mt-5 grid grid-cols-3 gap-3">
              <MetricTile label="Link" value={isOnline ? 'Available' : 'Down'} accent={isOnline} />
              <MetricTile label="Type" value={effectiveType} detail="Browser estimate" />
              <MetricTile label="Latency" value={measuredLatency ?? browserLatency} detail={measuredLatency ? 'Measured' : 'Browser estimate'} />
            </div>
          </CardContent>
        </Card>

        <div className="flex flex-col gap-5">
          <Card className="instrument-panel">
            <CardContent className="p-5">
              <PanelHeading label="Download speed test" description="Adaptive multi-sample measurement" className="mb-5" />
              <div className="live-readout flex min-h-64 items-center justify-center p-5">
                {speedTest.status === 'idle' && (
                  <div className="relative z-10 w-full text-center">
                    <Gauge className="mx-auto h-7 w-7 text-primary" />
                    <p className="mt-3 font-mono text-xs font-semibold uppercase tracking-wide">Ready to measure</p>
                    <p className="mt-2 text-xs leading-relaxed text-muted-foreground">Runs for at least 6 seconds and may download up to 100 MB from Cloudflare. A same-site relay is used automatically if the direct stream is blocked. No file is retained.</p>
                    <Button onClick={runSpeedTest} disabled={!isOnline} className="mt-5 h-11 w-full gap-2 font-semibold"><Download className="h-4 w-4 shrink-0" /> Start Network Test</Button>
                  </div>
                )}
                {speedTest.status === 'running' && (
                  <div className="relative z-10 w-full text-center" aria-live="polite">
                    <span className="spec-item">{phaseLabel}</span>
                    <div className="readout-value mt-2 text-4xl text-primary">
                      {speedTest.phase === 'download' && speedTest.liveMbps != null ? speedTest.liveMbps : speedTest.progress}
                      <span className="ml-1 text-base text-muted-foreground">{speedTest.phase === 'download' && speedTest.liveMbps != null ? 'Mbps' : '%'}</span>
                    </div>
                    <Progress value={speedTest.progress} className="mt-5 h-2" />
                    <div className="mt-4 grid grid-cols-3 gap-2">
                      <MetricTile label="Latency" value={speedTest.latencyMs != null ? `${speedTest.latencyMs} ms` : 'N/A'} />
                      <MetricTile label="Samples" value={speedTest.samples} />
                      <MetricTile label="Data" value={`${(speedTest.transferredBytes / 1_000_000).toFixed(1)} MB`} />
                    </div>
                  </div>
                )}
                {speedTest.status === 'done' && (
                  <div className="relative z-10 w-full text-center">
                    <span className="spec-item">Measured download</span>
                    <div className="readout-value mt-2 text-5xl text-primary">{speedTest.mbps}<span className="ml-1 text-base text-muted-foreground">Mbps</span></div>
                    <div className="mt-4 grid grid-cols-3 gap-2">
                      <MetricTile label="Latency" value={`${speedTest.latencyMs} ms`} />
                      <MetricTile label="Jitter" value={`${speedTest.jitterMs} ms`} />
                      <MetricTile label="Duration" value={`${(speedTest.elapsedMs / 1000).toFixed(1)}s`} />
                    </div>
                    <p className="mt-3 font-mono text-[9px] uppercase tracking-wider text-muted-foreground">{speedTest.samples} download samples · {(speedTest.transferredBytes / 1_000_000).toFixed(1)} MB transferred</p>
                    <Button onClick={runSpeedTest} variant="outline" className="mt-5 h-10 w-full gap-2 font-semibold"><RotateCcw className="h-4 w-4 shrink-0" /> Test Again</Button>
                  </div>
                )}
                {speedTest.status === 'error' && (
                  <div className="relative z-10 w-full text-center">
                    <WifiOff className="mx-auto h-7 w-7 text-status-warn" />
                    <p className="mt-3 font-semibold text-status-warn">Measurement unavailable</p>
                    <p className="mt-2 text-xs text-muted-foreground">{speedTest.error}</p>
                    <Button onClick={runSpeedTest} variant="outline" disabled={!isOnline} className="mt-5 h-10 w-full font-semibold">Retry</Button>
                  </div>
                )}
              </div>
            </CardContent>
          </Card>

          <Card className="instrument-panel">
            <CardContent className="p-5">
              <PanelHeading label="Network evidence" description="Measured values are kept separate from browser estimates." className="mb-4" />
              <div className="space-y-3">
                <div className="flex items-center justify-between text-sm"><span className="flex items-center gap-2 text-muted-foreground"><Activity className="h-4 w-4" /> Measured latency</span><span className="readout-value text-xs">{measuredLatency ?? 'Not tested'}</span></div>
                <div className="flex items-center justify-between text-sm"><span className="flex items-center gap-2 text-muted-foreground"><Globe2 className="h-4 w-4" /> Browser downlink estimate</span><span className="readout-value text-xs">{connectionInfo?.downlink != null ? `${connectionInfo.downlink} Mbps` : 'N/A'}</span></div>
                <div className="flex items-center justify-between text-sm"><span className="flex items-center gap-2 text-muted-foreground"><Radio className="h-4 w-4" /> Data saver</span><span className="readout-value text-xs">{connectionInfo ? connectionInfo.saveData ? 'On' : 'Off' : 'N/A'}</span></div>
                <div className="flex items-center justify-between text-sm"><span className="flex items-center gap-2 text-muted-foreground"><Timer className="h-4 w-4" /> Test duration</span><span className="readout-value text-xs">{speedTest.status === 'done' ? `${(speedTest.elapsedMs / 1000).toFixed(1)}s` : 'N/A'}</span></div>
                <div className="flex items-center justify-between text-sm"><span className="flex items-center gap-2 text-muted-foreground"><Download className="h-4 w-4" /> Measurement route</span><span className="readout-value text-xs">{measurementRouteLabel}</span></div>
              </div>
            </CardContent>
          </Card>

          <Card className="instrument-panel"><CardContent className="flex items-center justify-between p-5"><span className="panel-label">Result</span><TestStatusBadge status={results.network} /></CardContent></Card>
        </div>
      </div>
    </motion.div>
  );
}
