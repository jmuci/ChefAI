# Architecture Decision Records

All ADRs live in [`docs/adrs/`](../adrs/). This file is a quick-reference index.

## Active Decisions

| # | Decision | Date | Key Takeaway |
|---|----------|------|--------------|
| 001 | [Hybrid Architecture](../adrs/adr-001-hybrid-architecture-choice.md) | Nov 2025 | Google Modern Android + selective Clean Architecture. Domain layer optional. |
| 002 | [Local DB & ID Generation](../adrs/adr-002–local-DB-choice-and-ID-gen.md) | Nov 2025 | Room + UUIDv7 (16-byte blobs in SQLite, native in Postgres). |
| 003 | [Two-Step Backend Sync](../adrs/adr-003–two-step-BE-sync.md) | Nov 2025 | Push dirty entities, then pull deltas. Last-writer-wins. |
| 004 | [Data Layer Composition](../adrs/adr-004-data-layer.md) | Nov 2025 | Dual-source repositories (Room + Ktor). Outbox = syncState field. |
| 005 | [Feature-Based Packages](../adrs/adr-0005-feature-based-package-structure.md) | Nov 2025 | Feature packages over layer packages. Start in feature, move to core/ when shared. |
| 006 | [Anonymous-First Sync](../adrs/adr-006-sync-protocol.md) | Feb 2026 | App works without login. Anonymous → Authenticated upgrade merges data. |
| 009 | [Navigation-Scoped ViewModel for Wizards](../adrs/adr-009-navigation-scoped-viewmodel-for-wizards.md) | Mar 2026 | Multi-screen wizard flows use a nested NavGraph-scoped ViewModel; state lives exactly as long as the flow. |
| 007 | [Anonymous-First Accounts](../adrs/adr-007-anonymous-first.md) | Feb 2026 | Recipes exist before accounts do. Registration re-parents local data to the new user UUID. |
| 008 | [Data Across Sessions](../adrs/adr-008-data-handling-across-sessions.md) | Feb 2026 | Logout keeps local data; a *different* user logging in clears it — Room tables **and** `recipe_images/` (see ADR-011 Decision 7). |
| 010 | [Client-Side Recipe URL Scraping](../adrs/adr-010-client-side-recipe-scraping.md) | Aug 2026 | Paste-a-URL import scrapes JSON-LD/microdata on-device via a new pure-Kotlin `:recipe-scraper` module; no backend endpoint, no per-site scrapers, unauthenticated `@ScraperHttpClient` prevents auth-token leakage to third-party hosts. Decision 5 (Aug 2026) adds a rendered-DOM fallback — an off-screen `WebView`, then a visible one — for the minority of sites that refuse HTTP clients outright. Decision 6 (Aug 2026) caches the imported hero image on-device via the same fetch-then-WebView ladder, since the same CDNs that block the HTML fetch block their own images too; the cached path is device-local only, not synced (see ADR-011). |
| 011 | [Cross-Device Recipe Images](../adrs/adr-011-cross-device-recipe-images.md) | Aug 2026 | Image bytes never ride the JSON sync payload. Scraped images are re-derived per device by a charging + unmetered backfill worker; user-authored photos are the only upload candidates, deferred to Stage 2 pending a deployed backend. A blank `imageUrl` means "user's own image, cannot be re-derived". Blob state lives in `recipe_image_state`, never on `recipes`, so the full-row upsert on the pull path can't wipe it. |

| 012 | [Recipe Image Upload (Stage 2)](../adrs/adr-012-recipe-image-upload.md) | Aug 2026 | Supersedes ADR-011 Decision 1: **every** recipe image is stored server-side, scraped as well as user-taken, and served to anyone who may see the recipe (`PRIVATE` still 404s for non-owners). A server-owned `imageBlobId` content hash rides `SyncRecipeDto` while the bytes never do; the backfill worker gains a preferred tier fetching it over an authenticated GET, so Coil never talks to our backend and no JWT can reach a third-party CDN. Upload is a post-sync sweep gated on `syncState = 'SYNCED'`, unmetered but not charging-only. Publicly serving scraped images is an accepted compliance posture, revisited at ~50 users ([#144](https://github.com/jmuci/ChefAI/issues/144)) and reversible by two server config flags. |

## RFCs

| # | Title | Status |
|---|-------|--------|
| 001 | [Offline-First Sync Protocol](../rfcs/rfc-001-offline-first-sync.md) | Draft |

## How to Add a New ADR

1. Create `docs/adrs/adr-NNN-short-title.md`
2. Follow the existing format: Context → Decision → Consequences
3. Add an entry to this index
4. Reference it in CLAUDE.md if it affects coding rules
