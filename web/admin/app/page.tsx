"use client";
import { useEffect, useState } from "react";
import { api } from "../lib/api";

export default function Page() {
  const API = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8001";
  const [health, setHealth] = useState<any>(null);
  const [kpis, setKpis] = useState<any>(null);
  const [err, setErr] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch(`${API}/health`).then(r=>r.json()).then(setHealth).catch(()=>setHealth({status:"down", db:"unknown"}));
    // try kpis with auth if token exists
    const tok = typeof window !== "undefined" ? localStorage.getItem("access_token") : null;
    if (tok) {
      api("/admin/kpis").then(setKpis).catch(e=>setErr(String(e))).finally(()=>setLoading(false));
    } else {
      setLoading(false);
      setErr("No admin token — login via POST /auth/login (admin@example.com / Admin123!) then store access_token in localStorage");
    }
  }, []);

  if (loading) return <p>Loading KPIs...</p>;

  return (
    <div>
      <h1 style={{ fontSize: 28, fontWeight: 700 }}>Admin Dashboard — KPIs</h1>
      <div style={{ background: health?.status==="ok" ? "#dcfce7" : "#fef2f2", border: `1px solid ${health?.status==="ok"?"#86efac":"#fecaca"}`, borderRadius: 8, padding: 12, marginTop: 12 }}>
        <strong>Backend {API}</strong> — Health: <strong>{health ? health.status : "loading..."}</strong> ({health?.db} / {health?.cache} / {health?.storage} / {health?.vision_provider})
        {health?.status!=="ok" && <span style={{ color: "#dc2626" }}> — check backend is running on 8001 (see 10-setup-guide.md)</span>}
      </div>
      {err && <pre style={{ background: "#fef2f2", border: "1px solid #fecaca", padding: 12, borderRadius: 8, marginTop: 12, whiteSpace: "pre-wrap" }}>{err}</pre>}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 16, marginTop: 24 }}>
        {[
          ["Total listings", kpis?.total_listings ?? "—"],
          ["Pending review", kpis?.pending_review ?? "—"],
          ["High-risk (blocked)", kpis?.high_risk ?? "—"],
          ["Approval rate", kpis ? `${kpis.approval_rate}%` : "—"],
          ["Avg risk", kpis?.avg_risk ?? "—"],
          ["Model", kpis?.model_confidence ? `${(kpis.model_confidence*100).toFixed(0)}%` : (health?.vision_provider || "stub")],
        ].map(([k,v])=>(
          <div key={k} style={{ background: "white", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
            <div style={{ color: "#64748b", fontSize: 13 }}>{k}</div>
            <div style={{ fontSize: 22, fontWeight: 700 }}>{String(v)}</div>
          </div>
        ))}
      </div>
      <div style={{ marginTop: 16, display: "flex", gap: 12 }}>
        <a href="/queue" style={{ background: "#0f172a", color: "white", padding: "8px 16px", borderRadius: 8, textDecoration: "none" }}>Flagged Queue →</a>
        <a href="/admin/audit" style={{ background: "white", border: "1px solid #e2e8f0", padding: "8px 16px", borderRadius: 8, textDecoration: "none" }}>Audit Logs</a>
      </div>
      <div style={{ marginTop: 16, background: "#f1f5f9", border: "1px solid #e2e8f0", borderRadius: 8, padding: 12 }}>
        <strong>Tamper-evidence:</strong> Every image sealed with SHA256 + server timestamp on <code>POST confirm-upload</code>. Edits invalidate hash — visible on report badge.
        <br/><strong>Hard stops:</strong> missing angles / duplicate hash / critical water_damage / invalid price/year → auto-blocked (R-DECISION-02).
      </div>
    </div>
  );
}
