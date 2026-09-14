"use client";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { api } from "../../../lib/api";

export default function ListingDetail() {
  const { id } = useParams() as { id: string };
  const [data, setData] = useState<any>(null);
  const [err, setErr] = useState("");
  useEffect(() => {
    api(`/admin/listings/${id}`).then(setData).catch(e=>setErr(String(e)));
  }, [id]);
  if (err) return <pre style={{ color: "red" }}>{err}</pre>;
  if (!data) return <p>Loading {id}...</p>;
  return (
    <div>
      <h1 style={{ fontSize: 22, fontWeight: 700 }}>Listing {id}</h1>
      <pre style={{ background: "white", border: "1px solid #e2e8f0", borderRadius: 8, padding: 12, overflow: "auto" }}>{JSON.stringify(data, null, 2)}</pre>
      <p style={{ color: "#64748b" }}>Annotated images, condition probs, diagnostics, anomaly factors, risk history, audit trail — persisted in detections/condition_predictions/risk_scores/decisions per 05-data-model.md</p>
    </div>
  );
}
