# SafeResale Marketplace v2 — Product Requirements & Design Doc

> Status: **Draft for review** · Owner: SafeResale Platform Team · Version: 2.0
> Supersedes and extends: `docs/01-prd.md`, `docs/02-requirements.md`, `docs/04-api-contract.md`, `docs/05-data-model.md`
> Reference for styling/features: **eClassify 2.8.0** (Flutter App v2.8.0, eClassify-Web v2.8.0). We **port the concepts**, never copy code, per `docs/12-laptop-diagnostics-PRD.md`.
> Major change vs v1: **native redesign (Android Jetpack Compose + shadcn/ui admin), full two-sided marketplace, Firebase Identity Platform auth.**

---

## 1. Why we're doing this

SafeResale today is a **single-sided trust engine**: a seller is authenticated, an 8-angle photo capture runs, the CoreV diagnostics bench tests 18 modules, a risk score is computed, and the result is one highlighted phone. There is no way for a buyer to *browse*, *search*, *filter*, *favorite*, *chat*, or *discover* — and no way for the admin to *moderate* or *manage* the catalog. It is a verification tool, not a marketplace.

The user experience also lags the 2026 bar: the Android app is dark-pinned with hand-rolled stylinghistorically, and the admin panel is a 4-page inline-styled App Router shell.

This release closes both gaps in three phases:

1. **Phase 0 — Foundations + Auth:** Shared design system (new SafeResale brand), Firebase Identity Platform authentication (email/password + Google) exchanged for our backend JWT, and the marketplace backend API surface.
2. **Phase 1 — Android marketplace app:** A complete redressed, modern, two-sided marketplace UI — browse, search, filters, listing detail, create/edit ads (with the existing CoreV bench + 8-angle capture wired into it), favorites, chat, notifications, profiles, inbox, and settings — all natively in Kotlin/Jetpack Compose.
3. **Phase 2 — Admin panel:** A full shadcn/ui + Tailwind admin dashboard for moderation, categories, users, listings, audit, and settings.

Monetization (subscriptions, Stripe/Razorpay/PhonePe), a jobs module, AdMob advertising, and a full FCM push pipeline are **explicitly deferred** (see §16).

---

## 2. Goals & non-goals

### Goals
- **G1.** Modern, cohesive, premium UI across Android app and admin panel using one shared design system (SafeResale brand: neon lime + deep navy, dark+light, rounded cards, glass surfaces).
- **G2.** Introduce a complete two-sided marketplace feature set: categories with subcategories + custom fields, public browsing/search/filtering, listing CRUD with full seller inventory, favorites, chat (selling/buying, offers, block/unblock), in-app notifications, seller profiles & reviews.
- **G3.** Authentication via **Firebase Identity Platform** (email/password + Google) on all clients, exchanged server-side for our own JWT — while **preserving the existing email/password backend login as a development fallback** so nothing breaks.
- **G4.** Keep every existing verification feature 100% intact: 8-angle capture + blur gate, CoreV 18-module bench, vision, risk engine, decision, PDF/CSV export.
- **G5.** Admin panel becomes a modern shadcn/ui dashboard matching the reference's breadth.

### Non-goals (this release)
- Merchandising / promotions engine (featured-ads boost) → backlog.
- Subscriptions, packages, payments, bank transfers → backlog (§16).
- Jobs module, resume upload, job applications → backlog.
- AdMob display ads → backlog.
- FCM full push (topic subscribe) → in-app notifications first; FCM later.
- Web storefront (eClassify-Web equivalent) → backlog (Android-first).
- WebSocket real-time chat → polling-based chat first (§5.4).

---

## 3. Personas

| Persona | Needs | Solved by |
|---|---|---|
| **Seller (primary)** | Publish trusted listing with hardware evidence, manage live/sold/expired ads, get notified of offers & chats | Create-ad wizard (incl. bench), My Ads, Inbox, Notifications |
| **Buyer (broadcast)** | Discover / search / filter ads, view detail with verification score, chat & offer, favorite, rate seller | Browse, Search+Filter, Detail, Chat, Favorites, Reviews |
| **Moderator / Admin** | Moderate flagged listings, manage categories, users, audit trail, KPIs | shadcn admin panel |
| **Inspector** | Review verification evidence queue | Admin: flagged queue + detail viewer |

---

## 4. Design System (shared tokens)

One source of truth for Compose (`ui/theme/*`) and admin (Tailwind `globals.css` / tokens).

### 4.1 Color palette (SafeResale brand v2)

| Token | Hex | Use |
|---|---|---|
| `brand` (Neon Lime) | `#BDF64A` | Primary accents, FAB, active nav, CTAs |
| `brand-dark` (Lime Green) | `#91C034` | Pressed/hover primary, small fills |
| `brand-light` (Soft Lime) | `#D9FF7A` | Tinted surfaces, chips, focus ring |
| `ink` (Deep Navy Black) | `#051217` | Dark background / primary text |
| `ink-surface` (Charcoal Navy) | `#101C21` | Dark surface / cards |
| `bg` (Off White) | `#F8FAF7` | Light app background |
| `card` (White) | `#FFFFFF` | Light cards |
| `text-2` (Cool Gray) | `#687277` | Secondary text |
| `border` (Light Gray) | `#E3E8E5` | Borders, dividers |
| `danger` (Red) | `#EF4444` | Errors, risk high, blocked |
| `warning` (Amber) | `#F59E0B` | Warnings, review-needed |
| `info` (Blue) | `#3B82F6` | Info, links, verified seller |
| `success` (Green) | `#22C55E` | Passed, approved, live |

**Theme strategy:** dark-first in the Android app (default) with a first-class light mode toggle and system-follow option; admin panel ships light default with dark-mode toggle (Tailwind `dark:` classes).

### 4.2 Typography
- **Android:** Material 3 type scale + **Space Grotesk** for display/headings, **Inter** for body (bundled via font resource; numbers tabular for scores). Keep the existing zero-dependency-default fallback if fonts aren't bundled.
- **Admin:** Inter (Next/font/google), headings `font-semibold` weight 600–700, tabular-nums for scores.

### 4.3 Radii / elevation / shape
- Rounded scale: `4 / 8 / 12 / 16 / 24 / 32 dp`; default card radius `16dp`, sheets `28dp` top corners (M3 + Admin `rounded-2xl`).
- Cards: `filled` surface variant, subtle border (`0.6alpha`), and optional **glass** variant (blur + 12% white overlay) for hero/dark surfaces.
- Elevation: 0 (flat default) → 1dp shadows for sheets/dialogs only. **Flat + borders, not heavy shadows** (modern 2026 aesthetic).

### 4.4 Spacing & grid
- 4dp base scale (`4,8,12,16,20,24,32`); horizontal screen padding `16dp`; content max-width `480dp` (Android) / `1280px` (admin).

### 4.5 Motion
- Material `easeInOutCubic`, 250–350ms; `Crossfade`/`AnimatedContent` for tabs & nav transitions; `animateContentSize` for expanders; shimmer skeletons on loading; `scaleIn` for dialog/rating dialogs.

### 4.6 Shared components
**Android (Compose, `ui/components/`):**
`SafeButton` (filled/tinted/outlined/text + loading), `SafeTextField` (OutlinedTextField + label/hint/error), `SafeTopBar` (M3 TopAppBar, brand), `BottomNavItem`, `SafeCard`, `StatusChip`, `VerifiedBadge`, `PriceText`, `ListingCard` (+ grid/row variants), `RatingStars`, `EmptyState/LoadingState/ErrorState`, `ShimmerSkeleton`, `ModalBottomSheet` (M3), `CountBadge`, `Avatar`.

**Admin (shadcn/ui):** `Button`, `Input`, `Card`, `Badge`, `Table` (DataTable toolkit), `Dialog`, `Sheet`, `Select`, `Tabs`, `DropdownMenu`, `Skeleton`, `Toast`, `Tooltip`, `Chart` (recharts), plus `StatCard`, `Sidebar(Item)`, `PageHeader`, `FilterBar`.

---

## 5. Authentication — Firebase Identity Platform

### 5.1 Design
- Users authenticate with **Firebase Authentication** (email/password + Google Sign-In) on Android and admin.
- A successful client sign-in yields a **Firebase ID token** (JWT signed by Google's keys). Each client exchanges it for our own backend JWT via the new `POST /auth/firebase` endpoint.
- Backend **verifies the ID token server-side** (RS256 against Google's published JWKS, checks `aud == <project_id>`, `iss == https://securetoken.google.com/<project_id>`, `exp`, `sub`), then **finds-or-creates the SafeResale user** (keyed by Firebase `uid`), records `firebase_uid` + `auth_provider` on the user doc, and issues our existing `access`/`refresh` token pair.
- **Dev fallback preserved:** `POST /auth/login` (email/password → Argon2 verify → JWT) stays fully functional and is the safety net for emulator/admin development until a real Firebase project is provisioned (settings flag `firebase_required`).

### 5.2 Backend contract

`POST /auth/firebase`
```json
{ "id_token": "<firebase-id-token>", "name": "optional" }
```
Response — same shape as `/auth/login`:
```json
{ "access_token", "refresh_token", "user": { "id","email","name","role","verified" } }
```
Errors: `400 invalid_token` (malformed/expired/bad-audience), `503 firebase_unconfigured` (project not configured server-side).

Config (`.env`): `FIREBASE_PROJECT_ID` (+ optional `FIREBASE_JWKS_URL` override for tests).

### 5.3 Android
- Add `firebase-bom`, `firebase-auth`, `play-services-auth`, `kotlinx-coroutines-play-services`.
- FirebaseApp initialized programmatically from `BuildConfig` (`FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_WEB_CLIENT_ID`) — **no google-services plugin dependency** so the build stays green and greenfield (values injected later; guarded with a "Firebase not configured — use dev login" state).
- Email/password: `createUserWithEmailAndPassword` / `signInWithEmailAndPassword`.
- Google: `GoogleSignInOptions.requestIdToken(BuildConfig.FIREBASE_WEB_CLIENT_ID)` → `GoogleAuthProvider.getCredential` → `signInWithCredential`.
- On success: `user.getIdToken(true)` → `POST /auth/firebase` → `TokenStore.save(access,refresh)` → enter app.
- **AuthScreen redesign:** SafeResale brand (lime/navy), brand logo mark, segmented Login · Sign up, email+password fields, "Continue with Google" button, inline OAuth error + progress, and a clearly-labelled **"Use email/password (developer)"** fallback that calls `/auth/login`.

### 5.4 Admin
- New `/login` page: Firebase email/password + "Continue with Google" (Firebase JS SDK) OR a developer fallback form hitting `/auth/login` directly.
- On success: exchange Firebase ID token via `POST /auth/firebase` → store `access_token` in localStorage → route guard redirects to dashboard.
- Admin uses **Firebase REST API** (identitytoolkit) to keep dependencies light; `NEXT_PUBLIC_FIREBASE_*` env vars; graceful dev fallback when unset.

---

## 6. Backend — Marketplace APIs (Phase 1)

New routes under existing FastAPI app; all follow `04-api-contract.md` (envelope `{error, message, data}` conventions, Bearer auth, rate limiting, audit).

### 6.1 Categories tree (`app/api/categories.py`)
- `GET /categories` — nested tree from MongoDB `categories` collection, each node: `{id, name, slug, parent_id, icon, subcategories[], custom_fields[]}`.
- `GET /categories/{id}` — single node + ancestors (breadcrumb).
- Admin: `POST/PATCH/DELETE /admin/categories` (+ reorder, translation fields).
- Seed: `backend/app/seed/seed_categories.py` — mobile, tablets, laptops, TV, audio, accessories, gaming … with subcategories + condition enums + per-category custom fields (RAM, storage, brand, model, screen size, gpu, etc.).

### 6.2 Listings marketplace (`app/api/listings.py` — extended)
- `GET /listings` — public browse: `?category=&subcategory=&search=&min_price=&max_price=&condition=&location=&page=&page_size=&sort=latest|price_asc|price_desc|score` — returns `{items, total, page, page_size, facets}` (facet counts for filters).
- `GET /listings/{id}` — public detail: listing + images + diagnostics summary + seller card + `can_chat`; increments view counter.
- `POST /listings` — create listing (extends existing draft wizard data): title, description, category/subcategory, custom fields, condition, price, location, photos, `run_bench: bool`.
- `PATCH /listings/{id}` (owner) — edit, incl. deactivate / mark sold.
- `GET /listings/{id}/linked-images` + existing images/scores/diagnostics endpoints (kept).
- `GET /users/{id}/listings` — public seller listings.

### 6.3 Favorites (`app/api/favorites.py`)
- `POST /favorites/{listing_id}/toggle`, `GET /favorites` (paginated), `DELETE /favorites/{listing_id}`.

### 6.4 Chat (`app/api/chat.py`) — polling-based (no WebSocket this release)
- `POST /chat/listings/{listing_id}/send` `{message, offer_price?}` — per-listing thread.
- `GET /chat` — conversations list with `selling|buying` grouping + unread counts + last message + item thumbnail.
- `GET /chat/listings/{listing_id}/messages` — message history (paginated).
- `POST /chat/block-user/{user_id}` / `unblock` / `GET /chat/blocked`.
- Message model: `{_id, listing_id, thread_with, sender_id, body, offer_price, type:text|offer|system, read, created_at}`.

### 6.5 Notifications (`app/api/notifications.py`)
- `GET /notifications` (paginated, unread first), `POST /notifications/{id}/read`, `POST /notifications/read-all`.
- Generated server-side on: new message, offer received, listing approved/rejected, listing sold, favorite milestone.

### 6.6 Sellers & reviews (`app/api/sellers.py`)
- `GET /sellers/{id}` — profile: name, avatar, join date, verification badge, rating aggregate, review count.
- `GET /sellers/{id}/reviews`, `POST /listings/{id}/review` (verified buyers only, 1–5 stars + comment + optional photo).
- `POST /listings/{id}/report` — report ad + reason.

### 6.7 Data model additions
`categories`, `listings.transitions` (status history), `listings.views`, `favorites`, `chat_messages`, `chat_threads`, `notifications`, `reviews`, `seller_reports`. New indexes: `listings(category,status,price,created_at)`, `favorites(user_id,listing_id)` unique, `chat_threads(listing_id,thread_with)` unique, `notifications(user_id,created_at)`.

---

## 7. Android App — Screens & Navigation (Phase 1)

### 7.1 App shell
- New **bottom navigation** (`MainActivity`): Home · Explore (marketplace) · **Add (+ FAB)** · Inbox (chat) · Profile. Verified-check + bench remain accessible from Home ("Start a check") and Profile; the 18-module CoreV bench is unchanged but restyled to the new theme.

### 7.2 Screen inventory (new/redesigned)
| Screen | Notes |
|---|---|
| **Onboarding** (new) | 2 slides: "Verify before you buy" / "Hardware truth". Skip + Get Started. |
| **Auth / Login** (redesign) | §5.3 — brand, tabs, Google button, dev fallback. |
| **Home** (redesign) | Search bar, category chips row, "Start a verification check" hero card, featured listings carousel + horizontal scrollers, section headings, trust banner. |
| **Browse / Explore** (new) | Searchable + sortable grid; category/subcategory chips; filter bottom sheet (price range, condition, location, sort); infinite scroll. |
| **Listing detail** (new) | Image gallery + dots, price, condition chip, verified score badge, spec fields grid, description, seller card (avatar, verified, rating) + "Chat" button, related listings, report ad. |
| **New check wizard** (redesign, reuse) | Category select → details (title/price/desc/condition/location) → 8-angle capture (existing CameraX + blur gate, restyled) → optional CoreV bench → publish. |
| **My Ads** (new) | Status tabs (live, sold, expired, review, rejected) + actions: deactivate, mark sold, renew, edit, delete. |
| **Favorites** (new) | Grid of saved listings + remove. |
| **Chat inbox** (new) | Selling/Buying tabs, thread rows (avatar, last msg, unread badge, need action), search. |
| **Chat thread** (new) | Item card header, bubbles (mine/other), offer card w/ accept/reject, text + attachment-free input, block/unblock menu, empty state. |
| **Notifications** (new) | Grouped list, unread highlighting, mark-read, tap-through routing. |
| **Profile** (new) | Avatar, verified badge, name, stats (ads, favorites, rating), menu: My Ads, Favorites, Reviews, Notifications, Settings, About, Sign out. |
| **Settings** (new/moved) | Dark mode toggle, notification prefs, app info, privacy, logout. |
| **All existing bench/score/export screens** (restyle) | Re-theme only; logic untouched. |

---

## 8. Admin Panel — shadcn/ui rebuild (Phase 2)

Modernize the existing `web/admin` App Router app with Tailwind v4 + shadcn/ui.

### 8.1 Shell
- `/` redirect → `/dashboard`; `(auth)` route group w/ `/login`; `(app)` group with sidebar layout; route guard reading `localStorage.access_token` + role; dark/light toggle.

### 8.2 Pages
| Page | Contents |
|---|---|
| `/login` | Firebase email/password + Google (or dev fallback) → JWT. |
| `/dashboard` | Stat cards (listings, pending review, high-risk, approval rate, avg risk), flagged-queue table preview, risk trend chart, quick links. |
| `/queue` | Flagged listings moderation queue: filters (status/risk/category), paginated table, per-row actions (approve / block / request-inspection / review) + reason dialog. |
| `/queue/[id]` | Full listing detail: gallery, diagnosis summary, risk breakdown, decisions history, audit trail, image-by-image evidence. |
| `/listings` | All listings table w/ search + status/risk filters + CSV export. |
| `/categories` | Tree CRUD + custom fields editor + reorder. |
| `/users` | Users table (role, verification, status) + role change + block. |
| `/audit` | Audit log with filters + CSV export. |
| `/models` | ML models + metrics (per existing backend `/admin/models`). |
| `/settings` | KV settings, developer toggles, backend health. |

Reusable: `DataTable`, `FilterBar`, `StatusBadge`, `ConfirmDialog`, `Toast`, `PageHeader`, `EmptyState`, chart cards. Design tokens per §4 mapped to Tailwind.

---

## 9. Release plan & verification

| Phase | Contents | Green-check |
|---|---|---|
| **0** | PRD + design tokens + Firebase auth across backend/Android/admin; marketplace seed + categories endpoint | `pytest`, `assembleDebug`, admin `next build` |
| **1a** | Backend marketplace APIs + tests (§6) | `pytest` green + live curl smoke |
| **1b** | Android shell + browse/detail/create/favorites/chat/notifications/profile | `assembleDebug` + emulator E2E |
| **2** | Admin panel shadcn rebuild (§8) | `next build` + Playwright login/dashboard smoke |

Milestones map to `docs/09-phase-plan.md`; every phase lands with backend tests + Android build + admin build verified.

---

## 10. Risks & mitigations
- **Firebase not provisioned yet** → server-side verifier + programmatic Android init + dev fallback; anyone can develop without credentials. Config hooks documented; one env fill activates Firebase.
- **Scope creep** → explicit §2 non-goals; monetization/jobs/ads/WSS deferred.
- **Backend chat without WSS** → fixed polling interval + incremental fetch (only new messages), adequate for MVP.
- **Breaking existing bench flow** → bench screens are re-themed not rewritten (Phase 1b guardrail); E2E smoke after every change.

## 11. Assumptions
- Firebase project to be created by platform owner; only `FIREBASE_PROJECT_ID` (+ optional Android web client id) required server-side. ID-token JWKS verification requires **no** service-account secret.
- Mongo stays the single backend datastore; no new infra.
- Android continues to target emulator `10.0.2.2:8000`; admin targets `:8000`.

## 12. Success metrics
- Browse flow reachable on device: Home → browse → detail → chat in ≤3 taps.
- New checks publishable from within marketplace wizard.
- Admin moderation of a flagged listing in ≤4 clicks.
- 0 regressions in existing bench/score/risk E2E.

---

*Backlog checklist (deferred): featured-ads/promotions, subscription packages + Stripe/Razorpay/PhonePe/bank-transfer, jobs module, AdMob, FCM push, WebSocket chat, web storefront, seller-verification fields flow port, in-app subscriptions (iOS).*
