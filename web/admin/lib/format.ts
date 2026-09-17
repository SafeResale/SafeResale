import type { RiskBand } from "./types";

export function money(v?: number | null, currency?: string | null) {
  if (v === undefined || v === null || isNaN(Number(v))) return "—";
  const cur = currency || "USD";
  try {
    return new Intl.NumberFormat("en-US", { style: "currency", currency: cur, maximumFractionDigits: 2 }).format(Number(v));
  } catch {
    return `$${Number(v).toFixed(2)}`;
  }
}

export function fmtNumber(v?: number | null) {
  if (v === undefined || v === null || isNaN(Number(v))) return "—";
  return new Intl.NumberFormat("en-US").format(Number(v));
}

export function fmtDate(ts?: number | null, opts: Intl.DateTimeFormatOptions = {}) {
  if (!ts) return "—";
  return new Date(ts * 1000).toLocaleString("en-US", { month: "short", day: "numeric", year: "numeric", hour: "2-digit", minute: "2-digit", ...opts });
}

export function fmtShort(ts?: number | null) {
  if (!ts) return "—";
  return new Date(ts * 1000).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

export function timeAgo(ts?: number | null) {
  if (!ts) return "—";
  const s = Math.floor(Date.now() / 1000 - ts);
  if (s < 60) return "just now";
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  const d = Math.floor(h / 24);
  if (d < 30) return `${d}d ago`;
  return fmtShort(ts);
}

export function riskBandColor(band?: RiskBand | null): string {
  switch (band) {
    case "low":
      return "success";
    case "medium":
      return "warning";
    case "high":
      return "destructive";
    default:
      return "secondary";
  }
}

export function riskLabel(score?: number | null): { band: RiskBand | null; label: string } {
  if (score === undefined || score === null || isNaN(Number(score))) return { band: null, label: "—" };
  const n = Number(score);
  const band: RiskBand = n <= 30 ? "low" : n <= 60 ? "medium" : "high";
  return { band, label: `${n.toFixed(0)} / 100` };
}

export function capitalize(s?: string | null) {
  if (!s) return "—";
  return s.replace(/(^|[-_\s])(\w)/g, (_, sep: string, c: string) => (sep ? " " : "") + c.toUpperCase());
}

export function initials(name?: string | null, email?: string | null) {
  const src = name || email || "?";
  return src
    .split(/[\s@._-]/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join("");
}

export function pct(n: number, total: number) {
  if (!total) return 0;
  return Math.round((n / total) * 100);
}