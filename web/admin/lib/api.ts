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
  localStorage.removeItem("access_token");
  localStorage.removeItem("refresh_token");
  localStorage.removeItem("admin_user");
  if (!silent && !window.location.pathname.startsWith("/login")) window.location.href = "/login";
}

async function tryRefresh(): Promise<boolean> {
  const rt = token("refresh_token");
  if (!rt) return false;
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