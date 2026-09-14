export const API = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8000";

export function authHeaders() {
  if (typeof window === "undefined") return {};
  const tok = localStorage.getItem("access_token");
  return tok ? { Authorization: `Bearer ${tok}` } : {};
}

export async function api(path: string, opts: RequestInit = {}) {
  const headers: any = { "Content-Type": "application/json", ...(opts.headers || {}), ...authHeaders() };
  const res = await fetch(`${API}${path}`, { ...opts, headers });
  if (!res.ok) {
    const txt = await res.text();
    throw new Error(`${res.status} ${txt}`);
  }
  return res.json();
}
