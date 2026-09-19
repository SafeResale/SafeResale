export const API = process.env.NEXT_PUBLIC_API_BASE_URL || "";

export class ApiError extends Error {
  status: number;
  code?: string;
  constructor(status: number, message: string, code?: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

type ApiOpts = RequestInit & { skipAuth?: boolean };

function token(key: string) {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(key);
}

// ── JWT expiry parsing ──────────────────────────────────────────────

function parseJwtExp(jwt: string): number | null {
  try {
    const payload = jwt.split(".")[1];
    if (!payload) return null;
    const decoded = JSON.parse(atob(payload));
    return typeof decoded.exp === "number" ? decoded.exp : null;
  } catch {
    return null;
  }
}

// ── Proactive refresh timer ─────────────────────────────────────────

let refreshTimer: ReturnType<typeof setTimeout> | null = null;
const REFRESH_BUFFER_MS = 60_000; // refresh 60 s before expiry
const LOCK_KEY = "sr_refresh_lock";
const LOCK_TTL_MS = 10_000;

function cancelRefreshTimer() {
  if (refreshTimer) {
    clearTimeout(refreshTimer);
    refreshTimer = null;
  }
}

function scheduleRefresh() {
  cancelRefreshTimer();
  const at = token("access_token");
  if (!at) return;
  const exp = parseJwtExp(at);
  if (!exp) return;
  const msUntilRefresh = exp * 1000 - Date.now() - REFRESH_BUFFER_MS;
  if (msUntilRefresh <= 0) {
    // Token already expired or about to — refresh now
    tryRefresh().then((ok) => {
      if (ok) scheduleRefresh();
    });
    return;
  }
  refreshTimer = setTimeout(() => {
    tryRefresh().then((ok) => {
      if (ok) scheduleRefresh();
    });
  }, msUntilRefresh);
}

/** Returns true if this tab acquired the lock (another tab is not already refreshing). */
function acquireRefreshLock(): boolean {
  if (typeof window === "undefined") return false;
  const now = Date.now();
  const raw = localStorage.getItem(LOCK_KEY);
  if (raw) {
    const lockTime = parseInt(raw, 10);
    if (!isNaN(lockTime) && now - lockTime < LOCK_TTL_MS) {
      return false; // another tab holds the lock
    }
  }
  localStorage.setItem(LOCK_KEY, String(now));
  return true;
}

function releaseRefreshLock() {
  if (typeof window === "undefined") return;
  localStorage.removeItem(LOCK_KEY);
}

// ── Start / stop auth watch ─────────────────────────────────────────

let _watching = false;

export function startAuthWatch() {
  if (_watching || typeof window === "undefined") return;
  _watching = true;
  scheduleRefresh();

  // Re-schedule when tokens change (e.g. after login)
  const orig = window.localStorage.setItem.bind(localStorage);
  window.localStorage.setItem = (key: string, value: string) => {
    orig(key, value);
    if (key === "access_token") scheduleRefresh();
  };
}

export function stopAuthWatch() {
  cancelRefreshTimer();
  _watching = false;
}

// ── API helpers ─────────────────────────────────────────────────────

export async function parseError(res: Response): Promise<ApiError> {
  let status = res.status;
  let message = `${status} ${res.statusText}`;
  let code: string | undefined;
  try {
    const data = await res.json();
    const detail = data?.detail;
    if (typeof detail === "string") {
      message = detail;
    } else if (detail && typeof detail === "object") {
      message = detail.message || message;
      code = detail.code;
    } else if (typeof data?.message === "string") {
      message = data.message;
    }
  } catch {
    /* non-json body */
  }
  return new ApiError(status, message, code);
}

export async function api<T = any>(path: string, opts: ApiOpts = {}): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...((opts.headers as Record<string, string>) || {}),
  };
  const tok = token("access_token");
  if (!opts.skipAuth && tok) headers.Authorization = `Bearer ${tok}`;

  let res = await fetch(`${API}${path}`, { ...opts, headers });

  if (res.status === 401 && opts.skipAuth === undefined) {
    const refreshed = await tryRefresh();
    if (refreshed) {
      const retry = { ...opts, headers: { ...headers, Authorization: `Bearer ${token("access_token")}` } };
      res = await fetch(`${API}${path}`, retry);
    }
  }

  if (res.status === 401) {
    clearSession(window.location.pathname.startsWith("/login"));
    throw new ApiError(401, "Session expired — please sign in again", "session_expired");
  }
  if (!res.ok) throw await parseError(res);

  if (res.status === 204) return undefined as T;
  const ct = res.headers.get("content-type") || "";
  if (!ct.includes("application/json")) return (await res.text()) as unknown as T;
  return res.json();
}

export function authHeaders(): Record<string, string> {
  const t = token("access_token");
  return t ? { Authorization: `Bearer ${t}` } : {};
}

export const get = <T = any>(path: string, opts?: ApiOpts) => api<T>(path, { ...opts, method: "GET" });
export const post = <T = any>(path: string, body?: any, opts?: ApiOpts) =>
  api<T>(path, { ...opts, method: "POST", body: body === undefined ? undefined : JSON.stringify(body) });
export const patch = <T = any>(path: string, body?: any, opts?: ApiOpts) =>
  api<T>(path, { ...opts, method: "PATCH", body: body === undefined ? undefined : JSON.stringify(body) });
export const put = <T = any>(path: string, body?: any, opts?: ApiOpts) =>
  api<T>(path, { ...opts, method: "PUT", body: body === undefined ? undefined : JSON.stringify(body) });
export const del = <T = any>(path: string, opts?: ApiOpts) => api<T>(path, { ...opts, method: "DELETE" });

export function clearSession(silent = false) {
  if (typeof window === "undefined") return;
  cancelRefreshTimer();
  releaseRefreshLock();
  localStorage.removeItem("access_token");
  localStorage.removeItem("refresh_token");
  localStorage.removeItem("admin_user");
  if (!silent && !window.location.pathname.startsWith("/login")) window.location.href = "/login";
}

async function tryRefresh(): Promise<boolean> {
  const rt = token("refresh_token");
  if (!rt) return false;

  // Tab-lock: prevent concurrent refreshes across tabs
  if (!acquireRefreshLock()) {
    // Another tab is refreshing — wait briefly, then check if a new token appeared
    const prevTok = token("access_token");
    await new Promise((r) => setTimeout(r, 500));
    const newTok = token("access_token");
    return !!newTok && newTok !== prevTok;
  }

  try {
    const res = await fetch(`${API}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refresh_token: rt }),
    });
    if (!res.ok) return false;
    const d = await res.json();
    if (!d.access_token) return false;
    localStorage.setItem("access_token", d.access_token);
    if (d.refresh_token) localStorage.setItem("refresh_token", d.refresh_token);
    if (d.user) localStorage.setItem("admin_user", JSON.stringify(d.user));
    return true;
  } catch {
    return false;
  } finally {
    releaseRefreshLock();
  }
}

export function storeTokens(d: any) {
  if (!d.access_token) return;
  localStorage.setItem("access_token", d.access_token);
  if (d.refresh_token) localStorage.setItem("refresh_token", d.refresh_token);
  if (d.user) localStorage.setItem("admin_user", JSON.stringify(d.user));
  else if (!d.user && !localStorage.getItem("admin_user") && d.email) {
    localStorage.setItem("admin_user", JSON.stringify({ email: d.email }));
  }
}

export function getSessionUser(): { name?: string; email?: string; role?: string } | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem("admin_user");
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export async function exchangeIdToken(idToken: string) {
  const d = await post("/auth/firebase", { id_token: idToken });
  storeTokens(d);
  return d;
}

export function queryString(params: Record<string, string | number | boolean | undefined | null>) {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== "") sp.set(k, String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : "";
}