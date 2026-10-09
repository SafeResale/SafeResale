import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { Activity, Info, Keyboard } from 'lucide-react';
import { useTestContext } from '@/context/TestContext';
import { Card, CardContent } from '@/components/ui/card';
import { TestPageHeader } from '@/components/TestPageHeader';
import { TestStatusBadge } from '@/components/DiagnosticPrimitives';
import { cn } from '@/lib/utils';
import {
  collectLayoutKeys,
  defaultKeyboardLayoutId,
  getKeyboardLayout,
  keyboardLayouts,
  type KeyDefinition,
  type KeyboardLayoutId,
} from './keyboardLayouts';

type RecentKey = { label: string; code: string; timestamp: number };

const systemHandledCodes = new Set(['PrintScreen', 'MetaLeft', 'MetaRight', 'ContextMenu', 'Pause']);
const modifierCodes = new Set(['ShiftLeft', 'ShiftRight', 'ControlLeft', 'ControlRight', 'AltLeft', 'AltRight', 'MetaLeft', 'MetaRight']);
/** OS often swallows keydown for these; browsers usually only emit keyup. */
const keyUpDetectableCodes = new Set(['PrintScreen', 'Pause']);
const AUTO_PASS_KEY_COUNT = 10;

function blockBrowserAction(event: KeyboardEvent) {
  if (event.cancelable) {
    event.preventDefault();
    event.stopPropagation();
  }
}

function resolvePrintScreenCode(event: KeyboardEvent): boolean {
  return event.code === 'PrintScreen'
    || event.key === 'PrintScreen'
    || event.keyCode === 44
    || event.which === 44;
}

function KeyboardKey({
  definition,
  tested,
  held,
  style,
}: {
  definition: KeyDefinition;
  tested: boolean;
  held?: boolean;
  style?: React.CSSProperties;
}) {
  const systemHandled = systemHandledCodes.has(definition.code);
  return (
    <div
      className={`keyboard-key keycap ${tested ? 'is-tested' : ''} ${held ? 'is-held' : ''} ${systemHandled ? 'is-system-key' : ''}`}
      style={style}
      title={`${definition.label} / ${definition.code}${systemHandled ? ' / May be reserved by the operating system' : ''}`}
      data-key-code={definition.code}
      data-system-key={systemHandled || undefined}
    >
      {definition.label}
    </div>
  );
}

export function KeyboardTest() {
  const { results, setResult } = useTestContext();
  const [layoutId, setLayoutId] = useState<KeyboardLayoutId>(defaultKeyboardLayoutId);
  const [testedCodes, setTestedCodes] = useState<Set<string>>(new Set());
  const [heldCodes, setHeldCodes] = useState<Set<string>>(new Set());
  const [pressCount, setPressCount] = useState(0);
  const [recentKeys, setRecentKeys] = useState<RecentKey[]>([]);
  const testedCodesRef = useRef(testedCodes);
  testedCodesRef.current = testedCodes;

  const layout = useMemo(() => getKeyboardLayout(layoutId), [layoutId]);
  const layoutKeys = useMemo(() => collectLayoutKeys(layout), [layout]);
  const testableCodes = useMemo(() => new Set(layoutKeys.map(({ code }) => code)), [layoutKeys]);

  const resolveLayoutCode = useCallback((event: KeyboardEvent): string | null => {
    if (testableCodes.has(event.code)) return event.code;
    // PrintScreen is inconsistently reported across browsers/OS screenshot hooks.
    if (testableCodes.has('PrintScreen') && resolvePrintScreenCode(event)) return 'PrintScreen';
    return null;
  }, [testableCodes]);

  const registerKeyActivity = useCallback((event: KeyboardEvent) => {
    const layoutCode = resolveLayoutCode(event);
    const displayCode = layoutCode ?? event.code;

    if (!event.repeat) {
      setPressCount((previous) => previous + 1);
      setRecentKeys((previous) => [{
        label: event.key === ' ' ? 'Space' : event.key || displayCode || 'Unknown',
        code: displayCode || 'Unavailable',
        timestamp: event.timeStamp,
      }, ...previous].slice(0, 6));
    }

    if (layoutCode && modifierCodes.has(layoutCode)) {
      setHeldCodes((previous) => new Set(previous).add(layoutCode));
    }

    if (layoutCode) {
      setTestedCodes((previous) => new Set(previous).add(layoutCode));
    }
  }, [resolveLayoutCode]);

  const handleKeyDown = useCallback((event: KeyboardEvent) => {
    blockBrowserAction(event);
    registerKeyActivity(event);
  }, [registerKeyActivity]);

  const handleKeyUp = useCallback((event: KeyboardEvent) => {
    blockBrowserAction(event);
    const layoutCode = resolveLayoutCode(event);

    // PrintScreen / Pause often never emit keydown — count them on keyup instead.
    if (layoutCode && keyUpDetectableCodes.has(layoutCode) && !testedCodesRef.current.has(layoutCode)) {
      registerKeyActivity(event);
    }

    if (layoutCode && modifierCodes.has(layoutCode)) {
      setHeldCodes((previous) => {
        const next = new Set(previous);
        next.delete(layoutCode);
        return next;
      });
    }
  }, [registerKeyActivity, resolveLayoutCode]);

  useEffect(() => {
    const clearHeldKeys = () => setHeldCodes(new Set());
    window.addEventListener('keydown', handleKeyDown, true);
    window.addEventListener('keyup', handleKeyUp, true);
    window.addEventListener('blur', clearHeldKeys);
    return () => {
      window.removeEventListener('keydown', handleKeyDown, true);
      window.removeEventListener('keyup', handleKeyUp, true);
      window.removeEventListener('blur', clearHeldKeys);
    };
  }, [handleKeyDown, handleKeyUp]);

  const totalKeys = testableCodes.size;
  const testedCount = useMemo(
    () => [...testedCodes].filter((code) => testableCodes.has(code)).length,
    [testedCodes, testableCodes],
  );
  const progress = totalKeys > 0 ? Math.round((testedCount / totalKeys) * 100) : 0;

  useEffect(() => {
    if (testedCount >= AUTO_PASS_KEY_COUNT && results.keyboard === 'untested') {
      setResult('keyboard', 'working');
    }
  }, [testedCount, pressCount, results.keyboard, setResult]);

  const isMacLayout = layout.platform === 'mac';
  const modifierState = [
    { label: 'Shift', active: heldCodes.has('ShiftLeft') || heldCodes.has('ShiftRight'), detected: testedCodes.has('ShiftLeft') || testedCodes.has('ShiftRight') },
    { label: isMacLayout ? 'Control' : 'Ctrl', active: heldCodes.has('ControlLeft') || heldCodes.has('ControlRight'), detected: testedCodes.has('ControlLeft') || testedCodes.has('ControlRight') },
    { label: isMacLayout ? 'Option' : 'Alt', active: heldCodes.has('AltLeft') || heldCodes.has('AltRight'), detected: testedCodes.has('AltLeft') || testedCodes.has('AltRight') },
    { label: isMacLayout ? 'Cmd' : 'Win', active: heldCodes.has('MetaLeft') || heldCodes.has('MetaRight'), detected: testedCodes.has('MetaLeft') || testedCodes.has('MetaRight') },
  ];

  const isKeyHeld = (code: string) => heldCodes.has(code);

  const undoTest = () => {
    setTestedCodes(new Set());
    setHeldCodes(new Set());
    setPressCount(0);
    setRecentKeys([]);
  };

  const selectLayout = (nextId: KeyboardLayoutId) => {
    setLayoutId(nextId);
    setHeldCodes(new Set());
  };

  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="test-page mx-auto flex w-full max-w-[90rem] flex-col">
      <TestPageHeader
        testId="T-01"
        testKey="keyboard"
        title="Keyboard"
        description={layout.description}
        canUndoTest={testedCodes.size > 0 || heldCodes.size > 0 || pressCount > 0 || recentKeys.length > 0}
        onUndoTest={undoTest}
      />

      <div className="mb-5 grid items-start gap-4 md:grid-cols-3">
        <Card className="instrument-panel">
          <CardContent className="p-4 sm:p-5">
            <h2 className="panel-label">Modifier activity</h2>
            <div className="mt-3 grid grid-cols-2 gap-2">
              {modifierState.map((modifier) => (
                <div
                  key={modifier.label}
                  className={`metric-tile flex items-center justify-between gap-2 p-3 ${modifier.active || modifier.detected ? 'border-primary/40 bg-primary/5' : ''}`}
                >
                  <span className="font-mono text-[10px] text-muted-foreground">{modifier.label}</span>
                  <span className={`font-mono text-[9px] font-semibold ${modifier.active ? 'text-primary' : modifier.detected ? 'text-status-pass' : 'text-status-idle'}`}>
                    {modifier.active ? 'HELD' : modifier.detected ? 'SEEN' : 'WAIT'}
                  </span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        <Card className="instrument-panel">
          <CardContent className="p-4 sm:p-5">
            <div className="flex items-center justify-between gap-3">
              <h2 className="panel-label">Recent input</h2>
              <span className="font-mono text-[9px] text-muted-foreground">{pressCount} {pressCount === 1 ? 'PRESS' : 'PRESSES'}</span>
            </div>
            <div className="live-readout mt-3 flex min-h-24 flex-col justify-center p-4">
              {recentKeys.length > 0 ? (
                <div className="relative z-10 grid gap-3">
                  <div className="flex items-end justify-between gap-4">
                    <div className="min-w-0">
                      <span className="spec-item mb-1 block">Latest key</span>
                      <div className="readout-value truncate text-2xl">{recentKeys[0].label}</div>
                    </div>
                    <span className="shrink-0 font-mono text-[10px] text-primary">{recentKeys[0].code}</span>
                  </div>
                  <div className="flex min-h-8 items-center gap-2 overflow-hidden border-t border-border/70 pt-3">
                    {recentKeys.slice(1).map((entry) => (
                      <motion.span
                        key={`${entry.code}-${entry.timestamp}`}
                        initial={{ opacity: 0, scale: 0.9 }}
                        animate={{ opacity: 1, scale: 1 }}
                        className="keycap shrink-0 rounded-md px-2 py-1 font-mono text-[10px] text-muted-foreground"
                        title={entry.code}
                      >
                        {entry.label}
                      </motion.span>
                    ))}
                    {recentKeys.length === 1 && <span className="font-mono text-[9px] text-muted-foreground">CONTINUE TYPING TO BUILD HISTORY</span>}
                  </div>
                </div>
              ) : (
                <div className="relative z-10 flex items-center gap-3 text-muted-foreground">
                  <Activity className="h-5 w-5 shrink-0 text-primary/60" />
                  <div><p className="font-mono text-[10px] font-semibold text-foreground">AWAITING INPUT</p><p className="mt-1 text-xs">Recent physical key presses will appear here.</p></div>
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        <Card className="instrument-panel">
          <CardContent className="p-4 sm:p-5">
            <div className="flex items-center justify-between">
              <span className="panel-label">Result</span>
              <TestStatusBadge status={results.keyboard} />
            </div>
            <div className="mt-3 grid grid-cols-2 gap-3">
              <div className="metric-tile p-4">
                <span className="spec-item">Verified</span>
                <div className="readout-value mt-2 text-xl text-primary">{testedCount}</div>
              </div>
              <div className="metric-tile p-4">
                <span className="spec-item">Remaining</span>
                <div className="readout-value mt-2 text-xl">{totalKeys - testedCount}</div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="min-w-0">
        <Card className="instrument-panel min-w-0">
          <CardContent className="p-3 sm:p-5">
            <div className="mb-4 flex flex-col gap-4 sm:mb-5 sm:flex-row sm:items-start sm:justify-between">
              <div className="min-w-0">
                <h2 className="panel-label">Interactive layout</h2>
                <div className="mt-2 flex flex-wrap items-baseline gap-x-3 gap-y-1">
                  <h3 className="font-display text-xl font-semibold tracking-tight text-foreground sm:text-2xl">
                    {layout.name}
                  </h3>
                  <span className="rounded-md border border-primary/25 bg-primary/10 px-2 py-0.5 font-mono text-[10px] font-semibold tracking-wide text-primary">
                    {layout.sizeLabel} · {layout.keyCount} keys
                  </span>
                </div>
              </div>
              <div className="metric-tile flex shrink-0 items-center gap-2 self-start px-3 py-2">
                <Keyboard className="h-4 w-4 text-primary" />
                <span className="readout-value text-xs tabular-nums">{testedCount} / {totalKeys}</span>
              </div>
            </div>

            <div className="mb-4">
              <span className="spec-item mb-2 block">Keyboard type</span>
              <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Keyboard layout">
                {keyboardLayouts.map((preset) => {
                  const selected = preset.id === layoutId;
                  return (
                    <button
                      key={preset.id}
                      type="button"
                      role="radio"
                      aria-checked={selected}
                      onClick={() => selectLayout(preset.id)}
                      className={cn(
                        'metric-tile min-w-[5.75rem] px-3 py-2.5 text-left transition-[border-color,background-color,box-shadow] hover:border-primary/40 hover:bg-primary/5',
                        selected && 'border-primary/65 bg-primary/10 shadow-sm ring-1 ring-primary/20',
                      )}
                    >
                      <span className={cn('block font-mono text-[10px] font-semibold tracking-wide', selected ? 'text-primary' : 'text-foreground')}>
                        {preset.sizeLabel}
                      </span>
                      <span className="mt-0.5 block text-[11px] font-medium text-foreground">{preset.name}</span>
                      <span className={cn('mt-1 block font-mono text-[10px] tabular-nums', selected ? 'text-primary' : 'text-muted-foreground')}>
                        {preset.keyCount} keys
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>

            <div
              className="full-keyboard rounded-xl border border-border/70 bg-secondary/25 p-2 sm:p-3"
              data-layout={layout.columns}
              data-layout-id={layout.id}
              style={{
                '--keyboard-rows': layout.mainRows.length,
                '--keyboard-main-cols': layout.mainColumnCount,
              } as React.CSSProperties}
              aria-label={`${layout.name} keyboard test layout`}
            >
              <div className="keyboard-main-grid">
                {layout.mainRows.map((row, rowIndex) => (
                  <div className="keyboard-main-row" key={rowIndex}>
                    {row.map((slot, slotIndex) => 'spacer' in slot
                      ? <span key={`spacer-${rowIndex}-${slotIndex}`} style={{ gridColumn: `span ${slot.span}` }} aria-hidden="true" />
                      : (
                        <KeyboardKey
                          key={slot.code}
                          definition={slot}
                          tested={testedCodes.has(slot.code)}
                          held={isKeyHeld(slot.code)}
                          style={{ gridColumn: `span ${slot.span || 2}` }}
                        />
                      ))}
                  </div>
                ))}
              </div>

              {layout.navigationRows && (
                <div className="keyboard-navigation-grid">
                  {layout.navigationRows.flatMap((row, rowIndex) => row.map((definition, columnIndex) => definition
                    ? (
                      <KeyboardKey
                        key={definition.code}
                        definition={definition}
                        tested={testedCodes.has(definition.code)}
                        held={isKeyHeld(definition.code)}
                        style={{ gridColumn: columnIndex + 1, gridRow: rowIndex + 1 }}
                      />
                    )
                    : <span key={`nav-spacer-${rowIndex}-${columnIndex}`} style={{ gridColumn: columnIndex + 1, gridRow: rowIndex + 1 }} aria-hidden="true" />))}
                </div>
              )}

              {layout.numpadKeys && (
                <div className="keyboard-numpad-grid">
                  {layout.numpadKeys.map((definition) => (
                    <KeyboardKey
                      key={definition.code}
                      definition={definition}
                      tested={testedCodes.has(definition.code)}
                      held={isKeyHeld(definition.code)}
                      style={{
                        gridColumn: `${definition.column} / span ${definition.columnSpan || 1}`,
                        gridRow: `${definition.row} / span ${definition.rowSpan || 1}`,
                      }}
                    />
                  ))}
                </div>
              )}
            </div>

            <div className="mt-5 flex items-center gap-3 rounded-lg border border-border/60 bg-background/55 px-4 py-3">
              <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-secondary">
                <div className="h-full rounded-full bg-primary transition-[width] duration-300" style={{ width: `${progress}%` }} />
              </div>
              <span className="font-mono text-[10px] font-medium tabular-nums text-muted-foreground">{progress}% VERIFIED</span>
            </div>

            <div className="mt-3 flex items-start gap-2 rounded-lg border border-primary/15 bg-primary/5 px-4 py-3 text-xs leading-relaxed text-muted-foreground">
              <Info className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
              This test listens to physical keyboard events; the on-screen keycaps are read-only. Choose the layout that matches your board so remaining counts stay honest. Dashed keys may be intercepted by the operating system — Print Screen is counted on key release when the browser receives it. Fn is intentionally excluded because laptop firmware usually handles it without sending a browser event.
            </div>
          </CardContent>
        </Card>
      </div>
    </motion.div>
  );
}
