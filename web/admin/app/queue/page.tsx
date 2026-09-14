"use client";
import { useEffect, useState } from "react";
import { api } from "../../lib/api";

export default function Queue() {
  const [items, setItems] = useState<any[]>([]);
  const [err, setErr] = useState("");
  useEffect(() => {
    api("/admin/listings/flagged").then(d=>setItems(d.items||[])).catch(e=>setErr(String(e)));
  }, []);
  return (
    <div>
      <h1 style={{ fontSize: 22, fontWeight: 700 }}>Flagged Queue</h1>
      <p style={{ color: "#64748b" }}>Evidence from verification orchestrator — vision + diagnostics + anomaly → risk → decision</p>
      {err && <pre style={{ color: "red" }}>{err} — create an admin user and set Authorization header (see README)</pre>}
      <table style={{ width: "100%", marginTop: 16, borderCollapse: "collapse", background: "white", border: "1px solid #e2e8f0" }}>
        <thead><tr style={{ background: "#f1f5f9" }}><th style={{ padding: 8, textAlign: "left" }}>Listing</th><th>Risk</th><th>Status</th></tr></thead>
        <tbody>
          {items.length===0 ? <tr><td colSpan={3} style={{ padding: 16, color: "#64748b" }}>No flagged listings yet — run POST /listings/{`{id}`}/compute-risk on a draft.</td></tr> :
          items.map((it:any)=><tr key={it._id} style={{ borderTop: "1px solid #e2e8f0" }}><td style={{ padding: 8 }}>{it.title || it._id}</td><td style={{ padding: 8 }}>{it.risk ?? "-"}</td><td style={{ padding: 8 }}>{it.status}</td></tr>)}
        </tbody>
      </table>
    </div>
  );
}
