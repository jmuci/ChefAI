# ADR 012 – Recipe Images (on-device cache, cross-device sync and upload)

**Date:** August 2026 (merged with ADR-011 in November 2026)
**Status:** Accepted
**Author:** Claude (Opus 5), on behalf of Jose Mucientes
**Replaces:** [ADR-011](adr-011-cross-device-recipe-images.md), now a pointer to this file
**Related:** [ADR-006](adr-006-sync-protocol.md), [ADR-007](adr-007-anonymous-first.md),
[ADR-008](adr-008-data-handling-across-sessions.md),
[ADR-010 Decision 6](adr-010-client-side-recipe-scraping.md),
[issue #94](https://github.com/jmuci/ChefAI/issues/94),
[issue #132](https://github.com/jmuci/ChefAI/issues/132),
[issue #144](https://github.com/jmuci/ChefAI/issues/144)

> ADR-011 (Stage 1: on-device only) and ADR-012 (Stage 2: upload) were one design shipped in two
> steps, and only one ADR-011 decision was ever reversed. They are merged here. **Decisions 2–5 keep
> ADR-011's numbers**, so older code comments citing "ADR-011 Decision N" for N = 2–5 still point at
> the right text. The **History** section records what changed between the stages.

---

## Context

ADR-010 Decision 6 caches an imported recipe's hero image on-device at
`filesDir/recipe_images/<recipeId>`, referenced by a `localImagePath` column deliberately absent from
`SyncRecipeDto`. That leaves three problems:

- **A second device has no image.** It holds only `imageUrl`, and the bot walls that motivated
  ADR-010 — Akamai on Food Network, Cloudflare on Serious Eats — refuse plain HTTP clients. The image
  loader is OkHttp-based, so hotlinking does not work for those sources.
- **A URL that renders today can be pulled tomorrow.** Source pages get deleted and image URLs rotate.
- **A photo the user took has no source URL**, so the device holding it holds the only copy. A
  reinstall, a new phone or a soft delete destroys it.

Issue #132 laid out four options: (A) the client uploads bytes and the backend re-hosts them, (B) the
backend fetches from the source URL, (C) no blob sync and each device re-derives, (D) a hybrid. B was
rejected on arrival — it is the datacenter-IP problem ADR-010 already turned down. This ADR is A for
storage plus C as a fallback ladder.

---

## Decision 1: Every image is stored server-side, regardless of origin

Scraped images are uploaded as well as user-taken ones. The two failures above — an unreachable CDN
and a rotated URL — apply equally to both kinds, and a copy we hold survives both.

**Cost accepted knowingly:** storage and egress scale with recipes × users rather than with user
photos alone. The per-user quota (500 MB) is the only lever on that, and it is required.

## Decision 2: Bytes never ride the sync payload

`SyncApiService` posts JSON, and recipes push as complete aggregates batched fifty at a time
(ADR-006). Base64 blobs inside that batch is not a serious option; uploads get their own endpoint
outside `/sync/push`.

`localImagePath`'s absence from `SyncRecipeDto` is load-bearing: `RecipeDao.upsertRecipe` is a
full-row `@Upsert`, so any field the DTO does not carry is reset on every pull that touches the row.
That was a real bug (#139) — a freshly cached image path vanished moments after import because the
post-mutation sync pulled the row straight back — fixed by threading the existing value through
`toRecipeEntity` explicitly.

## Decision 3: A blank `imageUrl` means the image is the user's own

The two image sources are alternatives, never both. `PickedImageStored` clears `imageUrl` when a
photo is stored. A blank `imageUrl` therefore means exactly: **this image cannot be re-derived from
anywhere.** The scrape tier of the backfill (Decision 8) is gated on `imageUrl != ''`, which keeps it
from re-downloading a photo that never came from the network or overwriting the user's choice with
a scraped one.

**Why not an explicit `imageSource` enum:** richer state (upload state, a blob id) lives in
`recipe_image_state` (Decision 5) anyway. **Tradeoff:** the discriminator is derived, so it breaks if
a recipe ever has a remote URL and a local photo at once; adding the column then is a migration, not
a redesign.

## Decision 4: Picked photos are copied at pick time, not at save

`RecipeImageStore.writeFromUri` decodes the picked `content://` immediately and writes it under the
draft's `recipeId`, which is stable from the moment a draft exists, so nothing moves on save. The
picker's read grant is scoped to the Activity, so a URI merely remembered in state is unreadable
after process death.

Decoding through `ImageDecoder` rather than copying verbatim downsizes camera originals (8–15 MB
against a 2048px display need) and applies the EXIF orientation. `MAX_IMAGE_BYTES` does not apply:
it bounds an untrusted network read, not a file the user chose.

## Decision 5: Blob state lives in its own table, not on `recipes`

`recipe_image_state` is a sibling table keyed to the recipe with a CASCADE foreign key. Every
device-local column on `recipes` is another field `toRecipeEntity` must thread through by hand or
the full-row pull upsert wipes it (Decision 2); a sibling table is immune to that.

It holds both ladders' bookkeeping: `attempts`/`lastAttemptAt` for downloads and
`uploadAttempts`/`lastUploadAttemptAt`/`uploadedFileModifiedAt` for uploads, counted separately so a
recipe stuck on one cannot starve the other. Both ladders write with **targeted SQL upserts**, never
a whole-row `@Upsert`, which would reset the other's columns — resurrecting a permanently rejected
upload or making an uploaded image look unsent (gotchas #24, one table further in).

`uploadedFileModifiedAt` is how a **replaced** photo is noticed: files are keyed by recipe id, so a
new pick overwrites the same path and every other signal says the work is done. Comparing the file's
mtime avoids hashing every candidate on every sweep.

## Decision 6: Serving follows recipe visibility, not image provenance

| | Owner | Another user |
|---|---|---|
| `PRIVATE` recipe | serve | 404 |
| `PUBLIC` recipe | serve | serve |

If a caller may see the recipe, they may see its image.

**This is a deliberate posture.** Re-hosting a third party's photo and serving it publicly is not the
same act as caching it on the user's device: EU case law (*Renckhoff*, CJEU 2018) treats it as a new
communication to the public, and the US "server test" (*Perfect 10*, *Hunley v. Instagram*) protects
hotlinking precisely because the linker does not serve the bytes. It is a **compliance posture rather
than a prohibition** — Pinterest does this under DMCA §512(c) with an agent, notice-and-takedown and
a repeat-infringer policy. At zero users that machinery is not worth building;
[#144](https://github.com/jmuci/ChefAI/issues/144) revisits it at roughly 50 users, with cross-user
dedup and object storage/CDN.

**What keeps the retreat cheap:**

- `image_blobs.provenance` (`USER | SCRAPED`) is recorded from day one and read by nothing; it cannot
  be reconstructed later.
- `imageUrl` is never overwritten, so source and attribution survive.
- Two server flags make retreat a config change: `allowScrapedImageUpload = false` stops accepting
  scraped uploads; `serveScrapedBlobsToNonOwners = false` narrows serving to the uploader.
- Blobs are keyed `(user_id, content_hash)` with **no cross-user dedup**, so deletion, quota and
  takedown stay per-user and no user's bytes are served on another's behalf.

## Decision 7: The pointer syncs; it is server-owned

`imageBlobId` — the blob's content hash — rides `RecipeEntity`, the domain `Recipe` and
`SyncRecipeDto`. Only the upload endpoint sets it; a push never changes it, and the server echoes its
own value. So a client cannot point a recipe at a blob it did not upload, and the field takes no
part in last-writer-wins: blobs are immutable and content-addressed, so there is no blob conflict to
resolve.

`Recipe.toRoomEntity()` threads it explicitly, for the full-row-upsert reason in Decision 2.

## Decision 8: Resolution ladder, run by a backfill worker

```
1. localImagePath, file on disk    → render it
2. else imageBlobId != null        → authenticated GET from ChefAI
3. else imageUrl non-blank         → HTTP → WebView scrape ladder (CacheRecipeImage)
4. else no image
```

Tiers 2 and 3 write into `RecipeImageStore`, so a resolved recipe is back on tier 1 and works
offline. The candidate predicate is `(imageUrl != '' OR imageBlobId IS NOT NULL)`; the `OR` is what
restores a user's own photo on a second device. Tier 3 covers the window before any device has
uploaded, and refused uploads.

**Coil never talks to our backend.** Rewriting `imageUrl` to a ChefAI URL would mean attaching the
JWT inside Coil's OkHttp stack and scoping it by host so it never reaches the third-party CDNs the
app also hotlinks — the leak `@ScraperHttpClient` exists to prevent. **Invariant:** the backend image
URL is built only from `BuildConfig.API_BASE_URL` and the recipe id, and `@ScraperHttpClient` is
never pointed at our host.

`RecipeImageBackfillWorker` runs on its own work request, enqueued by `SyncWorker` after a successful
sync (not chained, which would hold the sync's slot), with `UNMETERED` + `setRequiresCharging(true)`
— the only work that downloads images in a burst and spins up WebViews. Not on the pull path (it
would stretch every sync) and not on the render path (mid-scroll spin-ups). Until it runs, the UI
hotlinks `imageUrl`.

Bounds: attempts are recorded **before** the fetch, so a run killed mid-WebView still counts (three
strikes, then never again); ten images per run from a two-hundred-row scan; the worker re-enqueues
itself while more remain. The query over-selects rows that already have a `localImagePath`, because
"the file is on disk" is not expressible in SQL; filtering in Kotlin repairs a row whose bytes were
deleted underneath it.

## Decision 9: Upload is a post-sync sweep gated on `syncState = 'SYNCED'`

`RecipeImageUploadWorker` mirrors the backfill's shape — bounded scan, batch cap, attempts recorded
first, self-re-enqueue — and is enqueued by `SyncWorker` after a successful sync.

The `SYNCED` gate is load-bearing: the recipe row exists server-side before an image attaches to it,
so the upload can never 404, and a pointer exists only **because** an upload completed. User photos
go first (`ORDER BY (imageUrl = '') DESC`), so a partial sweep saves the irreplaceable bytes.

**Unmetered, but not charging.** An upload is one request of a few hundred kilobytes, and every hour
it waits is another hour a user's only copy lives on one device.

A 4xx other than 408/429 spends the whole attempt budget at once — a refused body, someone else's
recipe, an exhausted quota or a disabled scraped upload fails identically on every retry. A `200`
whose body won't parse is *transient*, not success: not knowing the blob id is indistinguishable from
never having uploaded.

## Decision 10: Sessions and deletion

- **Anonymous users** need no special handling. `SyncWorker` returns early for them, so nothing is
  uploaded or backfilled. On account upgrade `AccountUpgradeUseCase` re-parents recipes as `PENDING`;
  they push, become `SYNCED`, and the next sweep collects them because `imageBlobId IS NULL`. No
  local upload queue.
- **Soft delete** deletes the local file at once (nothing ever hard-deletes the row, and there is no
  undo, #129). Server-side the blob is marked unreferenced and hard-deleted after **30 days**. That
  does not make deletion undoable; it makes a future undo *possible* and closes the reinstall,
  second-device and lost-phone losses.
- **Account switch** extends ADR-008 to `recipe_images/`: the branch that preserves anonymous data
  collects the departing account's recipe ids *before* the bulk delete (`deleteRecipesForUser` is one
  SQL statement, after which ownership is unknowable) and deletes those files. A blanket `deleteAll()`
  would be wrong there, since that branch exists to keep the anonymous session's own images.

---

## Consequences

**Positive**

- A user-taken photo is no longer one device failure away from gone.
- The bot-walled case stops being a per-device tax: one device gets past Akamai on a residential IP
  and every other device downloads the result. Images survive their source page disappearing.
- `imageUrl` blank ⟺ user-authored is a single, testable invariant.
- Two config flags and one unread column keep the rights posture reversible without a client release.

**Negative / accepted tradeoffs**

- Storage and egress scale with recipes × users; the quota is the only lever.
- Publicly serving scraped images is a compliance posture with a to-do list (#144) that comes due
  when the app has users. Unwinding Decision 1 later means deleting blobs people can see.
- `LocalDiskBlobStore` will not survive real traffic; object storage and a CDN are #144's third item.
- `imageUrlThumbnail` is a lie — `DraftMapper.toRecipe()` sets it to `imageUrl` and nothing generates
  thumbnails. Masked because the UI prefers `localImagePath`. Server-side derivatives are the natural
  fix but need an image-processing dependency.
- Backfill never runs for a user who is never unmetered while charging, and uploads never run for one
  who is never unmetered; those recipes stay on hotlinks or single-copy.
- A scraped image with no uploaded blob breaks permanently if its source goes, and after three failed
  attempts the app stops trying.

---

## History

- **Stage 1 (ADR-011, Aug 2026)** shipped with no backend to upload to: every base URL was an
  emulator loopback and nothing was deployed. Its Decision 1 was option D — scraped images re-derived
  per device, **only user-authored images** as future upload candidates — on the grounds that
  re-hosting third-party photos is a different posture from caching them, and should be a deliberate
  decision rather than a side effect of sync. The same work made picked photos real: before it,
  `RecipeDraft.toRecipe()` never read `selectedImageUri`, the `GetContent()` grant died with the
  Activity, and the editor drew a placeholder. Decisions 2–5 here are Stage 1's, unchanged.
- **Stage 2 (ADR-012, Aug 2026)** shipped once the backend existed (spec:
  `docs/archive/prompts/recipe-image-upload-backend-prompt.md`). It reversed Stage 1's Decision 1 —
  every image is uploaded (Decision 1 here) — because hotlinking does not work for the bot-walled
  sources at all, and source URLs rot. Everything else was additive.
