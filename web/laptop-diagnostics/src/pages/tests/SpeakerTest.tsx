import { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { AudioLines, Check, Play, Square, Volume1, Volume2 } from 'lucide-react';
import { useTestContext } from '@/context/TestContext';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Slider } from '@/components/ui/slider';
import { TestPageHeader } from '@/components/TestPageHeader';
import { MetricTile, PanelHeading, TestStatusBadge, WaitingReadout } from '@/components/DiagnosticPrimitives';
import { cn } from '@/lib/utils';

type Channel = 'left' | 'right' | 'both';
type SoundPresetId = 'sweep' | 'melody' | 'chord';
type PlayingMode = Channel | SoundPresetId | null;

const MAX_OUTPUT_GAIN = 0.5;

const soundPresets: Array<{
  id: SoundPresetId;
  label: string;
  detail: string;
  readout: string;
  duration: number;
}> = [
  {
    id: 'sweep',
    label: 'Frequency Sweep',
    detail: 'Smooth rise from 200 Hz to 2 kHz',
    readout: '200 Hz - 2 kHz',
    duration: 2.5,
  },
  {
    id: 'melody',
    label: 'Reference Melody',
    detail: 'Short five-note clarity check',
    readout: 'C5 - G5',
    duration: 2.4,
  },
  {
    id: 'chord',
    label: 'Reference Chord',
    detail: 'Layered tones for distortion checks',
    readout: 'C major',
    duration: 3,
  },
];

export function SpeakerTest() {
  const { results, setResult } = useTestContext();
  const [playing, setPlaying] = useState<PlayingMode>(null);
  const [selectedSound, setSelectedSound] = useState<SoundPresetId>('sweep');
  const [volume, setVolume] = useState([50]);
  const [hasTestActivity, setHasTestActivity] = useState(false);
  const audioContextRef = useRef<AudioContext | null>(null);
  const oscillatorsRef = useRef<OscillatorNode[]>([]);
  const masterGainRef = useRef<GainNode | null>(null);
  const timerRef = useRef<number | null>(null);

  const selectedPreset = soundPresets.find((preset) => preset.id === selectedSound) ?? soundPresets[0];

  const initAudio = () => {
    if (!audioContextRef.current) {
      const AudioContextClass = window.AudioContext || (window as typeof window & { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      audioContextRef.current = new AudioContextClass();
    }
    if (audioContextRef.current.state === 'suspended') {
      void audioContextRef.current.resume();
    }
    return audioContextRef.current;
  };

  const stopTone = () => {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
    timerRef.current = null;
    oscillatorsRef.current.forEach((oscillator) => {
      try {
        oscillator.stop();
        oscillator.disconnect();
      } catch {
        // The scheduled oscillator may already have ended.
      }
    });
    oscillatorsRef.current = [];
    masterGainRef.current?.disconnect();
    masterGainRef.current = null;
    setPlaying(null);
  };

  const createMasterGain = (context: AudioContext) => {
    const gain = context.createGain();
    gain.gain.value = (volume[0] / 100) * MAX_OUTPUT_GAIN;
    gain.connect(context.destination);
    masterGainRef.current = gain;
    return gain;
  };

  const finishAfter = (durationSeconds: number) => {
    timerRef.current = window.setTimeout(() => stopTone(), durationSeconds * 1000);
  };

  const markSoundStarted = () => {
    setHasTestActivity(true);
    if (results.speaker === 'untested') setResult('speaker', 'working');
  };

  const playTone = (channel: Channel) => {
    stopTone();
    const context = initAudio();
    const masterGain = createMasterGain(context);
    const oscillator = context.createOscillator();
    const panner = context.createStereoPanner ? context.createStereoPanner() : null;

    oscillator.type = 'sine';
    oscillator.frequency.value = 440;
    if (panner) {
      panner.pan.value = channel === 'left' ? -1 : channel === 'right' ? 1 : 0;
      oscillator.connect(panner);
      panner.connect(masterGain);
    } else {
      oscillator.connect(masterGain);
    }

    oscillator.start();
    oscillatorsRef.current = [oscillator];
    setPlaying(channel);
    finishAfter(3);
    markSoundStarted();
  };

  const playSweep = (context: AudioContext, masterGain: GainNode) => {
    const oscillator = context.createOscillator();
    oscillator.type = 'sine';
    oscillator.frequency.setValueAtTime(200, context.currentTime);
    oscillator.frequency.exponentialRampToValueAtTime(2000, context.currentTime + 2.5);
    oscillator.connect(masterGain);
    oscillator.start();
    oscillator.stop(context.currentTime + 2.5);
    oscillatorsRef.current = [oscillator];
  };

  const playMelody = (context: AudioContext, masterGain: GainNode) => {
    const notes = [523.25, 659.25, 783.99, 659.25, 523.25];
    const noteLength = 0.38;
    const gap = 0.46;
    const startAt = context.currentTime + 0.04;

    oscillatorsRef.current = notes.map((frequency, index) => {
      const oscillator = context.createOscillator();
      const envelope = context.createGain();
      const noteStart = startAt + index * gap;
      const noteEnd = noteStart + noteLength;

      oscillator.type = 'sine';
      oscillator.frequency.value = frequency;
      envelope.gain.setValueAtTime(0.0001, noteStart);
      envelope.gain.exponentialRampToValueAtTime(0.7, noteStart + 0.03);
      envelope.gain.exponentialRampToValueAtTime(0.0001, noteEnd);
      oscillator.connect(envelope);
      envelope.connect(masterGain);
      oscillator.start(noteStart);
      oscillator.stop(noteEnd + 0.02);
      return oscillator;
    });
  };

  const playChord = (context: AudioContext, masterGain: GainNode) => {
    const frequencies = [261.63, 329.63, 392];
    const startAt = context.currentTime + 0.03;
    const endAt = startAt + 3;

    oscillatorsRef.current = frequencies.map((frequency) => {
      const oscillator = context.createOscillator();
      const voiceGain = context.createGain();
      oscillator.type = 'sine';
      oscillator.frequency.value = frequency;
      voiceGain.gain.setValueAtTime(0.0001, startAt);
      voiceGain.gain.exponentialRampToValueAtTime(0.3, startAt + 0.05);
      voiceGain.gain.setValueAtTime(0.3, endAt - 0.12);
      voiceGain.gain.exponentialRampToValueAtTime(0.0001, endAt);
      oscillator.connect(voiceGain);
      voiceGain.connect(masterGain);
      oscillator.start(startAt);
      oscillator.stop(endAt + 0.02);
      return oscillator;
    });
  };

  const playSelectedSound = () => {
    stopTone();
    const context = initAudio();
    const masterGain = createMasterGain(context);

    if (selectedSound === 'sweep') playSweep(context, masterGain);
    if (selectedSound === 'melody') playMelody(context, masterGain);
    if (selectedSound === 'chord') playChord(context, masterGain);

    setPlaying(selectedSound);
    finishAfter(selectedPreset.duration + 0.1);
    markSoundStarted();
  };

  const selectSound = (presetId: SoundPresetId) => {
    if (playing === 'sweep' || playing === 'melody' || playing === 'chord') stopTone();
    setSelectedSound(presetId);
  };

  useEffect(() => {
    const context = audioContextRef.current;
    const gain = masterGainRef.current;
    if (!context || !gain) return;
    const nextValue = (volume[0] / 100) * MAX_OUTPUT_GAIN;
    gain.gain.cancelScheduledValues(context.currentTime);
    gain.gain.setTargetAtTime(nextValue, context.currentTime, 0.015);
  }, [volume]);

  useEffect(() => () => {
    stopTone();
    void audioContextRef.current?.close();
  }, []);

  const undoTest = () => {
    stopTone();
    setSelectedSound('sweep');
    setVolume([50]);
    setHasTestActivity(false);
  };

  const activePreset = soundPresets.find((preset) => preset.id === playing);
  const activeLabel = playing === 'left'
    ? 'Left channel'
    : playing === 'right'
      ? 'Right channel'
      : playing === 'both'
        ? 'Stereo pair'
        : activePreset?.label ?? 'No tone';
  const toneReadout = activePreset?.readout ?? (playing ? '440 Hz' : selectedPreset.readout);

  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="test-page mx-auto flex w-full max-w-[90rem] flex-col">
      <TestPageHeader
        testId="T-05"
        testKey="speaker"
        title="Speaker"
        description="Verify left, right, stereo, live volume response, and multiple reference sounds."
        canUndoTest={hasTestActivity || selectedSound !== 'sweep' || volume[0] !== 50}
        onUndoTest={undoTest}
      />

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(19rem,1fr)]">
        <Card className="instrument-panel">
          <CardContent className="p-5 sm:p-6">
            <PanelHeading label="Stereo output" description="Each channel tone plays for up to three seconds." className="mb-5" />
            <div className="live-readout relative flex min-h-[340px] flex-col items-center justify-center overflow-hidden p-6">
              <div className="relative z-10 flex h-32 w-32 items-center justify-center">
                {playing && (
                  <>
                    <span className="absolute h-full w-full rounded-full border border-primary/15 [animation:trace-pulse_1.8s_ease-in-out_infinite]" />
                    <span className="absolute h-24 w-24 rounded-full border border-primary/25 [animation:trace-pulse_1.4s_ease-in-out_infinite]" />
                  </>
                )}
                <div className="test-hero-icon"><Volume2 className="h-8 w-8" /></div>
              </div>
              <div className="relative z-10 mt-5 text-center">
                <span className="spec-item">Current output</span>
                <div className="readout-value mt-2 text-2xl">{activeLabel}</div>
                <p className="mt-2 text-sm text-muted-foreground">{playing ? 'Sound is playing now' : 'Select a channel or reference sound'}</p>
              </div>
              <div className="relative z-10 mt-7 grid w-full max-w-lg grid-cols-3 gap-3">
                {(['left', 'both', 'right'] as const).map((channel) => (
                  <Button
                    key={channel}
                    variant={playing === channel ? 'default' : 'outline'}
                    className="h-auto flex-col gap-1 py-3 font-semibold"
                    onClick={() => playing === channel ? stopTone() : playTone(channel)}
                  >
                    <span>{channel === 'both' ? 'Both' : channel[0].toUpperCase() + channel.slice(1)}</span>
                    <span className={cn('font-mono text-[9px] uppercase tracking-wider', playing === channel ? 'text-white/70' : 'text-muted-foreground')}>
                      {channel === 'left' ? 'L / -1.0' : channel === 'right' ? 'R / +1.0' : 'L + R / 0'}
                    </span>
                  </Button>
                ))}
              </div>
            </div>
            <div className="mt-5 grid grid-cols-3 gap-3">
              <MetricTile label="Output" value={playing ? 'Active' : 'Idle'} accent={Boolean(playing)} />
              <MetricTile label="Tone" value={toneReadout} />
              <MetricTile label="Volume" value={volume[0] + '%'} />
            </div>
          </CardContent>
        </Card>

        <div className="flex flex-col gap-5">
          <Card className="instrument-panel">
            <CardContent className="p-5">
              <PanelHeading label="Output level" description="Changes the active sound immediately" className="mb-5" />
              <div className="flex items-center gap-3">
                <Volume1 className="h-4 w-4 shrink-0 text-muted-foreground" />
                <Slider value={volume} onValueChange={setVolume} max={100} step={1} aria-label="Speaker output level" className="flex-1" />
                <span className="readout-value w-12 text-right text-sm">{volume[0]}%</span>
              </div>
            </CardContent>
          </Card>

          <Card className="instrument-panel">
            <CardContent className="p-5">
              <PanelHeading label="Sound checks" description="Choose a locally generated reference sound." className="mb-4" />
              <div className="grid gap-2">
                {soundPresets.map((preset) => {
                  const isSelected = selectedSound === preset.id;
                  const isPlaying = playing === preset.id;

                  return (
                    <button
                      key={preset.id}
                      type="button"
                      aria-pressed={isSelected}
                      className={cn(
                        'metric-tile relative flex items-center justify-between gap-4 overflow-hidden p-3 text-left transition-[border-color,background-color,box-shadow,transform] hover:-translate-y-0.5 hover:border-primary/40 hover:bg-primary/5 hover:shadow-sm',
                        isSelected && 'border-primary/65 bg-primary/10 shadow-sm ring-1 ring-primary/20',
                        isPlaying && 'border-primary bg-primary/15 shadow-md shadow-primary/10 ring-2 ring-primary/25',
                      )}
                      onClick={() => selectSound(preset.id)}
                    >
                      {isSelected && <span className="absolute inset-y-2 left-0 w-1 rounded-r-full bg-primary" />}
                      <span className="min-w-0 pl-1">
                        <span className={cn('block text-sm font-semibold', isSelected && 'text-primary')}>{preset.label}</span>
                        <span className="mt-1 block text-xs text-muted-foreground">{preset.detail}</span>
                      </span>
                      <span className="flex shrink-0 flex-col items-end gap-2">
                        <span className="font-mono text-[9px] text-muted-foreground">{preset.duration.toFixed(1)}s</span>
                        {isSelected && (
                          <span className="flex items-center gap-1 rounded-md border border-primary/25 bg-primary/10 px-2 py-1 font-mono text-[8px] font-semibold uppercase tracking-wider text-primary">
                            {isPlaying ? <AudioLines className="h-3 w-3 [animation:trace-pulse_1s_ease-in-out_infinite]" /> : <Check className="h-3 w-3" />}
                            {isPlaying ? 'Playing' : 'Selected'}
                          </span>
                        )}
                      </span>
                    </button>
                  );
                })}
              </div>

              <div className="live-readout mt-4 flex min-h-24 items-center justify-center p-4">
                {playing === selectedSound ? (
                  <div className="relative z-10 space-y-2 text-center">
                    <AudioLines className="mx-auto h-6 w-6 text-primary [animation:trace-pulse_1s_ease-in-out_infinite]" />
                    <p className="readout-value text-sm">{selectedPreset.readout}</p>
                  </div>
                ) : (
                  <WaitingReadout title={selectedPreset.label + ' ready'} detail={selectedPreset.detail} />
                )}
              </div>

              <Button
                variant={playing === selectedSound ? 'outline' : 'secondary'}
                className="mt-4 h-11 w-full gap-2 font-semibold"
                onClick={() => playing === selectedSound ? stopTone() : playSelectedSound()}
              >
                {playing === selectedSound ? <Square className="h-4 w-4 shrink-0" /> : <Play className="h-4 w-4 shrink-0 text-primary" />}
                {playing === selectedSound ? 'Stop Sound' : 'Play ' + selectedPreset.label}
              </Button>
            </CardContent>
          </Card>

          <Card className="instrument-panel">
            <CardContent className="flex items-center justify-between p-5"><span className="panel-label">Result</span><TestStatusBadge status={results.speaker} /></CardContent>
          </Card>
        </div>
      </div>
    </motion.div>
  );
}