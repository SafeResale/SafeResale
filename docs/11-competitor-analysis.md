# 11 — Competitor & Trust-Flaw Analysis (OLX, Quikr, et al.)

Version 1.0 • August 2026

## 1. Purpose

Identifies systemic trust/reliability flaws in incumbent second-hand classifieds platforms (OLX India, Quikr, and similar
C2C classifieds) and maps each flaw to a SafeResale design countermeasure. Used to (a) justify product decisions in the
final report/viva, and (b) keep SafeResale differentiated as a *trust engine*, not "another marketplace".

## 2. Documented flaws

| # | Flaw | Evidence | Impact |
|---|---|---|---|
| F1 | **Unverified sellers / fake profiles.** Anyone can create an account and post. No identity, contact, or device verification. | Academic OLX case study: users distrust seller quality; UX teardowns: "fake profiles and user profiles" a top complaint | Buyers cannot tell real sellers from scammers; transactions drift off-platform (cash/in-person) |
| F2 | **No photo authenticity verification.** Listing photos are not provably taken by the seller; stolen, edited, or AI-generated photos are common. | Academic study: "no guarantee that this is the true picture of the product"; Indian Express: fraudsters "lifted photos from genuine listings"; police: photos "edited or enhanced" | Fake/non-existent items listed with convincing imagery; reverse-image search is the only buyer defense |
| F3 | **Advance-payment fraud with no protection.** Scammers ask for token/advance payments (UPI Collect, QR, "UTR mismatch", courier fees) then vanish. | Multiple 2024–2026 police cases (OLX UPI Collect case study, TOI Bengaluru/Gurgaon/Rajkot reports); OLX's own fraud blog describes the advance-payment playbook | Buyers lose money; sellers are also targeted by fake buyers (₹1 UPI calibration, QR-code draining) |
| F4 | **No buyer protection / no escrow.** Platform holds no funds; no structured dispute path. | Marketplace trust research: classifieds lack the "forward-looking protection" (escrow/guarantees) that drives conversion | Buyers bear all risk; disputes are unmediated and slow |
| F5 | **Fraud contact velocity.** Fraudsters contact fresh listings within minutes via scraping bots / alert abuse, then move to WhatsApp. | Zarelva UPI Collect case study: contact within minutes, lead-scraping infrastructure, Telegram distribution | Genuine users are immediately targeted; the platform does not flag it |
| F6 | **Contact info in listing images/ads** (phone numbers embedded in photos) to bypass moderation. | OLX fraud-prevention blog: OCR used to detect phone numbers in images | Direct-contact scams, off-platform leakage |
| F7 | **No explanation for ad rejection/suspension.** Listings get removed without clear reason; support is hard to reach. | UX case studies: "ads getting rejected without any obvious reasons", hidden helpline | User frustration and distrust of the platform |
| F8 | **Weak/absent price sanity.** Unrealistically low prices are the #1 bait but are rarely auto-flagged. | Fraud reports: "very low prices" = scam signal; OLX uses fair-price models but coverage is partial | Below-market bait listings reach buyers |
| F9 | **No image-quality guidance.** Buyers click away from blurry/poorly-lit/cropped photos; sellers get no feedback. | OLX Engineering: 20% fewer clicks when cover image is poor; they built in-app quality scoring | Poor listings underperform for honest sellers |
| F10 | **Ad clutter / monetization pressure.** Overwhelming ads degrade navigation and trust. | Multiple UX teardowns: "too many ads ... making it unusable" | Users leave or ignore the feed; trust erodes |
| F11 | **Inconsistent UX** (icons, photo orientation, spacing, filters, notifications). | UX case studies: inconsistent icons, 62.5% frustrated with sort/filter, poor notification controls | Reduced confidence in the platform |
| F12 | **No structured reviews after verified transactions.** | Marketplace research: fake-review risk; classifieds lack verified-purchase gating | Reputation signals are weak or gameable |

## 3. SafeResale countermeasures (map to requirements)

| Flaw | SafeResale countermeasure | Requirement(s) |
|---|---|---|
| F1 | Email/phone verification, hashed device fingerprint, seller trust tier, reliability signals (response time, listing count) | R-AUTH-01/02/12, R-REPORT-03 |
| F2 | **Live-capture-only 8-angle flow; SHA-256 tamper-evident hash + server-side timestamp; AI-generated-image detection as a pre-publish gate; duplicate perceptual hash** | R-CAPTURE-03/08, R-QUALITY-02, R-VISION-08, R-SEC-06 |
| F3/F4 | Simulated escrow ("SafeResale Protection") + structured dispute workflow with SLA and evidence; platform-mediated, clearly labeled as simulation | R-ESCROW-01..05, R-DISPUTE-01..06 |
| F5 | Contact-velocity feature in anomaly builder; admin flagging; rate-limited sensitive endpoints | R-ANOM-01, R-SEC-05 |
| F6 | Upload policy: no contact info in listing media; OCR phone-number scan flagged (P2-later) | R-SEC-06 |
| F7 | Decision/reason-code transparency; every block/rejection returns a machine-readable + friendly reason; admin review trail | R-DECISION-03, R-EXPLAIN-06, R-ADMIN-04 |
| F8 | Real price-anomaly service (category/brand/model median + deviation ratio) feeding behavioral risk + review | R-ANOM-05 |
| F9 | On-device + server image-quality checks with retake guidance (blur/exposure/glare) | R-CAPTURE-03, R-QUALITY-01 |
| F10/F11 | Trust-first UI: unified verification badge, above-the-fold trust summary, guided listing wizard (no ad clutter in MVP) | R-REPORT-06, R-ADMIN |
| F12 | Deferred post-MVP: reviews gated on verified transactions with two-sided blind reveal | future scope |

## 4. Positioning statement (for viva)

Competitors expose listings with minimal verification and shift all risk to buyers. SafeResale inverts this: the platform
produces **provable evidence before a listing is trusted** — live-captured, hash-sealed photos checked for AI generation,
native device diagnostics, seller/price anomaly detection, and a transparent risk decision — plus a simulated protection
layer (escrow + disputes) that demonstrates the trust infrastructure without handling real money. The differentiator is
not "AI detects damage"; it is an **explainable, multi-signal trust engine** with an auditable evidence trail.
