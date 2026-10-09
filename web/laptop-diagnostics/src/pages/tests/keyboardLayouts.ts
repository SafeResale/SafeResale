export type KeyDefinition = {
  code: string;
  label: string;
  span?: number;
};

export type MainSlot = KeyDefinition | { spacer: true; span: number };
export type PositionedKey = KeyDefinition & {
  column: number;
  row: number;
  columnSpan?: number;
  rowSpan?: number;
};

export type KeyboardLayoutId =
  | 'full'
  | 'tkl'
  | 'compact75'
  | 'compact65'
  | 'sixty'
  | 'forty'
  | 'mac';

export type KeyboardLayout = {
  id: KeyboardLayoutId;
  sizeLabel: string;
  name: string;
  /** Typical marketed key count, e.g. 104. */
  keyCount: number;
  description: string;
  platform: 'windows' | 'mac';
  columns: 'full' | 'tkl' | 'main-only';
  /** Column count for the main alphanumeric grid rows. */
  mainColumnCount: number;
  mainRows: MainSlot[][];
  navigationRows?: Array<Array<KeyDefinition | null>>;
  numpadKeys?: PositionedKey[];
};

const key = (code: string, label: string, span = 2): KeyDefinition => ({ code, label, span });
const spacer = (span: number): MainSlot => ({ spacer: true, span });

const fullMainRows: MainSlot[][] = [
  [key('Escape', 'Esc'), spacer(1), key('F1', 'F1'), key('F2', 'F2'), key('F3', 'F3'), key('F4', 'F4'), spacer(1), key('F5', 'F5'), key('F6', 'F6'), key('F7', 'F7'), key('F8', 'F8'), spacer(1), key('F9', 'F9'), key('F10', 'F10'), key('F11', 'F11'), key('F12', 'F12'), spacer(1)],
  [key('Backquote', '`'), key('Digit1', '1'), key('Digit2', '2'), key('Digit3', '3'), key('Digit4', '4'), key('Digit5', '5'), key('Digit6', '6'), key('Digit7', '7'), key('Digit8', '8'), key('Digit9', '9'), key('Digit0', '0'), key('Minus', '-'), key('Equal', '='), key('Backspace', 'Backspace', 4)],
  [key('Tab', 'Tab', 3), key('KeyQ', 'Q'), key('KeyW', 'W'), key('KeyE', 'E'), key('KeyR', 'R'), key('KeyT', 'T'), key('KeyY', 'Y'), key('KeyU', 'U'), key('KeyI', 'I'), key('KeyO', 'O'), key('KeyP', 'P'), key('BracketLeft', '['), key('BracketRight', ']'), key('Backslash', '\\', 3)],
  [key('CapsLock', 'Caps', 4), key('KeyA', 'A'), key('KeyS', 'S'), key('KeyD', 'D'), key('KeyF', 'F'), key('KeyG', 'G'), key('KeyH', 'H'), key('KeyJ', 'J'), key('KeyK', 'K'), key('KeyL', 'L'), key('Semicolon', ';'), key('Quote', "'"), key('Enter', 'Enter', 4)],
  [key('ShiftLeft', 'Shift', 5), key('KeyZ', 'Z'), key('KeyX', 'X'), key('KeyC', 'C'), key('KeyV', 'V'), key('KeyB', 'B'), key('KeyN', 'N'), key('KeyM', 'M'), key('Comma', ','), key('Period', '.'), key('Slash', '/'), key('ShiftRight', 'Shift', 5)],
  [key('ControlLeft', 'Ctrl', 3), key('MetaLeft', 'Win', 3), key('AltLeft', 'Alt', 3), key('Space', 'Space', 11), key('AltRight', 'Alt', 3), key('MetaRight', 'Win', 3), key('ContextMenu', 'Menu', 2), key('ControlRight', 'Ctrl', 2)],
];

const navigationRows: Array<Array<KeyDefinition | null>> = [
  [key('PrintScreen', 'PrtSc'), key('ScrollLock', 'ScrLk'), key('Pause', 'Pause')],
  [null, null, null],
  [key('Insert', 'Ins'), key('Home', 'Home'), key('PageUp', 'PgUp')],
  [key('Delete', 'Del'), key('End', 'End'), key('PageDown', 'PgDn')],
  [null, key('ArrowUp', '\u2191'), null],
  [key('ArrowLeft', '\u2190'), key('ArrowDown', '\u2193'), key('ArrowRight', '\u2192')],
];

const numpadKeys: PositionedKey[] = [
  { ...key('NumLock', 'Num'), column: 1, row: 2 },
  { ...key('NumpadDivide', '/'), column: 2, row: 2 },
  { ...key('NumpadMultiply', '*'), column: 3, row: 2 },
  { ...key('NumpadSubtract', '-'), column: 4, row: 2 },
  { ...key('Numpad7', '7'), column: 1, row: 3 },
  { ...key('Numpad8', '8'), column: 2, row: 3 },
  { ...key('Numpad9', '9'), column: 3, row: 3 },
  { ...key('NumpadAdd', '+'), column: 4, row: 3, rowSpan: 2 },
  { ...key('Numpad4', '4'), column: 1, row: 4 },
  { ...key('Numpad5', '5'), column: 2, row: 4 },
  { ...key('Numpad6', '6'), column: 3, row: 4 },
  { ...key('Numpad1', '1'), column: 1, row: 5 },
  { ...key('Numpad2', '2'), column: 2, row: 5 },
  { ...key('Numpad3', '3'), column: 3, row: 5 },
  { ...key('NumpadEnter', 'Enter'), column: 4, row: 5, rowSpan: 2 },
  { ...key('Numpad0', '0'), column: 1, row: 6, columnSpan: 2 },
  { ...key('NumpadDecimal', '.'), column: 3, row: 6 },
];

/** 75%: function row + alphas + compacted nav/arrows (no section gaps). */
const compact75MainRows: MainSlot[][] = [
  [key('Escape', 'Esc'), key('F1', 'F1'), key('F2', 'F2'), key('F3', 'F3'), key('F4', 'F4'), key('F5', 'F5'), key('F6', 'F6'), key('F7', 'F7'), key('F8', 'F8'), key('F9', 'F9'), key('F10', 'F10'), key('F11', 'F11'), key('F12', 'F12'), key('Delete', 'Del', 4)],
  [key('Backquote', '`'), key('Digit1', '1'), key('Digit2', '2'), key('Digit3', '3'), key('Digit4', '4'), key('Digit5', '5'), key('Digit6', '6'), key('Digit7', '7'), key('Digit8', '8'), key('Digit9', '9'), key('Digit0', '0'), key('Minus', '-'), key('Equal', '='), key('Backspace', 'Bksp', 2), key('Home', 'Home', 2)],
  [key('Tab', 'Tab', 2), key('KeyQ', 'Q'), key('KeyW', 'W'), key('KeyE', 'E'), key('KeyR', 'R'), key('KeyT', 'T'), key('KeyY', 'Y'), key('KeyU', 'U'), key('KeyI', 'I'), key('KeyO', 'O'), key('KeyP', 'P'), key('BracketLeft', '['), key('BracketRight', ']'), key('Backslash', '\\'), key('PageUp', 'PgUp', 2)],
  [key('CapsLock', 'Caps', 4), key('KeyA', 'A'), key('KeyS', 'S'), key('KeyD', 'D'), key('KeyF', 'F'), key('KeyG', 'G'), key('KeyH', 'H'), key('KeyJ', 'J'), key('KeyK', 'K'), key('KeyL', 'L'), key('Semicolon', ';'), key('Quote', "'"), key('Enter', 'Enter', 2), key('PageDown', 'PgDn', 2)],
  [key('ShiftLeft', 'Shift', 5), key('KeyZ', 'Z'), key('KeyX', 'X'), key('KeyC', 'C'), key('KeyV', 'V'), key('KeyB', 'B'), key('KeyN', 'N'), key('KeyM', 'M'), key('Comma', ','), key('Period', '.'), key('Slash', '/'), key('ShiftRight', 'Shift', 3), key('ArrowUp', '\u2191', 2)],
  [key('ControlLeft', 'Ctrl', 3), key('MetaLeft', 'Win', 2), key('AltLeft', 'Alt', 2), key('Space', 'Space', 11), key('AltRight', 'Alt', 2), key('MetaRight', 'Win', 2), key('ControlRight', 'Ctrl', 2), key('ArrowLeft', '\u2190'), key('ArrowDown', '\u2193'), key('ArrowRight', '\u2192')],
];

/** 65%: no function row; arrows + thin nav column on the right. */
const compact65MainRows: MainSlot[][] = [
  [key('Escape', 'Esc'), key('Digit1', '1'), key('Digit2', '2'), key('Digit3', '3'), key('Digit4', '4'), key('Digit5', '5'), key('Digit6', '6'), key('Digit7', '7'), key('Digit8', '8'), key('Digit9', '9'), key('Digit0', '0'), key('Minus', '-'), key('Equal', '='), key('Backspace', 'Bksp', 2), key('Home', 'Home', 2)],
  [key('Tab', 'Tab', 2), key('KeyQ', 'Q'), key('KeyW', 'W'), key('KeyE', 'E'), key('KeyR', 'R'), key('KeyT', 'T'), key('KeyY', 'Y'), key('KeyU', 'U'), key('KeyI', 'I'), key('KeyO', 'O'), key('KeyP', 'P'), key('BracketLeft', '['), key('BracketRight', ']'), key('Backslash', '\\'), key('PageUp', 'PgUp', 2)],
  [key('CapsLock', 'Caps', 4), key('KeyA', 'A'), key('KeyS', 'S'), key('KeyD', 'D'), key('KeyF', 'F'), key('KeyG', 'G'), key('KeyH', 'H'), key('KeyJ', 'J'), key('KeyK', 'K'), key('KeyL', 'L'), key('Semicolon', ';'), key('Quote', "'"), key('Enter', 'Enter', 2), key('PageDown', 'PgDn', 2)],
  [key('ShiftLeft', 'Shift', 5), key('KeyZ', 'Z'), key('KeyX', 'X'), key('KeyC', 'C'), key('KeyV', 'V'), key('KeyB', 'B'), key('KeyN', 'N'), key('KeyM', 'M'), key('Comma', ','), key('Period', '.'), key('Slash', '/'), key('ShiftRight', 'Shift', 3), key('End', 'End', 2)],
  [key('ControlLeft', 'Ctrl', 3), key('MetaLeft', 'Win', 2), key('AltLeft', 'Alt', 2), key('Space', 'Space', 11), key('AltRight', 'Alt', 2), key('MetaRight', 'Win', 2), key('ArrowLeft', '\u2190'), key('ArrowDown', '\u2193'), key('ArrowRight', '\u2192'), key('ArrowUp', '\u2191')],
];

/** 60%: alphanumeric block only. */
const sixtyMainRows: MainSlot[][] = [
  [key('Escape', 'Esc'), key('Digit1', '1'), key('Digit2', '2'), key('Digit3', '3'), key('Digit4', '4'), key('Digit5', '5'), key('Digit6', '6'), key('Digit7', '7'), key('Digit8', '8'), key('Digit9', '9'), key('Digit0', '0'), key('Minus', '-'), key('Equal', '='), key('Backspace', 'Backspace', 4)],
  [key('Tab', 'Tab', 3), key('KeyQ', 'Q'), key('KeyW', 'W'), key('KeyE', 'E'), key('KeyR', 'R'), key('KeyT', 'T'), key('KeyY', 'Y'), key('KeyU', 'U'), key('KeyI', 'I'), key('KeyO', 'O'), key('KeyP', 'P'), key('BracketLeft', '['), key('BracketRight', ']'), key('Backslash', '\\', 3)],
  [key('CapsLock', 'Caps', 4), key('KeyA', 'A'), key('KeyS', 'S'), key('KeyD', 'D'), key('KeyF', 'F'), key('KeyG', 'G'), key('KeyH', 'H'), key('KeyJ', 'J'), key('KeyK', 'K'), key('KeyL', 'L'), key('Semicolon', ';'), key('Quote', "'"), key('Enter', 'Enter', 4)],
  [key('ShiftLeft', 'Shift', 5), key('KeyZ', 'Z'), key('KeyX', 'X'), key('KeyC', 'C'), key('KeyV', 'V'), key('KeyB', 'B'), key('KeyN', 'N'), key('KeyM', 'M'), key('Comma', ','), key('Period', '.'), key('Slash', '/'), key('ShiftRight', 'Shift', 5)],
  [key('ControlLeft', 'Ctrl', 3), key('MetaLeft', 'Win', 3), key('AltLeft', 'Alt', 3), key('Space', 'Space', 12), key('AltRight', 'Alt', 3), key('MetaRight', 'Win', 3), key('ControlRight', 'Ctrl', 3)],
];

/**
 * 40% mini / left-hand gaming keypad style.
 * Uses a 20-column grid (see CSS) instead of the full 30-column main block.
 */
const fortyMainRows: MainSlot[][] = [
  [key('Escape', 'Esc'), key('F1', 'F1'), key('F2', 'F2'), key('F3', 'F3'), key('F4', 'F4'), key('Digit1', '1'), key('Digit2', '2'), key('Digit3', '3'), key('Digit4', '4'), key('Digit5', '5')],
  [key('Tab', 'Tab', 2), key('KeyQ', 'Q'), key('KeyW', 'W'), key('KeyE', 'E'), key('KeyR', 'R'), key('KeyT', 'T'), key('KeyY', 'Y'), key('KeyU', 'U'), key('KeyI', 'I'), key('Backspace', 'Bksp', 2)],
  [key('CapsLock', 'Caps', 2), key('KeyA', 'A'), key('KeyS', 'S'), key('KeyD', 'D'), key('KeyF', 'F'), key('KeyG', 'G'), key('KeyH', 'H'), key('KeyJ', 'J'), key('KeyK', 'K'), key('Enter', 'Enter', 2)],
  [key('ShiftLeft', 'Shift', 4), key('KeyZ', 'Z'), key('KeyX', 'X'), key('KeyC', 'C'), key('KeyV', 'V'), key('KeyB', 'B'), key('KeyN', 'N'), key('KeyM', 'M'), spacer(2)],
  [key('ControlLeft', 'Ctrl', 3), key('MetaLeft', 'Win', 3), key('AltLeft', 'Alt', 3), key('Space', 'Space', 11)],
];
const macMainRows: MainSlot[][] = [
  [key('Escape', 'Esc'), spacer(1), key('F1', 'F1'), key('F2', 'F2'), key('F3', 'F3'), key('F4', 'F4'), spacer(1), key('F5', 'F5'), key('F6', 'F6'), key('F7', 'F7'), key('F8', 'F8'), spacer(1), key('F9', 'F9'), key('F10', 'F10'), key('F11', 'F11'), key('F12', 'F12'), spacer(1)],
  [key('Backquote', '`'), key('Digit1', '1'), key('Digit2', '2'), key('Digit3', '3'), key('Digit4', '4'), key('Digit5', '5'), key('Digit6', '6'), key('Digit7', '7'), key('Digit8', '8'), key('Digit9', '9'), key('Digit0', '0'), key('Minus', '-'), key('Equal', '='), key('Backspace', 'Delete', 4)],
  [key('Tab', 'Tab', 3), key('KeyQ', 'Q'), key('KeyW', 'W'), key('KeyE', 'E'), key('KeyR', 'R'), key('KeyT', 'T'), key('KeyY', 'Y'), key('KeyU', 'U'), key('KeyI', 'I'), key('KeyO', 'O'), key('KeyP', 'P'), key('BracketLeft', '['), key('BracketRight', ']'), key('Backslash', '\\', 3)],
  [key('CapsLock', 'Caps', 4), key('KeyA', 'A'), key('KeyS', 'S'), key('KeyD', 'D'), key('KeyF', 'F'), key('KeyG', 'G'), key('KeyH', 'H'), key('KeyJ', 'J'), key('KeyK', 'K'), key('KeyL', 'L'), key('Semicolon', ';'), key('Quote', "'"), key('Enter', 'Return', 4)],
  [key('ShiftLeft', 'Shift', 5), key('KeyZ', 'Z'), key('KeyX', 'X'), key('KeyC', 'C'), key('KeyV', 'V'), key('KeyB', 'B'), key('KeyN', 'N'), key('KeyM', 'M'), key('Comma', ','), key('Period', '.'), key('Slash', '/'), key('ShiftRight', 'Shift', 3), key('ArrowUp', '\u2191', 2)],
  [key('ControlLeft', 'Control', 2), key('AltLeft', 'Option', 2), key('MetaLeft', 'Cmd', 3), key('Space', 'Space', 12), key('MetaRight', 'Cmd', 3), key('AltRight', 'Option', 2), key('ArrowLeft', '\u2190'), key('ArrowDown', '\u2193'), key('ArrowRight', '\u2192')],
];

export const keyboardLayouts: KeyboardLayout[] = [
  {
    id: 'full',
    sizeLabel: '100%',
    name: 'Full Sized',
    keyCount: 104,
    description: 'Standard desktop board with every common section present.',
    platform: 'windows',
    columns: 'full',
    mainColumnCount: 30,
    mainRows: fullMainRows,
    navigationRows,
    numpadKeys,
  },
  {
    id: 'tkl',
    sizeLabel: '80%',
    name: 'Tenkeyless',
    keyCount: 87,
    description: 'Full-size layout with the numpad removed, keeping nav spacing and arrows.',
    platform: 'windows',
    columns: 'tkl',
    mainColumnCount: 30,
    mainRows: fullMainRows,
    navigationRows,
  },
  {
    id: 'compact75',
    sizeLabel: '75%',
    name: 'Compact TKL',
    keyCount: 84,
    description: 'Function row and arrows kept, with sections packed tightly together.',
    platform: 'windows',
    columns: 'main-only',
    mainColumnCount: 30,
    mainRows: compact75MainRows,
  },
  {
    id: 'compact65',
    sizeLabel: '65%',
    name: 'Compact',
    keyCount: 68,
    description: 'No dedicated function row. Arrows and a thin navigation column stay on the right.',
    platform: 'windows',
    columns: 'main-only',
    mainColumnCount: 30,
    mainRows: compact65MainRows,
  },
  {
    id: 'sixty',
    sizeLabel: '60%',
    name: 'Small',
    keyCount: 61,
    description: 'Alphanumeric block only — no function row, arrows, or navigation cluster.',
    platform: 'windows',
    columns: 'main-only',
    mainColumnCount: 30,
    mainRows: sixtyMainRows,
  },
  {
    id: 'forty',
    sizeLabel: '40%',
    name: 'Mini',
    keyCount: 42,
    description: 'Left-hand mini / gaming-keypad cluster for compact one-handed boards.',
    platform: 'windows',
    columns: 'main-only',
    mainColumnCount: 20,
    mainRows: fortyMainRows,
  },
  {
    id: 'mac',
    sizeLabel: 'Mac',
    name: 'Mac Layout',
    keyCount: 76,
    description: 'MacBook / Magic Keyboard style with Control, Option, Command, and arrows.',
    platform: 'mac',
    columns: 'main-only',
    mainColumnCount: 30,
    mainRows: macMainRows,
  },
];

export const defaultKeyboardLayoutId: KeyboardLayoutId = 'full';

export function getKeyboardLayout(id: KeyboardLayoutId): KeyboardLayout {
  return keyboardLayouts.find((layout) => layout.id === id) ?? keyboardLayouts[0];
}

export function collectLayoutKeys(layout: KeyboardLayout): KeyDefinition[] {
  const mainKeys = layout.mainRows.flatMap((row) =>
    row.filter((slot): slot is KeyDefinition => !('spacer' in slot)),
  );
  const navKeys = (layout.navigationRows ?? []).flatMap((row) =>
    row.filter((slot): slot is KeyDefinition => slot !== null),
  );
  return [...mainKeys, ...navKeys, ...(layout.numpadKeys ?? [])];
}
