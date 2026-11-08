# ADR 003 – Two-Step Synchronization with Backend

**Date:** 2025-11-10
**Status:** Superseded and merged into [ADR-006 – Sync Protocol](adr-006-sync-protocol.md)
(November 2026).

Push-then-pull with last-writer-wins survives in ADR-006 Decision 1. The Outbox table this ADR
proposed was never built — each row's `syncState` is the outbox. See ADR-006's **History** section;
the original text is in git history.
