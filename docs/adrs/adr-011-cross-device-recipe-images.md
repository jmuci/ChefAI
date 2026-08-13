# ADR 011 – Cross-Device Recipe Images

**Date:** August 2026
**Status:** Accepted
**Author:** Claude (Opus 5), on behalf of Jose Mucientes
**Related:** [ADR-006](adr-006-sync-protocol.md), [ADR-007](adr-007-anonymous-first.md),
[ADR-008](adr-008-data-handling-across-sessions.md),
[ADR-010 Decision 6](adr-010-client-side-recipe-scraping.md),
[issue #132](https://github.com/jmuci/ChefAI/issues/132)

---

## Context

ADR-010 Decision 6 caches an imported recipe's hero image on-device, at
`filesDir/recipe_images/<recipeId>`, referenced by a `localImagePath` column deliberately absent from
`SyncRecipeDto`. It closed by naming the open question rather than answering it: *"Whether and how to
synchronize cached image bytes across devices … is tracked separately as issue #132."*

Issue #132 laid out four options — (A) client uploads bytes and the backend re-hosts, (B) the backend
fetches from the source URL, (C) no blob sync and each device re-derives, (D) a hybrid uploading only
what cannot be re-derived. Option B was rejected on arrival: it is the datacenter-IP problem ADR-010
Decisions 1 and 5 both already turned down, and it fails on exactly the two sites that motivated the
work.

Three facts established while investigating shape the decision:

**The backend is not a place we can put things yet.** `ktor-chefai` is a separate repository, and
every base URL in this app is a hardcoded `http://10.0.2.2:8080` emulator loopback — in
`SyncApiService`, `AuthApiService`, `HomeLayoutApiService`, and `MealPlanApiService`. There is no
object storage, no CDN, no upload endpoint, and nothing deployed. Option A could not be tested
end-to-end today even if it were written.

**The argument that most demanded Option A was, on inspection, vacuous.** #132 leans on user-picked
photos being "straightforward data loss" — but the picker was inert end-to-end. `RecipeDraft.toRecipe()`
never read `selectedImageUri`, so saving discarded the choice silently; the picker is `GetContent()`
with no `takePersistableUriPermission`, so the `content://` read grant died with the Activity
regardless; and `ImageUploadContent` drew a placeholder icon under the comment *"Since Coil is not
available"*, which was never true. There was no data to lose yet, which inverts the priority: the
valuable work was making picked photos real, not syncing them.

**Option C is better supported than #132 credited.** `WebViewImageFetcher` already navigates a *fresh*
off-screen WebView straight at the image URL, with no prior page load. ADR-010's probe table shows the
Akamai wall is TLS/IP fingerprinting — every okhttp header permutation, including the full Chrome set,
returned 403 — which Chromium beats regardless of cookies. And `recipeExternalUrl` already rides
`SyncRecipeDto`, so if a cold image fetch ever does fail, a second device can replay the first one's
winning sequence (load the page, then the image) without any protocol change.

---

## Decision 1: Option D, staged — Stage 1 is entirely local

Scraped images are re-derived per device. User-authored images are the only upload candidates, and
that upload is Stage 2, deferred until there is a deployed backend to upload to.

**Why not A:** beyond the cost of infrastructure that does not exist for an app with no users, Option
A puts our infrastructure in the business of re-hosting third-party photographs and serving them to
other people's devices. Caching an image on the user's own device is what a browser does;
re-distributing it is a different posture and deserves a deliberate decision rather than arriving as a
side effect of a sync feature. Restricting uploads to user-authored content keeps both the cost and
the rights story unambiguous.

**Why not C alone:** it has no answer for a photo the user took themselves, which has no source URL
and cannot be re-derived by any means.

**Tradeoff accepted:** every device pays its own WebView cost for a bot-walled image, and a scraped
image breaks permanently if the source page removes it or rotates its URL. Both are acceptable while
the alternative is unbuildable; neither is made harder to fix later, because Stage 1 leaves the
protocol untouched.

## Decision 2: Bytes never ride the sync payload

`SyncApiService` posts `ContentType.Application.Json`, and recipes push as complete aggregates
batched fifty at a time (ADR-006). Base64 blobs inside that batch is not a serious option. Whatever
Stage 2 does, it gets its own channel — a sibling endpoint outside `/sync/push`.

This is what `localImagePath`'s absence from `SyncRecipeDto` means, and that absence is load-bearing:
`RecipeDao.upsertRecipe` is a full-row `@Upsert`, so any field the DTO does not carry is reset on
every pull that touches the row. That was a real bug — a freshly cached image path vanished moments
after import because the post-mutation sync pulled the row straight back — fixed by threading the
existing value through `toRecipeEntity` explicitly.

## Decision 3: A blank `imageUrl` means the image is the user's own

The two image sources are alternatives, never both. `PickedImageStored` clears `imageUrl` when a photo
is stored, codifying what the editor already presented as an either/or.

That gives a precise, checkable meaning to a blank `imageUrl`: **this image cannot be re-derived from
anywhere.** The backfill's SQL predicate is exactly `imageUrl != ''`, which is what keeps it from
trying to re-download a photo that never came from the network, and from overwriting a user's own
choice with a scraped one.

**Why not an explicit `imageSource` enum:** it is speculative now, and Stage 2 will need richer state
than a two-value discriminator anyway — upload state, a blob id, a content hash — which is what
`recipe_image_state` (Decision 5) is for.

**Tradeoff accepted:** the discriminator is derived, not stored. It breaks if a future feature ever
allows a remote URL and a local photo on the same recipe simultaneously. Adding the column at that
point is a migration, not a redesign.

## Decision 4: Picked photos are copied at pick time, not at save

`RecipeImageStore.writeFromUri` decodes the picked `content://` immediately and writes it under the
draft's `recipeId`, which is stable from the moment a draft exists — so the bytes land at exactly the
path the saved recipe reads from and nothing moves on save.

Copying now rather than at save is what makes the choice durable: the picker's read grant is scoped to
the Activity, so a URI merely remembered in state is unreadable after process death.

Decoding through `ImageDecoder` rather than copying the bytes verbatim earns two things beyond
storage: a camera original is routinely 8–15MB against a 2048px display need, and `ImageDecoder`
applies the EXIF orientation tag, which a raw copy would leave rendering portrait shots sideways.
`MAX_IMAGE_BYTES` deliberately does not apply — it bounds an untrusted network read, not a local file
the user chose explicitly.

## Decision 5: Blob state lives in its own table, not on `recipes`

`recipe_image_state(recipeId, attempts, lastAttemptAt)` is a sibling table keyed to the recipe, with a
CASCADE foreign key.

#132 asked whether to add an `imageSyncState` column or use a dedicated table, observing that
"overloading the recipe row seems wrong". It is: every device-local column on `recipes` is another
field `SyncRecipeDto.toRecipeEntity` must thread through by hand, or the full-row upsert on the pull
path silently wipes it — see Decision 2. A sibling table is structurally immune to that failure, and
gives Stage 2 somewhere to record upload state and a content hash without touching the synced row at
all.

A row exists only while an image is unresolved; the backfill deletes it once the bytes land.

## Decision 6: Backfill runs on its own schedule, on a charger and off metered data

`RecipeImageBackfillWorker` re-derives missing images with `CacheRecipeImage`'s existing
HTTP-then-WebView ladder.

**Not on the pull path:** a WebView spin-up inside the sync transaction would stretch every sync by
seconds for something nobody is waiting on. **Not on the render path** either — that lands the same
spin-up mid-scroll. It gets its own work request, enqueued by `SyncWorker` after a successful sync
rather than chained to it, since chaining would hold the sync's slot until the charging constraint was
satisfied.

`NetworkType.UNMETERED` + `setRequiresCharging(true)` is stricter than anything else in `SyncManager`,
because this is the only work that downloads whole images in a burst. Until it runs, the UI degrades
to hotlinking `imageUrl`, which already works everywhere except the bot-walled minority.

Three bounds keep it cheap: attempts are recorded **before** the fetch, so a run killed mid-WebView
still counts and a URL that reliably hangs is not retried forever (three strikes, then never again);
ten images per run from a two-hundred-row scan; and the worker re-enqueues itself if more remain
rather than waiting for a sync that might be a day away.

The candidate query deliberately over-selects rows that already have a `localImagePath`, because
whether the file is actually on disk is not expressible in SQL. Filtering that in Kotlin is what
repairs a row whose bytes were deleted underneath it — trusting the column alone would leave such a
recipe permanently pictureless.

## Decision 7: ADR-008's wipe rules extend to `recipe_images/`

ADR-008 is written entirely in terms of Room tables, which left two on-disk leaks.

A soft-deleted recipe keeps its image unless something reclaims it, and nothing ever hard-deletes a
soft-deleted row — so the file is deleted at soft-delete time. Deletion has no undo (#129) and the row
leaves every query the moment `deletedAt` is set.

More seriously, the account-switch path that preserves anonymous data deleted the departing account's
recipe rows but not their image files, leaving the previous user's photos readable on a shared device.
Ids are now collected before the bulk delete — `deleteRecipesForUser` is a single SQL statement, after
which there is no way to tell which images belonged to whom. A blanket `deleteAll()` is wrong in that
branch, which exists precisely to preserve the anonymous session's own images.

---

## Consequences

**Positive:**
- Ships without any backend work, on an app whose backend is not deployed and whose sync only runs for
  authenticated users in the first place.
- A user-picked photo now survives being picked, which it did not before — three live bugs
  (silent drop on save, transient URI, placeholder instead of a preview) fall out of one change.
- Both image sources are unified under `localImagePath`, which is the precondition Stage 2 needs.
  Adding an upload later is additive: a new endpoint, a new column on `recipe_image_state`, no change
  to anything decided here.
- The cross-account image leak is closed.
- `imageUrl` blank ⟺ user-authored is a single, testable invariant rather than a convention.

**Negative / accepted tradeoffs:**
- Every device pays its own WebView cost for a bot-walled image; there is still no cross-user or
  cross-device sharing of fetched bytes.
- A scraped image breaks permanently if the source removes it or rotates its URL, and after three
  failed attempts the app stops trying.
- Backfill never runs for a user who is never on an unmetered network while charging. Those recipes
  stay on the hotlink fallback, which is today's behaviour, not a regression.
- **Deleting a recipe destroys a user-picked photo irreversibly**, since there is no source to
  re-derive it from. This is the strongest argument for Stage 2 and the clearest cost of deferring it.
- `imageUrlThumbnail` is still a lie for locally-created recipes — `DraftMapper.toRecipe()` sets it to
  `imageUrl`, and no thumbnail is generated anywhere. Masked today because the UI prefers
  `localImagePath` for both list and detail. If a backend ever enters the loop, server-side
  derivatives are the natural place to make it real.
- Anonymous users get no backfill at all, since they never sync. Correct by construction — they have
  nothing pulled to backfill — but it means the feature is invisible until a user has an account.

## Stage 2 (not decided here, deliberately)

Blocked on a deployed backend. When it happens, the open questions #132 raised that Stage 1 does not
answer: transport (a sibling endpoint, resumability for ≤5MB one-shots), access control for `PRIVATE`
recipes (signed URLs vs. an authenticated proxy), what happens to blobs created while anonymous
(ADR-007), server-side blob reclamation against #129's *soft* delete, and how a pulling device learns
that an image it was promised has finally been uploaded.
