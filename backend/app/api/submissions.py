"""Token-based score submission.

Flow: a seller's draft gets a random `submission_token` + public URL. The
seller shares the URL with a third party (e.g. a technician) who runs the
physical inspection and submits score / drawbacks / condition via the public
page (or API). The backend decides whether the score is "fit to show" and
stores the decision on the listing, which the marketplace surfaces as the
trust/score badge.
"""
import time
import html as _html
from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel
from bson import ObjectId
from fastapi.responses import HTMLResponse

from app.core.db import get_db
from app.core.config import settings

router = APIRouter(tags=["submissions"])

class SubmissionIn(BaseModel):
    score: float | None = None
    drawbacks: list[str] = []
    condition: dict = {}
    condition_summary: str | None = None
    submitter: str | None = None


def _submit_url(token: str) -> str:
    return f"{settings.public_base_url.rstrip('/')}/submit/{token}"


def decide_fit(listing: dict, payload: SubmissionIn) -> tuple[bool, str, str]:
    """Rule-based moderation: is this score safe to show to buyers?"""
    if listing.get("status") in ("blocked", "inactive", "deactivated"):
        return False, "listing_not_active", "This listing is not active."
    if payload.score is None:
        return False, "score_pending", "No score submitted yet."
    if payload.score < 0 or payload.score > 100:
        return False, "score_out_of_range", "Score must be between 0 and 100."
    if payload.score < 40:
        return False, "score_below_threshold", "Score is below the fit-to-show threshold (40/100)."
    if not payload.condition and not payload.condition_summary:
        return False, "condition_missing", "A condition report is required to show a score."
    return True, "ok", "Score accepted — visible to buyers as the trust badge."


def _public_view(doc: dict) -> dict:
    risk = doc.get("risk") or {}
    adjusted = risk.get("adjusted_score") if isinstance(risk, dict) else None
    return {
        "listing": {
            "_id": str(doc.get("_id", "")),
            "title": doc.get("title"),
            "category": doc.get("category"),
            "brand": doc.get("brand"),
            "model": doc.get("model"),
            "price": doc.get("price"),
            "status": doc.get("status"),
            "address": doc.get("address"),
        },
        "scores": {
            "diagnostic_score": doc.get("diagnostic_score"),
            "adjusted_score": adjusted,
        },
        "submission": {
            "is_fit_to_show": doc.get("is_fit_to_show"),
            "score": doc.get("submission_score"),
            "drawbacks": doc.get("submission_drawbacks") or [],
            "condition": doc.get("submission_condition") or {},
            "condition_summary": doc.get("submission_condition_summary"),
            "review_note": doc.get("submission_review_note"),
            "submitter": doc.get("submission_submitter"),
            "updated_at": doc.get("submission_at"),
        },
    }


async def _find_by_token(token: str):
    db = get_db()
    if not token:
        raise HTTPException(status_code=400, detail={"code": "bad_token", "message": "Missing submission token"})
    doc = await db.listings.find_one({"submission_token": token})
    if not doc:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Submission link not found"})
    return doc


@router.get("/p/submit/{token}")
async def get_submission(token: str):
    doc = await _find_by_token(token)
    return _public_view(doc)


@router.post("/p/submit/{token}")
async def post_submission(token: str, payload: SubmissionIn, request: Request):
    from app.core.rate_limit import check_rate_limit
    await check_rate_limit(request, "submission", key=token)
    doc = await _find_by_token(token)
    fit, code, note = decide_fit(doc, payload)
    now = time.time()
    update = {
        "submission_score": payload.score,
        "submission_drawbacks": payload.drawbacks,
        "submission_condition": payload.condition,
        "submission_condition_summary": payload.condition_summary,
        "submission_submitter": payload.submitter,
        "is_fit_to_show": fit,
        "submission_review_note": note,
        "submission_decision_code": code,
        "submission_at": now,
        "updated_at": now,
    }
    db = get_db()
    await db.listings.update_one({"_id": doc["_id"]}, {"$set": update})
    doc.update(update)
    return {"decision": {"is_fit_to_show": fit, "code": code, "note": note}, **_public_view(doc)}


def _page(token: str, view: dict) -> str:
    s = view["scores"] or {}
    sub = view["submission"] or {}
    l = view["listing"] or {}
    fit = sub.get("is_fit_to_show")
    verdict = {
        True: ("fit", "FIT TO SHOW", "Great — this score is visible to buyers as the trust badge."),
        False: ("reject", "NOT FIT TO SHOW", (sub.get("review_note") or "Score withheld from buyers until approved.")),
        None: ("pending", "NOT SUBMITTED YET", "Submit the inspection result to moderate the score for buyers."),
    }[fit]
    price = l.get("price")
    price_str = f"${price:,.0f}" if isinstance(price, (int, float)) else (str(price) or "—")
    cond = sub.get("condition") or {}
    cond_s = sub.get("condition_summary") or cond.get("summary") or ("Not provided" if not cond else "")
    rows = "\n".join(f"<li>{_html.escape(str(d))}</li>" for d in (sub.get("drawbacks") or []))
    score_raw = s.get("diagnostic_score")
    score_txt = str(score_raw) if score_raw is not None else "—"
    from app.api.listings import _trust_rating
    adj = s.get("adjusted_score")
    tr = _trust_rating({"risk": {"adjusted_score": adj}} if adj is not None else {})
    trust_txt = f"Trust {tr['score']}" if tr["score"] is not None else "Trust —"
    return f"""<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>SafeResale · Device score submission</title>
<style>
body{{font-family:-apple-system,'Segoe UI',Roboto,sans-serif;background:#f6f5fa;margin:0;color:#111}}
.card{{max-width:520px;margin:32px auto;background:#fff;border:1px solid #eee;border-radius:18px;padding:24px}}
h1{{font-size:20px;margin:0 0 4px}} .muted{{color:#777;font-size:13px}}
label{{font-weight:600;font-size:13px;display:block;margin:16px 0 6px}}
input,select,textarea{{width:100%;padding:10px;border:1px solid #ddd;border-radius:10px;font-size:14px;box-sizing:border-box}}
button{{width:100%;margin-top:18px;padding:12px;background:#00b2ca;color:#fff;border:0;border-radius:10px;font-size:15px;font-weight:600;cursor:pointer}}
.badge{{display:inline-block;padding:4px 10px;border-radius:999px;font-size:12px;font-weight:700;margin-top:10px}}
.fit{{background:#02ad11;color:#fff}} .reject{{background:#fe0000;color:#fff}} .pending{{background:#ffbb33;color:#fff}}
.box{{background:#f6f5fa;border-radius:12px;padding:14px;margin-top:8px;font-size:14px;color:#333}}
.box b{{color:#111}} ul{{margin:6px 0;padding-left:20px}} li{{font-size:13px}}
</style></head><body>
<div class="card">
  <h1>SafeResale · Device score</h1>
  <p class="muted">This link lets a technician submit the physical-inspection score, drawbacks and condition for a device. The score is moderated before it is shown to buyers.</p>
  <div class="box">
    <b>{_html.escape(str(l.get("title") or "Untitled listing"))}</b>
    <span class="muted"> · {" ".join(filter(None, [_html.escape(str(l.get("category") or "")), _html.escape(str(l.get("brand") or "")), _html.escape(str(l.get("model") or ""))]))}</span>
    <div style="margin-top:6px"><b>{_html.escape(price_str)}</b> · {_html.escape(str(l.get("status") or ""))}</div>
  </div>
  <div class="box"><b>Current state</b><br>
    <span class="badge {verdict[0]}">{verdict[1]}</span>
    <div class="muted" style="margin-top:6px">{_html.escape(verdict[2])}</div>
  </div>
  <div class="box"><b>Score</b>: {score_txt} diagnostic · {trust_txt} trust</div>
  {"<div class='box'><b>Drawbacks</b><ul>" + rows + "</ul></div>" if rows else ""}
  <div class="box"><b>Condition</b>: {_html.escape(str(cond_s))}</div>
  <label>Score (0–100)</label>
  <input id="score" type="number" min="0" max="100" step="1" placeholder="e.g. 86">
  <label>Condition</label>
  <select id="condition">
    <option value="">— none —</option>
    <option>good</option><option>moderate</option><option>poor</option>
  </select>
  <label>Condition summary</label>
  <input id="summary" placeholder="e.g. 'Screen clean, battery healthy, minor scuffs on body'">
  <label>Drawbacks (one per line)</label>
  <textarea id="drawbacks" rows="4" placeholder="e.g.&#10;Battery drops 10% per hour&#10;Back camera has a scratch"></textarea>
  <label>Submitter</label>
  <input id="submitter" placeholder="Inspector name / shop">
  <button onclick="submitIt()">Submit score</button>
  <div id="out" class="box" style="display:none;margin-top:12px"></div>
</div>
<script>
var TOKEN={_html.escape(token, quote=True)};
async function submitIt(){{
  var cond=document.getElementById('condition').value;
  var summary=document.getElementById('summary').value;
  var body={{score:document.getElementById('score').value===''?null:Number(document.getElementById('score').value),
    drawbacks:document.getElementById('drawbacks').value.split('\\n').map(function(s){{return s.trim();}}).filter(Boolean),
    condition:cond?{{'class':cond}}:{{}},
    condition_summary:summary,
    submitter:document.getElementById('submitter').value}};
  try{{
    var r=await fetch('/p/submit/'+TOKEN,{{method:'POST',headers:{{'Content-Type':'application/json'}},body:JSON.stringify(body)}});
    var j=await r.json();
    var d=j.decision||{{}};
    var el=document.getElementById('out');
    el.style.display='block';
    el.style.background=d.is_fit_to_show===true?'rgba(2,173,17,.15)':d.is_fit_to_show===false?'rgba(254,0,0,.1)':'rgba(255,187,51,.15)';
    el.textContent='Verdict: '+(d.is_fit_to_show===true?'FIT TO SHOW ✓':(d.is_fit_to_show===false?'NOT FIT TO SHOW ✗':'NOT DECIDED'))+' — '+(d.note||'');
  }}catch(e){{var el=document.getElementById('out');el.style.display='block';el.style.background='rgba(254,0,0,.1)';el.textContent='Error: '+e.message;}}
}}
</script>
</body></html>"""


@router.get("/submit/{token}")
async def submit_page(token: str):
    doc = await _find_by_token(token)
    return HTMLResponse(content=_page(token, _public_view(doc)))