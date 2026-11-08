# Architecture Decision Records

All ADRs live in [`docs/adrs/`](../adrs/). This file is a quick-reference index.

## Active Decisions

One line each, on purpose — the ADR is the source of truth, and longer summaries here drift.

| # | Decision | Date | In one line |
|---|----------|------|-------------|
| 001 | [Hybrid Architecture](../adrs/adr-001-hybrid-architecture-choice.md) | Nov 2025 | Google Modern Android + selective Clean Architecture; domain layer optional. |
| 002 | [Local DB & ID Generation](../adrs/adr-002–local-DB-choice-and-ID-gen.md) | Nov 2025 | Room + client-generated UUIDv7; no FTS (local search is a `LIKE` scan). |
| 005 | [Feature-Based Packages](../adrs/adr-0005-feature-based-package-structure.md) | Nov 2025 | Feature packages; move to `core/` only when a second feature needs it. |
| 006 | [Sync Protocol](../adrs/adr-006-sync-protocol.md) | Mar 2026 | Push before pull, aggregate recipes, LWW on `updatedAt`; absorbs ADR-003/004. |
| 007 | [Anonymous-First Accounts](../adrs/adr-007-anonymous-first.md) | Feb 2026 | Data exists before accounts; upgrade re-parents rows locally, no endpoint. |
| 008 | [Data Across Sessions](../adrs/adr-008-data-handling-across-sessions.md) | Mar 2026 | Logout keeps data; a different account drops the previous one's rows and files. |
| 009 | [Nav-Scoped Wizard ViewModel](../adrs/adr-009-navigation-scoped-viewmodel-for-wizards.md) | Mar 2026 | Multi-screen wizards share a nested-NavGraph-scoped ViewModel. |
| 010 | [Client-Side Recipe Scraping](../adrs/adr-010-client-side-recipe-scraping.md) | Aug 2026 | `:recipe-scraper` parses on-device; HTTP→WebView ladder; SSRF-guarded unauthenticated client. |
| 012 | [Recipe Images](../adrs/adr-012-recipe-image-upload.md) | Aug 2026 | Every image uploaded; `imageBlobId` syncs, bytes never do; absorbs ADR-011. |
| 013 | [Measurement Units](../adrs/adr-013-measurement-unit-conversion.md) | Aug 2026 | Convert at display time only; never rewrite stored amounts. |
| 014 | [Households](../adrs/adr-014-households.md) | Sep 2026 | Sharing is a scope layer (`meal_plans.householdId`), not an ownership change. |
| 015 | [Meal Plans Week View](../adrs/adr-015-meal-plans-week-view.md) | Sep 2026 | Seven dated rows that every live plan is projected onto; overlaps all show. |
| 016 | [Recipe Language](../adrs/adr-016-recipe-language.md) | Oct 2026 | ISO 639-1 per recipe, detected at import; ask only when unsure. |
| 017 | [Cook History Sync](../adrs/adr-017-cook-history-sync.md) | Oct 2026 | Event log keyed by client id; un-mark tombstones, and a tombstone is final. |
| 018 | [Saved Meal Plans](../adrs/adr-018-saved-meal-plans.md) | Nov 2026 | A saved plan is a template snapshot in its own table, synced LWW. |

## Merged or Superseded

| # | Decision | Now lives in |
|---|----------|--------------|
| 003 | [Two-Step Backend Sync](../adrs/adr-003–two-step-BE-sync.md) | ADR-006 History (Outbox table never built) |
| 004 | [Data Layer Composition](../adrs/adr-004-data-layer.md) | ADR-006 History |
| 011 | [Cross-Device Recipe Images](../adrs/adr-011-cross-device-recipe-images.md) | ADR-012 (Decisions 2–5 keep their numbers) |

## RFCs

| # | Title | Status |
|---|-------|--------|
| 001 | [Offline-First Sync Protocol](../rfcs/rfc-001-offline-first-sync.md) | Superseded by ADR-006, ADR-007, ADR-008 — kept for historical context |

## How to Add a New ADR

1. Create `docs/adrs/adr-NNN-short-title.md`
2. Follow the existing format: Context → Decision → Consequences
3. Add a one-line entry to this index (detail belongs in the ADR)
4. Reference it in CLAUDE.md if it affects coding rules
