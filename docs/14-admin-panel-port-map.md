# SafeResale Admin Panel — Port Scope Decision (from eclassify "Admin panel 2.8.0" reference)

Reference ground-truthed (byte-exact, Laravel + spatie/laravel-permission +
dacoto wizard installer + 6 payment bridges: Stripe, Paystack, Razorpay, PhonePe,
Flutterwave, PayPal — routes/web.php + composer.json verified).

## Decision (2026)
Do NOT port from the reference admin: spatie/permission role tree, the
fresh-install wizard (purchase-code/php-function install steps), or the 6 payment
remittance bridges. All three conflict with SafeResale v2 constraints:
- RBAC fallback: ours keeps a single `role` field on the user + Firebase admin
  allowlist (`firebase_admin_emails` in backend config) — one admin, no role graph.
- Wizard installer: ours ships `docs/10-setup-guide.md` + backend seed. N/A.
- Payment bridges: monetization is deferred in the PRD; bridges move real money,
  which the trust-engine epoch is explicitly NOT doing.

## Feature ADDED instead: Escrow (platform wallet, seller-only hold)
Model chosen by product owner: SafeResale holds the transacted amount in a
platform escrow wallet between `buyer pays` and `buyer confirms received`.
No payment gateway in v2. Internal-ledger-only. Release/refund are pure ledger
moves.

State machine (escrow lifecycle):
1. `pending`     — order placed, amount reserved, not yet funded.
2. `held`        — buyer confirms receipt of item; funds move buyer->platform
                   escrow wallet (internal ledger entry, no real move).
3. `in_review`   — dispute flagged OR admin verification requested; frozen.
4. `released`    — seller receives funds (ledger) after buyer confirmation / admin
                   verification; terminal OK state.
5. `refunded`    — buyer receives refund on dispute adjudicated for buyer; terminal.

Admin panel surfaces (ours, added):
- `web/admin/app/escrow/` — list all escrows (id, listing, buyer, seller, amount,
  status, timestamps) + `[id]` detail with lifecycle timeline.
- Filters: by status (`pending|held|in_review|released|refunded`) and by listing.
- Actions (state transitions, backend-enforced): mark held, escalate to review,
  release to seller, refund to buyer. Each transition validates current state +
  writes an `EscrowTransition` audit row for the audit trail.
- KPI widget: escrow-in-hold total (sum of `held` + `in_review` amounts) on the
  admin dashboard.

Backend surfaces:
- Model `Escrow` (+ `EscrowTransition`), protected by admin auth (`/auth/firebase`).
- Routes (admin-scoped): `GET /admin/escrows`, `GET /admin/escrows/{id}`,
  `POST /admin/escrows/{id}/hold`, `/{id}/review`, `/{id}/release`, `/{id}/refund`.
- Hermetic tests: full lifecycle `pending -> held -> released` and
  `pending -> held -> in_review -> refunded`, plus invalid-transition rejection
  (e.g. `held -> refunded` without review) and admin-auth guard.

## Carried forward unchanged (ours, already green)
Firebase login (`/auth/firebase`, email/Google -> JWT), marketplace v2
verification trust engine, KPI/queue/audit admin surfaces, SafeResale brand,
PRD feature set. Android Firebase wiring remains queued after this admin slice.
