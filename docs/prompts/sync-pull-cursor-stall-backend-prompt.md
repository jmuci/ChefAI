# Backend Prompt: `/sync/pull` Pagination Cursor Never Advances

> Generated: August 2026
> No tracking issue filed yet — this was diagnosed live from a client-visible symptom (see
> [ChefAI#96](https://github.com/jmuci/ChefAI/issues/96) for a prior, different `/sync/pull` bug on
> the same endpoint).
> Background, if useful: [ADR-006 (sync protocol)](https://github.com/jmuci/ChefAI/blob/main/docs/adrs/adr-006-sync-protocol.md)
>
> **This document is self-contained — it can be handed to a session in the `ktor-chefai` repo
> as-is.** It was written entirely from the outside (Android client logs + black-box `curl` probing
> of a local dev instance) — nobody read this repo's actual source to produce it. Treat the
> hypotheses in §2 as a strong lead, not a diagnosis of the code.

---

## TL;DR

`GET /sync/pull` never advances past its first page for any account whose visible recipes exceed
`limit`. The `serverTimestamp` the client is told to use as the next `since` does not reflect what
was actually returned — sending it back reproduces the exact same page, forever. Every affected
Android client's sync hangs indefinitely: `push()` then `pull()` never returns, and the app hammers
this endpoint in a tight loop for as long as it's running.

**This is not theoretical.** One real account has been stuck like this for **~22 hours — 360,988
requests** — continuously since 2026-08-15 16:32, against a local dev backend. Confirmed with a
second, brand-new throwaway account too, so this isn't specific to that account's data.

---

## 1. Reproduction (copy-pasteable, ~30 seconds against a local backend)

```bash
# 1. Register a throwaway user (isolates the repro from any existing data)
TOKEN=$(curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"repro@example.com","username":"repro","password":"Repro123!"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")

# 2. Pull from the beginning
curl -s "http://localhost:8080/sync/pull?since=0&limit=100" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool | grep -E "serverTimestamp|hasMore"
#   → serverTimestamp: 1786718842216, hasMore: true   (100 recipes)

# 3. Pull again using the server's OWN cursor from step 2 — should move forward. Doesn't:
curl -s "http://localhost:8080/sync/pull?since=1786718842216&limit=100" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool | grep -E "serverTimestamp|hasMore"
#   → serverTimestamp: 1786718842216, hasMore: true   (SAME 100 recipe UUIDs as step 2)
```

Repeat step 3 forever — nothing ever changes. This is exactly the request the Android client makes
in a loop, because it faithfully follows the documented contract: use the response's
`serverTimestamp` as the next `since` ([`SyncOrchestrator.kt`](https://github.com/jmuci/ChefAI/blob/main/app/src/main/java/com/tenmilelabs/chefai/core/data/sync/SyncOrchestrator.kt),
`pull()`, roughly lines 250-310 — `since = maxOf(response.serverTimestamp, bookmarkMaxTs,
mealPlanMaxTs)`).

**Two more probes that narrow this down:**

- `limit` works correctly on its own: `since=0&limit=5` returns exactly 5 recipes. This isn't a
  general endpoint failure — it's specific to how the next cursor is derived and/or how `since` is
  applied.
- An absurd `since` *does* change the behavior: `since=9999999999999` correctly returns
  `recipes: [], hasMore: false`, **and `serverTimestamp` in that response is exactly
  `9999999999999`** — identical to the `since` that was sent.

Put together, all observed calls are consistent with:

```
serverTimestamp_returned == max(1786718842216, since_requested)
```

— as if `1786718842216` is a cached/stale watermark that never itself moves, with the real
recipe-filtering by `since` not applying anywhere in the range that matters. That's a guess at the
shape, not the code; §2 below is where to actually look.

---

## 2. What to check in `ktor-chefai`

Not knowing this repo's structure, in likely order of suspicion:

1. **Is `since` actually bound into the `/sync/pull` query's `WHERE` clause?** The repro above is
   consistent with it being ignored (or short-circuited by an earlier branch/cache) for any value
   that isn't wildly out of range.
2. **How is `serverTimestamp` computed?** It needs to be *the maximum `updated_at` among the rows
   actually returned in this specific page* (or something provably ≥ that, computed fresh every
   call). Right now it behaves like either (a) a stale cached/materialized value that stopped
   updating, or (b) `max(some cached value, since)` — which alone would explain why only an
   out-of-range `since` changes the response.
3. **Is there a response cache in front of this endpoint** (in-memory, reverse proxy, CDN) keyed on
   something narrower than `(userId, since, limit)`? That would independently produce "identical
   response no matter what `since` I send."
4. **Secondary, lower-confidence lead — possibly unrelated:** of 917 rows in `recipes` (local dev
   DB), **789 (86%) share one identical `updated_at` value down to the millisecond**
   (`1786716567042` → 2026-08-14 14:09:27 UTC) — the signature of a bulk `UPDATE ... SET updated_at
   = now()` touching most of the table in one transaction, plausibly from a recent migration (image
   upload bookkeeping or privacy/visibility work). Worth a look, but it's ~38 minutes off from the
   frozen cursor value above, so don't chase it as *the* cause without more evidence — it may just
   be a coincidence of two things landing around the same recent deploy.

---

## 3. Impact

Every account whose visible recipes exceed `limit` (100) can never finish a pull — and right now
that's *every* account, since the endpoint returns the same fixed page regardless of `since` well
before per-account data would even matter. For each affected client, `sync()` (which does `push()`
then awaits `pull()`) never returns:

- The client-visible sync indicator never clears — it spins forever, not just "for a while."
- `SyncWorker` never reaches success *or* failure, so WorkManager's retry/backoff never engages.
  This isn't a retry storm from the client's side — it's one coroutine stuck in a tight in-process
  loop for as long as the app process is alive, re-requesting roughly every 200ms (network
  round-trip time, no client-side delay between iterations).
- Sustained load on this endpoint of several requests/second, per affected device, indefinitely.

## 4. Suggested acceptance test

Whatever the actual fix turns out to be, this is the invariant that should hold — and would have
caught this in CI:

> Two consecutive pulls, where call *N+1* uses call *N*'s `serverTimestamp` as its `since`, must
> never return an identical `(recipe UUIDs returned, serverTimestamp)` pair while `hasMore` is still
> `true`.

A test that seeds >100 recipes for one account and pages through `/sync/pull` until `hasMore ==
false`, asserting forward progress on every iteration and a sane upper bound on page count, would
have caught this before it shipped.

---

## 5. Client-side mitigation (already added — informational, no action needed here)

The Android client now refuses to loop forever if this recurs: it detects a `pull()` page that
fails to advance the cursor and stops with a warning log instead of retrying indefinitely. That
bounds the *symptom* (indefinite spinner, endless local requests/logging) but is not a substitute
for the real fix — a client stuck behind this bug still can never sync past page one, it just now
fails visibly and cheaply after one page instead of invisibly and expensively forever. See
`SyncOrchestrator.kt`'s `pull()` in the Android repo if useful context while fixing this side.

## 6. Definition of Done

- [ ] Root cause identified in the `/sync/pull` handler (cursor computation, query binding, or an
      intervening cache — see §2)
- [ ] Fix makes `serverTimestamp` reflect real forward progress through the actual result set,
      computed fresh per request
- [ ] Regression test per §4, using a seeded account with `>limit` recipes
- [ ] Manually re-run the §1 repro against the fix — step 3 must return a `serverTimestamp` and
      `hasMore` that differ meaningfully from step 2, converging to `hasMore: false` within a
      bounded number of pages
- [ ] Check whether this also affects `bookmarkedRecipes`/`mealPlans` pagination inputs to the same
      cursor (`bookmarkMaxTs`, `mealPlanMaxTs` on the client side) — not exercised by the repro
      above since the throwaway account has none of either, but they feed the same `since` on the
      client
