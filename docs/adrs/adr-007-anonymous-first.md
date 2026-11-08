# 🔄 ADR 007 – Anonymous-First Session Model

**Date:** 2026-02-25

**Status:** Accepted

**Context:** To decrease user adoption friction, new unauthenticated sessions create and anonymous session.

**Related:** ADR-002 (Local DB & ID Generation), RFC-001 (Anonymous Users)

---

## Decision: Anonymous-First Session Model

Users interact with the app immediately without registration. The session model has three states:

```kotlin
sealed class UserSession {
    data object Loading : UserSession()
    data class Anonymous(val localUserId: UUID) : UserSession()
    data class Authenticated(
        val user: User,
        val authToken: AuthToken,
        val household: AuthenticatedHousehold? = null, // ADR-014 Decision 2
    ) : UserSession()
}
```

- On first launch, a `UUIDv7` local user ID is generated, stored in `SecurePreferences`, and a corresponding `UserEntity` is inserted into Room.
- All recipes created offline use `creatorId = localUserId`.
- There is **no server-side anonymous user**. The backend only knows about a user after registration.
- Registration **or login** triggers a **local** account upgrade (`AccountUpgradeUseCase`, one Room
  transaction) that re-parents the anonymous user's recipes, bookmarks, meal plans and saved meal
  plans to the authenticated UUID and marks them `PENDING`; the next push uploads them.
  *Amended (Nov 2026):* an earlier draft named a `POST /auth/upgrade` endpoint — it was never built
  and is not needed, since ordinary push already accepts client-generated ids (ADR-002).

### Rationale

- Eliminates the login barrier — users can explore and create recipes instantly.
- UUIDv7 (client-generated, time-sortable) avoids the need for a server round-trip to assign IDs.
- Not creating server-side anonymous users keeps the backend simple and avoids ghost-account accumulation.

---

## Consequences

### Benefits

- **Zero-friction onboarding:** Users create recipes immediately; registration is optional.

### Trade-offs

- **Anonymous data is local-only:** If the user uninstalls without registering, all anonymous data is lost. This is by design — the upgrade flow incentivizes registration.

---

## References

- ADR-002: Local DB Choice & ID Generation
