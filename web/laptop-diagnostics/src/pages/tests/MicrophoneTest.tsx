import { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { LockKeyhole, Mic as MicIcon, Pause, Play, RotateCcw, ShieldAlert, Waves } from 'lucide-react';
import { useTestContext } from '@/context/TestContext';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { TestPageHeader } from '@/components/TestPageHeader';
import { MetricTile, PanelHeading, TestStatusBadge, WaitingReadout } from '@/components/DiagnosticPrimitives';

type SampleState = 'idle' | 'recording' | 'ready' | 'unsupported' | 'error';

const SAMPLE_DURATION_SECONDS = 5;
const SIGNAL_DETECTION_THRESHOLD = 3;

export function MicrophoneTest() {
  const { results, setResult } = useTestContext();
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sampleError, setSampleError] = useState<string | null>(null);
  const [sampleState, setSampleState] = useState<SampleState>('idle');
  const [sampleUrl, setSampleUrl] = useState<string | null>(null);
  const [captureElapsed, setCaptureElapsed] = useState(0);
  const [isStarting, setIsStarting] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [volume, setVolume] = useState(0);
  const [peak, setPeak] = useState(0);
  const peakRef = useRef(0);
  const streamRef = useRef<MediaStream | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const sampleUrlRef = useRef<string | null>(null);
  const audioRef = useRef<HTMLAudioElement>(null);
  const stopCaptureTimerRef = useRef<number | null>(null);
  const captureTickerRef = useRef<number | null>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const animationFrameRef = useRef(0);
  const requestIdRef = useRef(0);

  const clearCaptureTimers = () => {
    if (stopCaptureTimerRef.current !== null) window.clearTimeout(stopCaptureTimerRef.current);
    if (captureTickerRef.current !== null) window.clearInterval(captureTickerRef.current);
    stopCaptureTimerRef.current = null;
    captureTickerRef.current = null;
  };

  const stopLiveInput = () => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    setStream(null);
    analyserRef.current = null;
    if (audioContextRef.current) {
      void audioContextRef.current.close();
      audioContextRef.current = null;
    }
    cancelAnimationFrame(animationFrameRef.current);
    setVolume(0);
  };

  const discardActiveCapture = () => {
    clearCaptureTimers();
    const recorder = mediaRecorderRef.current;
    if (recorder && recorder.state !== 'inactive') {
      recorder.ondataavailable = null;
      recorder.onstop = null;
      recorder.onerror = null;
      recorder.stop();
    }
    mediaRecorderRef.current = null;
  };

  const stopStream = () => {
    discardActiveCapture();
    stopLiveInput();
  };

  const clearSample = () => {
    const audio = audioRef.current;
    if (audio) {
      audio.pause();
      audio.removeAttribute('src');
      audio.load();
    }
    setIsPlaying(false);
    if (sampleUrlRef.current) URL.revokeObjectURL(sampleUrlRef.current);
    sampleUrlRef.current = null;
    setSampleUrl(null);
  };

  const drawWaveform = () => {
    const analyser = analyserRef.current;
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!analyser || !canvas || !ctx) return;
    const data = new Uint8Array(analyser.frequencyBinCount);

    const draw = () => {
      animationFrameRef.current = requestAnimationFrame(draw);
      analyser.getByteTimeDomainData(data);
      let sum = 0;
      for (let index = 0; index < data.length; index++) {
        const normalized = (data[index] - 128) / 128;
        sum += normalized * normalized;
      }
      const level = Math.min(100, Math.floor(Math.sqrt(sum / data.length) * 400));
      setVolume(level);
      if (level >= SIGNAL_DETECTION_THRESHOLD && peakRef.current < SIGNAL_DETECTION_THRESHOLD) {
        setResult('microphone', 'working');
      }
      peakRef.current = Math.max(peakRef.current, level);
      setPeak(peakRef.current);

      const isDark = document.documentElement.classList.contains('dark');
      const primary = getComputedStyle(document.documentElement).getPropertyValue('--primary').trim();
      ctx.fillStyle = isDark ? '#181C24' : '#F7F8FA';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.strokeStyle = isDark ? 'rgba(255,255,255,0.045)' : 'rgba(0,0,0,0.045)';
      ctx.lineWidth = 1;
      for (let y = 24; y < canvas.height; y += 24) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(canvas.width, y);
        ctx.stroke();
      }
      ctx.strokeStyle = `hsl(${primary})`;
      ctx.lineWidth = 3;
      ctx.beginPath();
      const sliceWidth = canvas.width / data.length;
      data.forEach((sample, index) => {
        const x = index * sliceWidth;
        const y = (sample / 128) * canvas.height / 2;
        if (index === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.stroke();
    };
    draw();
  };

  const beginFiveSecondCapture = (nextStream: MediaStream) => {
    clearSample();
    setSampleError(null);
    setCaptureElapsed(0);

    if (typeof MediaRecorder === 'undefined') {
      setSampleState('unsupported');
      setSampleError('This browser can show the live microphone signal, but it cannot create a local playback sample.');
      return;
    }

    const preferredMimeType = [
      'audio/webm;codecs=opus',
      'audio/webm',
      'audio/ogg;codecs=opus',
      'audio/mp4',
    ].find((type) => MediaRecorder.isTypeSupported(type));

    try {
      const recorder = preferredMimeType
        ? new MediaRecorder(nextStream, { mimeType: preferredMimeType })
        : new MediaRecorder(nextStream);
      const chunks: Blob[] = [];
      mediaRecorderRef.current = recorder;

      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) chunks.push(event.data);
      };

      recorder.onerror = () => {
        clearCaptureTimers();
        recorder.ondataavailable = null;
        recorder.onstop = null;
        recorder.onerror = null;
        mediaRecorderRef.current = null;
        setSampleState('error');
        setSampleError('The browser could not finish the local audio sample. Please record it again.');
        setResult('microphone', 'issue');
        stopLiveInput();
      };

      recorder.onstop = () => {
        clearCaptureTimers();
        mediaRecorderRef.current = null;
        stopLiveInput();
        if (chunks.length === 0) {
          setSampleState('error');
          setSampleError('No audio data was captured. Please record the sample again.');
          setResult('microphone', 'issue');
          return;
        }
        const blob = new Blob(chunks, { type: recorder.mimeType || chunks[0].type || 'audio/webm' });
        const url = URL.createObjectURL(blob);
        sampleUrlRef.current = url;
        setSampleUrl(url);
        setCaptureElapsed(SAMPLE_DURATION_SECONDS);
        setSampleState('ready');
        setResult('microphone', peakRef.current >= SIGNAL_DETECTION_THRESHOLD ? 'working' : 'issue');
      };

      recorder.start(250);
      setSampleState('recording');
      const startedAt = performance.now();
      captureTickerRef.current = window.setInterval(() => {
        setCaptureElapsed(Math.min(SAMPLE_DURATION_SECONDS, (performance.now() - startedAt) / 1000));
      }, 100);
      stopCaptureTimerRef.current = window.setTimeout(() => {
        if (recorder.state !== 'inactive') recorder.stop();
      }, SAMPLE_DURATION_SECONDS * 1000);
    } catch (caught) {
      console.error('Local microphone sample error:', caught);
      setSampleState('error');
      setSampleError('This browser could not start the five-second local recording.');
      setResult('microphone', 'issue');
      stopLiveInput();
    }
  };

  const startMic = async () => {
    stopStream();
    const requestId = ++requestIdRef.current;
    setError(null);
    setSampleError(null);
    peakRef.current = 0;
    setPeak(0);
    setIsStarting(true);
    try {
      const nextStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      if (requestId !== requestIdRef.current) {
        nextStream.getTracks().forEach((track) => track.stop());
        return;
      }
      streamRef.current = nextStream;
      setStream(nextStream);
      const AudioContextClass = window.AudioContext || (window as typeof window & { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      const audioContext = new AudioContextClass();
      audioContextRef.current = audioContext;
      const analyser = audioContext.createAnalyser();
      analyser.fftSize = 256;
      analyser.smoothingTimeConstant = 0.82;
      audioContext.createMediaStreamSource(nextStream).connect(analyser);
      analyserRef.current = analyser;
      drawWaveform();
      beginFiveSecondCapture(nextStream);
    } catch (caught) {
      if (requestId !== requestIdRef.current) return;
      stopStream();
      const message = caught instanceof Error ? caught.message : 'Failed to access microphone';
      console.error('Microphone access error:', caught);
      setError(message);
      setResult('microphone', 'issue');
    } finally {
      setIsStarting(false);
    }
  };

  const togglePlayback = async () => {
    const audio = audioRef.current;
    if (!audio || !sampleUrl) return;
    if (!audio.paused) {
      audio.pause();
      setIsPlaying(false);
      return;
    }
    try {
      audio.currentTime = 0;
      await audio.play();
      setIsPlaying(true);
    } catch (caught) {
      console.error('Microphone sample playback error:', caught);
      setSampleError('The browser could not play this sample. Please record it again.');
    }
  };

  useEffect(() => {
    const resizeCanvas = () => {
      const canvas = canvasRef.current;
      const parent = canvas?.parentElement;
      if (canvas && parent) {
        canvas.width = parent.clientWidth;
        canvas.height = parent.clientHeight;
      }
    };
    const releaseMicrophone = () => {
      requestIdRef.current += 1;
      stopStream();
      clearSample();
    };
    window.addEventListener('resize', resizeCanvas);
    window.addEventListener('pagehide', releaseMicrophone);
    resizeCanvas();
    return () => {
      window.removeEventListener('resize', resizeCanvas);
      window.removeEventListener('pagehide', releaseMicrophone);
      releaseMicrophone();
    };
  }, []);

  const undoTest = () => {
    requestIdRef.current += 1;
    stopStream();
    clearSample();
    setError(null);
    setSampleError(null);
    setSampleState('idle');
    setCaptureElapsed(0);
    setIsStarting(false);
    setVolume(0);
    peakRef.current = 0;
    setPeak(0);
    const canvas = canvasRef.current;
    canvas?.getContext('2d')?.clearRect(0, 0, canvas.width, canvas.height);
  };

  const captureProgress = Math.min(100, (captureElapsed / SAMPLE_DURATION_SECONDS) * 100);
  const inputState = sampleState === 'recording' ? 'Recording' : sampleState === 'ready' ? 'Captured' : stream ? 'Listening' : 'Idle';
  const resultState = error
    ? 'Unavailable'
    : sampleState === 'recording'
      ? 'Capturing'
      : sampleState === 'ready'
        ? peak >= SIGNAL_DETECTION_THRESHOLD ? 'Detected' : 'No signal'
        : sampleState === 'error'
          ? 'Capture failed'
          : 'Not tested';

  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="test-page mx-auto flex w-full max-w-[90rem] flex-col">
      <TestPageHeader
        testId="T-04"
        testKey="microphone"
        title="Microphone"
        description="Capture five seconds, inspect the input response, then play the newest sample locally."
        canUndoTest={isStarting || stream !== null || error !== null || sampleState !== 'idle' || sampleUrl !== null || captureElapsed > 0 || volume > 0 || peak > 0}
        onUndoTest={undoTest}
      />

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(19rem,1fr)]">
        <Card className="instrument-panel">
          <CardContent className="p-5 sm:p-6">
            <PanelHeading label="Live waveform" description="The waveform and five-second sample are processed locally in this tab." className="mb-5" />
            <div className="live-readout relative flex min-h-[350px] items-center justify-center overflow-hidden">
              {!stream && !error && (
                <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-5 bg-card/92 px-6 text-center backdrop-blur-md">
                  <div className="test-hero-icon"><MicIcon className="h-8 w-8" /></div>
                  <div>
                    <h3 className="font-display text-lg font-bold">{sampleState === 'ready' ? 'Five-second sample ready' : 'Ready to capture locally'}</h3>
                    <p className="mt-2 max-w-md text-sm leading-relaxed text-muted-foreground">
                      {sampleState === 'ready'
                        ? 'Use Local sample to hear the newest capture, or record another five seconds.'
                        : 'Grant microphone permission. Recording begins immediately, stops after five seconds, and stays only in this tab.'}
                    </p>
                  </div>
                  <Button onClick={startMic} disabled={isStarting} className="h-11 min-w-48 gap-2 font-semibold">
                    {sampleState === 'ready' ? <RotateCcw className="h-4 w-4 shrink-0" /> : <Waves className="h-4 w-4 shrink-0" />}
                    {isStarting ? 'Requesting Access' : sampleState === 'ready' ? 'Record New Sample' : 'Start Microphone Test'}
                  </Button>
                </div>
              )}
              {error && (
                <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-5 bg-card/92 px-6 text-center backdrop-blur-md">
                  <div className="test-hero-icon border-status-fail/25 bg-status-fail/10 text-status-fail"><ShieldAlert className="h-8 w-8" /></div>
                  <div><h3 className="font-display text-lg font-bold">Microphone unavailable</h3><p className="mt-2 max-w-md text-sm leading-relaxed text-muted-foreground">{error}. Check browser permissions, then retry.</p></div>
                  <Button variant="outline" onClick={startMic} disabled={isStarting} className="h-11 gap-2 font-semibold"><RotateCcw className="h-4 w-4" />Retry Microphone</Button>
                </div>
              )}
              <canvas ref={canvasRef} className="absolute inset-0 h-full w-full" />
              {stream && (
                <div className="absolute left-4 top-4 z-10 flex items-center gap-2 rounded-md border border-primary/20 bg-card/75 px-3 py-2 font-mono text-[10px] uppercase tracking-[0.12em] backdrop-blur-md">
                  <span className="signal-status-dot" />
                  {sampleState === 'recording'
                    ? `Recording ${captureElapsed.toFixed(1)} / ${SAMPLE_DURATION_SECONDS}.0s`
                    : 'Listening locally'}
                </div>
              )}
            </div>
            <div className="mt-5 grid grid-cols-3 gap-3">
              <MetricTile label="Input" value={inputState} accent={sampleState === 'recording' || sampleState === 'ready'} />
              <MetricTile label="Level" value={`${volume}%`} />
              <MetricTile label="Peak" value={`${peak}%`} />
            </div>

            <div className="mt-5 rounded-xl border border-border/80 bg-background/40 p-5 sm:p-6">
              <div className="flex items-center justify-between">
                <span className="panel-label">Result</span>
                <TestStatusBadge status={results.microphone} />
              </div>
              <div className="mt-4 grid grid-cols-2 gap-3">
                <MetricTile label="Peak" value={`${peak}%`} accent={peak >= SIGNAL_DETECTION_THRESHOLD} />
                <MetricTile label="State" value={resultState} accent={resultState === 'Detected'} />
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="flex flex-col gap-5">
          <Card className="instrument-panel">
            <CardContent className="p-5">
              <PanelHeading label="Input level" description="Live signal intensity" className="mb-5" />
              <div className="live-readout p-5">
                <div className="relative z-10 flex items-end justify-between"><span className="spec-item">Current</span><span className="readout-value text-4xl text-primary">{volume}<span className="ml-1 text-base text-muted-foreground">%</span></span></div>
                <div className="relative z-10 mt-5 grid grid-cols-10 gap-1">
                  {Array.from({ length: 10 }, (_, index) => <span key={index} className={`h-12 rounded-sm transition-colors ${volume >= (index + 1) * 10 ? index >= 8 ? 'bg-status-warn' : 'bg-primary' : 'bg-secondary'}`} />)}
                </div>
              </div>
            </CardContent>
          </Card>


          <Card className="instrument-panel">
            <CardContent className="p-5">
              <PanelHeading label="Local sample" description="Newest five-second capture" className="mb-4" />
              <audio ref={audioRef} src={sampleUrl ?? undefined} className="hidden" preload="metadata" onEnded={() => setIsPlaying(false)} onPause={() => setIsPlaying(false)} />

              {sampleState === 'recording' && (
                <div className="live-readout p-4" aria-live="polite">
                  <div className="flex items-center justify-between"><span className="spec-item">Recording</span><span className="readout-value text-sm">{captureElapsed.toFixed(1)} / {SAMPLE_DURATION_SECONDS}.0s</span></div>
                  <div className="mt-4 h-2 overflow-hidden rounded-full bg-secondary"><div className="h-full bg-primary transition-[width] duration-100" style={{ width: `${captureProgress}%` }} /></div>
                </div>
              )}

              {sampleState === 'ready' && sampleUrl && (
                <div className="space-y-3">
                  <Button onClick={togglePlayback} className="h-11 w-full gap-2 font-semibold">
                    {isPlaying ? <Pause className="h-4 w-4 shrink-0" /> : <Play className="h-4 w-4 shrink-0" />}
                    {isPlaying ? 'Pause Playback' : 'Play Latest 5 Seconds'}
                  </Button>
                  <Button variant="outline" onClick={startMic} disabled={isStarting} className="h-10 w-full gap-2 font-semibold"><RotateCcw className="h-4 w-4 shrink-0" />Record Again</Button>
                </div>
              )}

              {(sampleState === 'idle' || sampleState === 'unsupported' || sampleState === 'error') && (
                <div className="live-readout flex min-h-24 items-center justify-center p-4">
                  <WaitingReadout
                    title={sampleState === 'unsupported' ? 'Playback unsupported' : sampleState === 'error' ? 'Sample unavailable' : 'No sample yet'}
                    detail={sampleError ?? 'Start the microphone test to capture five seconds'}
                  />
                </div>
              )}

              <div className="mt-4 flex items-start gap-2 border-t border-border/70 pt-4 text-xs leading-relaxed text-muted-foreground">
                <LockKeyhole className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
                <span>Held temporarily in this tab's memory. Never uploaded, saved to localStorage, or retained after you leave or record again.</span>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </motion.div>
  );
}