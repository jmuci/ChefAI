# ADR 012 – Recipe Image Upload (Stage 2)

**Date:** August 2026
**Status:** Accepted
**Author:** Claude (Opus 5), on behalf of Jose Mucientes
**Supersedes:** [ADR-011](adr-011-cross-device-recipe-images.md) Decision 1
**Related:** [ADR-006](adr-006-sync-protocol.md), [ADR-007](adr-007-anonymous-first.md),
[ADR-010](adr-010-client-side-recipe-scraping.md),
[issue #94](https://github.com/jmuci/ChefAI/issues/94),
[issue #132](https://github.com/jmuci/ChefAI/issues/132),
[issue #144](https://github.com/jmuci/ChefAI/issues/144)

---

## Context

ADR-011 shipped Stage 1: both image sources unified under a device-local `localImagePath`, scraped
images re-derived per device by a backfill worker, and uploading deferred to "Stage 2, blocked on a
deployed backend."

That backend now exists, built from `docs/prompts/recipe-image-upload-backend-prompt.md`.

Stage 1 left one loss unaddressed and named it as the clearest cost of deferring: **a photo the user
took has no source URL, so the device holding it holds the only copy in existence.** A reinstall, a
new phone, or a soft delete destroys it. Nothing on the client can fix that; only getting the bytes
off the device can.

---

## Decision 1: Every image is stored server-side, regardless of origin

ADR-011 Decision 1 restricted uploads to user-authored images. That restriction is lifted. Scraped
images are uploaded too.

Two failures argue for it, and they apply equally to both kinds of image:

- **A CDN URL may be unreachable from the app entirely.** The bot walls that motivated ADR-010 —
  Akamai on Food Network, Cloudflare on Serious Eats — refuse plain HTTP clients, and the app's image
  loader is OkHttp-based, so hotlinking is *not* a working fallback for those sources. On a second
  device such a recipe is simply pictureless until a charging + unmetered WebView sweep runs, which
  for some users is never.
- **A URL that renders today can be pulled tomorrow.** Source pages get deleted and image URLs
  rotate; a copy we hold survives both.

**Cost accepted knowingly:** storage and egress now scale with recipes × users rather than with user
photos alone. The per-user quota (500 MB) is the only lever on that, and it moves from optional to
required.

## Decision 2: Serving follows recipe visibility, not image provenance

| | Owner | Another user |
|---|---|---|
| `PRIVATE` recipe | serve | 404 |
| `PUBLIC` recipe | serve | serve |

If a caller may see the recipe, they may see its image. The `PRIVATE` rule is plain access control
and is not negotiable; provenance does not branch the rule.

**This is a real posture, taken deliberately.** Re-hosting a third party's photograph and serving it
publicly is not the same act as caching it on the user's own device. EU case law (*Renckhoff*, CJEU
2018) treats re-publishing a freely-available photo as a new communication to the public, and the US
"server test" that protects hotlinking (*Perfect 10*, *Hunley v. Instagram*) protects it precisely
because the linker does not serve the bytes — which, here, we would.

It is nonetheless a **compliance posture rather than a prohibition**: Pinterest does exactly this at
scale under DMCA §512(c) — uploads at users' direction, a registered agent, notice-and-takedown, a
repeat-infringer policy. Food publishers also ship Open Graph tags specifically so other services
display their hero image.

The judgement recorded here is therefore *"not now"*, not *"never"* and not *"no risk"*: at zero
users that machinery is not worth building. [#144](https://github.com/jmuci/ChefAI/issues/144)
revisits it at roughly the 50-user mark, together with the two other things that come due at that
scale (cross-user dedup, object storage/CDN).

**What keeps the retreat cheap**, none of it in the way meanwhile:

- `image_blobs.provenance` (`USER | SCRAPED`) is recorded from day one and read by nothing. It is the
  discriminator needed to re-impose an owner-only rule or to execute a takedown by source, and it
  cannot be reconstructed after the fact.
- `imageUrl` is never overwritten, so the source and its attribution survive.
- Two server config flags make either retreat a config change rather than a release:
  `allowScrapedImageUpload = false` stops accepting them; `serveScrapedBlobsToNonOwners = false`
  narrows serving to the uploading user, with existing blobs untouched.
- Blobs are keyed `(user_id, content_hash)` with **no cross-user dedup**. That is now a storage
  decision rather than a rights one — it keeps deletion, quota accounting and takedown per-user and
  refcount-free — but it also means no user's bytes are ever served on another user's behalf.

## Decision 3: The pointer syncs; the bytes never do

`imageBlobId` — the blob's content hash — is added to `RecipeEntity`, the domain `Recipe`, and
`SyncRecipeDto`. ADR-011 Decision 2 holds unchanged: a hash is not an image, and `POST /sync/push`
still carries no bytes.

**It is server-owned.** Only the upload endpoint sets it; a push never changes it, and the server
echoes its own value regardless of what a client sends. Two things follow:

- A client cannot point a recipe at a blob it did not upload.
- The field takes no part in last-writer-wins. Blobs are immutable and content-addressed, so two
  devices can never disagree about a blob's *contents*; which blob a recipe points at is settled by
  the same `updatedAt` comparison that already governs the row. #132 asked *"conflicts — LWW doesn't
  describe a blob"*; the answer is that there is no blob conflict to resolve.

`Recipe.toRoomEntity()` threads it explicitly. That mapper feeds a full-row `@Upsert`, so a field
omitted there is silently reset on every save through the domain model — the bug that ate a freshly
cached `localImagePath` in #139, and the one `deletedAt` still has in
[#128](https://github.com/jmuci/ChefAI/issues/128).

## Decision 4: A new preferred tier, above the existing ladder

```
1. localImagePath, if the file is on disk   → render it                  (Stage 1, unchanged)
2. else imageBlobId != null                 → authenticated GET from ChefAI      (NEW)
3. else imageUrl non-blank                  → HTTP → WebView scrape ladder (Stage 1, unchanged)
4. else no image
```

Tier 2 writes into `RecipeImageStore` exactly as tier 3 does, so once it lands the recipe is back on
tier 1 and works offline.

The backfill's candidate predicate widens from `imageUrl != ''` to
`(imageUrl != '' OR imageBlobId IS NOT NULL)`. That single `OR` is what finally lets a user's own
photo be restored on a second device — Stage 1 excluded those rows deliberately, because there was
nowhere to fetch them from.

Tier 3 still earns its place: a blob exists only once some device has uploaded one, so the scrape
ladder covers the window before that and the case where an upload was refused.

**Coil never talks to our backend.** The alternative — rewriting `imageUrl` to a ChefAI URL and
letting the image loader fetch it — would require attaching the JWT inside Coil's OkHttp stack and
then scoping that interceptor by host so the token never reaches the third-party CDNs the app also
hotlinks. That is the leak `@ScraperHttpClient` exists to prevent (ADR-010), and putting it in the
image loader would place the token one misconfiguration away from every CDN we touch. Fetching
through the worker with the already-authenticated client avoids inventing the surface at all.

**Invariant:** the backend image URL is built only from `BuildConfig.API_BASE_URL` and the recipe id.
It is never derived from `imageUrl`, and `@ScraperHttpClient` is never pointed at our host.

## Decision 5: Upload is a post-sync sweep gated on `syncState = 'SYNCED'`

`RecipeImageUploadWorker` mirrors the backfill's shape — bounded scan, batch cap, attempts recorded
*before* the attempt, self-re-enqueue when more remain — and is enqueued by `SyncWorker` after a
successful sync.

The `SYNCED` gate is load-bearing. The recipe row must exist server-side before an image can attach
to it, so the upload can never 404 — and #132's ordering question (*"a pulled `imageUrl` pointing at
our storage is useless if the upload hasn't landed"*) never arises, because the pointer comes into
existence only **because** an upload completed.

The candidate query orders user photos first (`ORDER BY (imageUrl = '') DESC`). If a sweep gets only
partway through its batch, the irreplaceable bytes are the ones that made it.

**Constraints are weaker than the backfill's: unmetered, but not charging.** Backfill waits for a
charger because a `WebView` spin-up per image is expensive and costs nothing to defer. An upload is
one request of a few hundred kilobytes, and every hour it waits is another hour a user's only copy of
a photo lives on one device — precisely the window this exists to close.

## Decision 6: Upload state extends `recipe_image_state`; failures split permanent from transient

Per ADR-011 Decision 5, blob bookkeeping stays on the sibling table. It gains `uploadAttempts`,
`lastUploadAttemptAt` and `uploadedFileModifiedAt`, counted separately from the download attempts so
a recipe stuck on one ladder cannot starve the other.

`uploadedFileModifiedAt` is how a **replaced** photo is noticed. Files are keyed by recipe id, so
picking a new photo overwrites the same path in place: `localImagePath` is unchanged and
`imageBlobId` is still set, so every other signal says the work is done. Comparing the file's mtime
against the value recorded at upload catches it without hashing every candidate on every sweep.

Both ladders now write their counters with targeted SQL upserts rather than a whole-row `@Upsert`.
A full-row write from one would reset the other's columns — resurrecting an upload the backend had
permanently rejected, or making an uploaded image look unsent. That is gotcha #24's hazard one table
further in.

A 4xx other than 408/429 spends the whole attempt budget at once: a body the server refuses, a
recipe that isn't ours, an exhausted quota, or a disabled scraped upload will fail identically on
every retry, and two more attempts only produce two more identical rejections. A `200` whose body
won't parse is *transient*, not a silent success — not knowing the blob id is indistinguishable from
never having uploaded.

## Decision 7: Anonymous users need no special handling; deletion gets a retention window

`SyncWorker` already returns early for anonymous sessions, so nothing is uploaded and behaviour is
unchanged. On account upgrade `AccountUpgradeUseCase` re-parents recipes and marks them `PENDING`;
they push, become `SYNCED`, and the next sweep collects them because `imageBlobId IS NULL`. #132's
*"queue blobs locally and drain on upgrade, or don't upload until there's an account?"* resolves to
the second answer for free, with no queue.

Server-side, a soft-deleted recipe marks its blob unreferenced and a sweep hard-deletes blobs
unreferenced for 30 days.

Be precise about what that does and does not fix: **Stage 2 does not make deletion undoable.** There
is still no undo (#129) and the local bytes still go immediately. What changes is that for 30 days
the bytes survive server-side, so a future undo becomes *possible* rather than *impossible*. The
losses actually closed here are reinstall, second device, and lost phone.

---

## Consequences

**Positive:**
- A user-taken photo is no longer one device failure away from gone — the loss ADR-011 named as the
  clearest cost of deferring Stage 2.
- The bot-walled case stops being a per-device tax. One device gets past Akamai on a residential IP;
  every other device downloads the result. It also stops being *broken* for anyone viewing a `PUBLIC`
  recipe, since hotlinking never worked for those sources.
- Images survive their source page being deleted or its URLs rotating.
- Purely additive: nothing decided in ADR-011 was reworked, only Decision 1 reversed. The blank
  `imageUrl` invariant, `recipe_image_state`, and the bytes-never-ride-the-payload rule all still
  hold.
- Two config flags and one unread column keep the rights posture reversible without a client release.

**Negative / accepted tradeoffs:**
- Storage and egress scale with recipes × users. The quota is the only lever, and it is now required
  rather than optional.
- Publicly serving scraped third-party images is a compliance posture with a to-do list attached
  (#144). Deferring that machinery is a decision made on the basis of having no users, and it expires
  when that stops being true.
- Uploaded blobs are user-visible, so unwinding Decision 1 later means deleting something people can
  see — unlike the flags in Decision 2, which only change what is served.
- `LocalDiskBlobStore` will not survive real traffic; object storage and a CDN are #144's third item.
- `imageUrlThumbnail` is still a lie. Server-side derivatives are the natural fix and now have a
  place to live, but they need an image-processing dependency and deserve their own decision.
- The upload sweep never runs for a user who is never on an unmetered network. Those photos stay
  single-copy, which is today's behaviour rather than a regression.
