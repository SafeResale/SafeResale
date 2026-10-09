import { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { Compass, Fingerprint, Move3D, Smartphone } from 'lucide-react';
import { useTestContext } from '@/context/TestContext';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { TestPageHeader } from '@/components/TestPageHeader';
import { MetricTile, PanelHeading, TestStatusBadge } from '@/components/DiagnosticPrimitives';

type Orientation = { alpha: number; beta: number; gamma: number };
type MotionReading = { x: number | null; y: number | null; z: number | null };
type TouchPoint = { id: number; x: number; y: number };

export function SensorsTest() {
  const { results, setResult } = useTestContext();
  const [permissionGranted, setPermissionGranted] = useState<boolean | null>(null);
  const [orientation, setOrientation] = useState<Orientation | null>(null);
  const [motionData, setMotionData] = useState<MotionReading | null>(null);
  const [touches, setTouches] = useState<TouchPoint[]>([]);
  const [touchDetected, setTouchDetected] = useState(false);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const touchesRef = useRef<TouchPoint[]>([]);
  const touchSupported = navigator.maxTouchPoints > 0;

  const requestPermission = async () => {
    const hasMotionApi = 'DeviceOrientationEvent' in window || 'DeviceMotionEvent' in window;
    if (!hasMotionApi && !touchSupported) {
      setPermissionGranted(false);
      setResult('sensors', 'unsupported');
      return;
    }

    const OrientationEvent = window.DeviceOrientationEvent as (typeof DeviceOrientationEvent & { requestPermission?: () => Promise<string> }) | undefined;
    const requestMotionPermission = OrientationEvent?.requestPermission;
    if (typeof requestMotionPermission !== 'function') {
      setPermissionGranted(true);
      return;
    }
    try {
      const permission = await requestMotionPermission.call(OrientationEvent);
      setPermissionGranted(permission === 'granted');
      if (permission !== 'granted') setResult('sensors', 'issue');
    } catch {
      setPermissionGranted(false);
      setResult('sensors', 'issue');
    }
  };

  useEffect(() => {
    if (permissionGranted !== true) return;
    const handleOrientation = (event: DeviceOrientationEvent) => {
      if (event.alpha == null || event.beta == null || event.gamma == null) return;
      setOrientation({ alpha: event.alpha, beta: event.beta, gamma: event.gamma });
      if (results.sensors === 'untested') setResult('sensors', 'working');
    };
    const handleMotion = (event: DeviceMotionEvent) => {
      const acceleration = event.accelerationIncludingGravity;
      if (!acceleration || [acceleration.x, acceleration.y, acceleration.z].every((value) => value == null)) return;
      setMotionData({ x: acceleration.x, y: acceleration.y, z: acceleration.z });
    };
    window.addEventListener('deviceorientation', handleOrientation);
    window.addEventListener('devicemotion', handleMotion);
    return () => {
      window.removeEventListener('deviceorientation', handleOrientation);
      window.removeEventListener('devicemotion', handleMotion);
    };
  }, [permissionGranted, results.sensors, setResult]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const resizeCanvas = () => {
      const parent = canvas.parentElement;
      if (parent) {
        canvas.width = parent.clientWidth;
        canvas.height = parent.clientHeight;
      }
    };
    const updateTouches = (event: TouchEvent) => {
      event.preventDefault();
      const rect = canvas.getBoundingClientRect();
      const nextTouches = Array.from(event.touches).map((touch) => ({ id: touch.identifier, x: touch.clientX - rect.left, y: touch.clientY - rect.top }));
      touchesRef.current = nextTouches;
      setTouches(nextTouches);
      if (nextTouches.length > 0) {
        setTouchDetected(true);
        if (results.sensors === 'untested') setResult('sensors', 'working');
      }
    };
    window.addEventListener('resize', resizeCanvas);
    resizeCanvas();
    if (permissionGranted === true) {
      canvas.addEventListener('touchstart', updateTouches, { passive: false });
      canvas.addEventListener('touchmove', updateTouches, { passive: false });
      canvas.addEventListener('touchend', updateTouches, { passive: false });
      canvas.addEventListener('touchcancel', updateTouches, { passive: false });
    }

    const context = canvas.getContext('2d');
    let frame = 0;
    const render = () => {
      if (!context) return;
      const dark = document.documentElement.classList.contains('dark');
      const primary = getComputedStyle(document.documentElement).getPropertyValue('--primary').trim();
      context.clearRect(0, 0, canvas.width, canvas.height);
      context.strokeStyle = dark ? 'rgba(255,255,255,0.055)' : 'rgba(0,0,0,0.055)';
      context.lineWidth = 1;
      for (let x = 0; x < canvas.width; x += 32) {
        context.beginPath();
        context.moveTo(x, 0);
        context.lineTo(x, canvas.height);
        context.stroke();
      }
      for (let y = 0; y < canvas.height; y += 32) {
        context.beginPath();
        context.moveTo(0, y);
        context.lineTo(canvas.width, y);
        context.stroke();
      }
      touchesRef.current.forEach((touch) => {
        context.beginPath();
        context.arc(touch.x, touch.y, 46, 0, Math.PI * 2);
        context.fillStyle = `hsl(${primary} / 0.14)`;
        context.fill();
        context.beginPath();
        context.arc(touch.x, touch.y, 27, 0, Math.PI * 2);
        context.fillStyle = `hsl(${primary})`;
        context.fill();
        context.strokeStyle = 'rgba(255,255,255,0.8)';
        context.lineWidth = 2;
        context.stroke();
        context.fillStyle = '#fff';
        context.font = '600 13px IBM Plex Mono, monospace';
        context.textAlign = 'center';
        context.textBaseline = 'middle';
        context.fillText(String(touch.id), touch.x, touch.y);
      });
      if (touchesRef.current.length === 0) {
        context.fillStyle = dark ? '#7f8794' : '#9CA3AF';
        context.font = '500 12px IBM Plex Mono, monospace';
        context.textAlign = 'center';
        context.fillText(touchSupported ? 'TOUCH SURFACE' : 'TOUCH INPUT N/A', canvas.width / 2, canvas.height / 2);
      }
      frame = requestAnimationFrame(render);
    };
    render();

    return () => {
      window.removeEventListener('resize', resizeCanvas);
      if (permissionGranted === true) {
        canvas.removeEventListener('touchstart', updateTouches);
        canvas.removeEventListener('touchmove', updateTouches);
        canvas.removeEventListener('touchend', updateTouches);
        canvas.removeEventListener('touchcancel', updateTouches);
      }
      cancelAnimationFrame(frame);
    };
  }, [permissionGranted, results.sensors, setResult, touchSupported]);

  const undoTest = () => {
    setPermissionGranted(null);
    setOrientation(null);
    setMotionData(null);
    setTouches([]);
    touchesRef.current = [];
    setTouchDetected(false);
    const canvas = canvasRef.current;
    canvas?.getContext('2d')?.clearRect(0, 0, canvas.width, canvas.height);
  };

  const completedSignals = Number(Boolean(orientation)) + Number(Boolean(motionData)) + Number(touchDetected);
  const coverage = Math.round((completedSignals / 3) * 100);
  const degrees = (value?: number | null) => value == null ? 'N/A' : `${Math.round(value)}\u00B0`;
  const acceleration = (value?: number | null) => value == null ? 'N/A' : value.toFixed(1);

  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="test-page mx-auto flex w-full max-w-[90rem] flex-col">
      <TestPageHeader testId="T-09" testKey="sensors" title="Sensors" description="Monitor orientation, acceleration, and multi-touch input in real time." canUndoTest={permissionGranted === true || orientation !== null || motionData !== null || touchDetected || touches.length > 0} onUndoTest={undoTest} />

      <div className="grid grid-cols-1 items-start gap-5 lg:grid-cols-[minmax(0,1.08fr)_minmax(20rem,.92fr)]">
        <Card className="instrument-panel">
          <CardContent className="p-5 sm:p-6">
            <PanelHeading label="Motion telemetry" description="Live gyroscope and accelerometer readings." className="mb-5" />
            <div className="live-readout relative flex min-h-[300px] items-center justify-center overflow-hidden p-6">
              {permissionGranted === null && (
                <div className="relative z-10 flex max-w-md flex-col items-center gap-5 text-center"><div className="test-hero-icon"><Smartphone className="h-8 w-8" /></div><div><h3 className="font-display text-lg font-bold">Sensor test stopped</h3><p className="mt-2 text-sm text-muted-foreground">Start the test to attach motion and touch listeners. iOS may show a browser permission prompt.</p></div><Button onClick={requestPermission} className="h-11 gap-2 font-semibold"><Smartphone className="h-4 w-4 shrink-0" /> Start Sensor Test</Button></div>
              )}
              {permissionGranted === false && <div className="relative z-10 flex max-w-md flex-col items-center gap-5 text-center"><div className="test-hero-icon border-status-idle/25 bg-status-idle/10 text-status-idle"><Compass className="h-8 w-8" /></div><div><h3 className="font-display text-lg font-bold">Motion sensors unavailable</h3><p className="mt-2 text-sm text-muted-foreground">No orientation events are exposed by this browser or device.</p></div></div>}
              {permissionGranted === true && (
                <div className="relative z-10 flex flex-col items-center text-center">
                  <div className="relative flex h-32 w-24 items-center justify-center rounded-xl border-4 border-foreground/80 bg-card shadow-lg transition-transform duration-100" style={{ transform: `perspective(500px) rotateX(${-(orientation?.beta || 0) / 3}deg) rotateY(${(orientation?.gamma || 0) / 3}deg)` }}><span className="absolute top-2 h-1 w-8 rounded-full bg-foreground/20" /><Move3D className="h-8 w-8 text-primary" /></div>
                  <p className="mt-5 font-mono text-xs font-semibold uppercase tracking-wide">{orientation ? 'Orientation signal active' : 'Awaiting compatible sensor data'}</p>
                  <p className="mt-2 text-xs text-muted-foreground">Readings remain N/A on devices that do not expose motion sensors.</p>
                </div>
              )}
            </div>
            <div className="mt-5 grid grid-cols-3 gap-3">
              <MetricTile label="Alpha / Z" value={degrees(orientation?.alpha)} accent={Boolean(orientation)} />
              <MetricTile label="Beta / X" value={degrees(orientation?.beta)} />
              <MetricTile label="Gamma / Y" value={degrees(orientation?.gamma)} />
            </div>
            <div className="mt-3 grid grid-cols-3 gap-3">
              <MetricTile label="Accel X" value={acceleration(motionData?.x)} detail={motionData?.x == null ? undefined : 'm/s²'} />
              <MetricTile label="Accel Y" value={acceleration(motionData?.y)} detail={motionData?.y == null ? undefined : 'm/s²'} />
              <MetricTile label="Accel Z" value={acceleration(motionData?.z)} detail={motionData?.z == null ? undefined : 'm/s²'} />
            </div>
          </CardContent>
        </Card>

        <div className="flex flex-col gap-5">
          <Card className="instrument-panel overflow-hidden">
          <CardContent className="flex min-h-[440px] flex-col p-0">
            <div className="flex items-center justify-between border-b border-border/70 px-5 py-4 sm:px-6"><div><h2 className="panel-label">Touch matrix</h2><p className="mt-2 text-sm text-muted-foreground">Place one or more fingers inside the surface.</p></div><span className="readout-value text-sm text-primary">{touches.length}</span></div>
            <div className="relative min-h-[360px] flex-1"><div className="absolute left-4 top-4 z-10 flex items-center gap-2 rounded-md border border-border/70 bg-card/85 px-3 py-2 font-mono text-[10px] font-semibold uppercase tracking-wider shadow-sm backdrop-blur-md"><Fingerprint className="h-4 w-4 text-primary" /> {touchSupported ? `${touches.length} points` : 'N/A'}</div><canvas ref={canvasRef} className="h-full w-full touch-none bg-background/40" /></div>
            </CardContent>
          </Card>

          <Card className="instrument-panel">
            <CardContent className="p-5">
              <PanelHeading label="Coverage" description="Independent signals detected" className="mb-4" />
              <div className="live-readout p-5">
                <div className="relative z-10 flex items-end justify-between"><span className="readout-value text-4xl text-primary">{completedSignals}<span className="text-base text-muted-foreground"> / 3</span></span><span className="font-mono text-[10px] text-muted-foreground">{coverage}%</span></div>
                <div className="relative z-10 mt-5 h-2 overflow-hidden rounded-full bg-secondary"><div className="h-full rounded-full bg-primary transition-[width]" style={{ width: `${coverage}%` }} /></div>
              </div>
            </CardContent>
          </Card>

          <Card className="instrument-panel">
            <CardContent className="p-5">
              <div className="flex items-center justify-between"><span className="panel-label">Result</span><TestStatusBadge status={results.sensors} /></div>
              <div className="mt-5 grid grid-cols-2 gap-3">
                <MetricTile label="Detected" value={completedSignals} accent={completedSignals > 0} />
                <MetricTile label="Touch points" value={touchSupported ? touches.length : 'N/A'} />
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </motion.div>
  );
}
