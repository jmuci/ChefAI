# ADR 004 – Data Layer Composition & Repository Pattern

**Date:** 2025-11-10
**Status:** Merged into [ADR-006 – Sync Protocol](adr-006-sync-protocol.md) (November 2026).

The repository-per-aggregate pattern over Room stands. The outbox table, the
`LocalDataSource`/`RemoteDataSource` pair and sync inside repositories never landed. See ADR-006's
**History** section for what holds and what doesn't; the original text is in git history.
