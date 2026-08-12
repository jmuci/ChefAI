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
| 010 | [Client-Side Recipe URL Scraping](../adrs/adr-010-client-side-recipe-scraping.md) | Aug 2026 | Paste-a-URL import scrapes JSON-LD/microdata on-device via a new pure-Kotlin `:recipe-scraper` module; no backend endpoint, no per-site scrapers, unauthenticated `@ScraperHttpClient` prevents auth-token leakage to third-party hosts. |

## RFCs

| # | Title | Status |
|---|-------|--------|
| 001 | [Offline-First Sync Protocol](../rfcs/rfc-001-offline-first-sync.md) | Draft |

## How to Add a New ADR

1. Create `docs/adrs/adr-NNN-short-title.md`
2. Follow the existing format: Context → Decision → Consequences
3. Add an entry to this index
4. Reference it in CLAUDE.md if it affects coding rules
