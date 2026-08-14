# Plan: Recipe Image Sync — Stage 2 (backend upload + cross-device download)

> Drafted: August 2026
> Tracking issue: [#94](https://github.com/jmuci/ChefAI/issues/94) (pre-existing; supersedes the
> "Stage 2" section of [ADR-011](../adrs/adr-011-cross-device-recipe-images.md))
> Prerequisite: Stage 1, merged as #140 / #141 / #142 / #143
> Backend counterpart: [recipe-image-upload-backend-prompt.md](recipe-image-upload-backend-prompt.md)

---

## 1. Where Stage 1 left things

Stage 1 unified both image sources under a single device-local file:

| Source | `imageUrl` | `localImagePath` | Re-derivable elsewhere? |
|---|---|---|---|
| Scraped at import | the source CDN URL | set by `CacheRecipeImage` | Yes — replay the HTTP→WebView ladder |
| User-picked photo | **`""` (blank)** | set by `CachePickedImage` | **No. The device holds the only copy.** |

`RecipeImageBackfillWorker` re-derives the first kind on a second device. The second kind has no
recovery path at all: a reinstall, a soft delete, or simply a new phone destroys the photo. That is
the loss Stage 2 exists to close.

Two Stage 1 decisions are load-bearing here and must survive:

- **ADR-011 Decision 2** — bytes never ride the JSON sync payload. Uploads get their own channel.
- **ADR-011 Decision 3** — a blank `imageUrl` means "user's own image". The backfill's SQL predicate
  is literally `imageUrl != ''`.

### Why issue #94's original sketch cannot be implemented as written

#94 says: *"Update Room `imageUri` → `imageUrl` (remote URL)"*. Overwriting `imageUrl` with a
ChefAI-hosted URL destroys Decision 3's invariant — a user photo would stop being identifiable as
user-authored, and `RecipeImageBackfillWorker` would then try to re-derive it by pointing the
**unauthenticated** `@ScraperHttpClient` at our own authenticated host, collect three 401s, and give
up permanently. `imageUrl` must keep meaning *"third-party source URL"*. The blob pointer needs its
own field.

---

## 2. Decisions

### D1 — A new synced field, `imageBlobId`, carries the pointer; `imageUrl` is never rewritten

`imageBlobId: String?` is added to `RecipeEntity`, the domain `Recipe`, and `SyncRecipeDto`. It holds
the blob's content hash and is **server-owned**: only the upload endpoint sets it, push ignores
whatever the client sends, pull echoes the server's value.

Server-ownership removes `imageBlobId` from last-writer-wins entirely (a client can't point its
recipe at a blob it didn't upload) and answers #132's *"conflicts — LWW doesn't describe a blob"*
question: blobs are immutable and content-addressed, so they never conflict. Which blob a recipe
points at is decided by the same LWW that already governs the row.

The two flags stay orthogonal, which is what makes D5 possible later:

- `imageUrl` blank ⟺ user-authored (unchanged from Stage 1)
- `imageBlobId` non-null ⟺ the bytes are on our server

### D2 — Resolution ladder: a new preferred tier, inserted above the existing two

```
1. localImagePath, if the file is on disk        → render it            (Stage 1, unchanged)
2. else imageBlobId != null                      → authenticated GET from ChefAI   (NEW)
3. else imageUrl non-blank                       → HTTP → WebView scrape ladder    (Stage 1, unchanged)
4. else no image
```

Tier 2 downloads into `RecipeImageStore` exactly as tier 3 does, so once it lands the recipe is back
on tier 1 and works offline. This is a strict extension — no Stage 1 code path changes behaviour.

**Consequence worth stating: Coil never talks to our backend.** The alternative (rewrite `imageUrl`
to a ChefAI URL and let Coil fetch it) would require teaching Coil's OkHttp stack to attach the JWT,
and then carefully scoping that interceptor by host so the token is never sent to Food Network. That
is exactly the leak `@ScraperHttpClient` exists to prevent (ADR-010), and doing it in the image
loader would put the token one misconfiguration away from every third-party CDN we hotlink. Fetching
through the worker with the already-authenticated `HttpClient` avoids inventing that surface.

**Invariant to enforce in review:** the backend image URL is built only from
`BuildConfig.API_BASE_URL` + the recipe id. It is never derived from `imageUrl`, and
`@ScraperHttpClient` is never pointed at our host.

### D3 — Upload state lives on `recipe_image_state`, download state stays separate

Per ADR-011 Decision 5, blob bookkeeping goes on the sibling table, never on `recipes`. It gains
three columns, kept distinct from the existing download counters so the two ladders can't starve each
other:

```
recipeId (PK), attempts, lastAttemptAt,                                   -- download (existing)
uploadAttempts, lastUploadAttemptAt, uploadedFileModifiedAt               -- upload (new)
```

`uploadedFileModifiedAt` is the cheap change-detector for a **replaced** photo. `RecipeImageStore`
keys files by recipe id, so picking a new photo overwrites the same path in place: `imageBlobId`
would still be non-null and the sweep would skip a recipe whose bytes had changed. Comparing the
file's `lastModified()` against the value recorded at upload catches that without hashing 200 files
per sweep — `write()` renames a temp file into place, so the mtime always moves.

### D4 — Upload is a post-sync sweep, gated on the recipe already existing server-side

`RecipeImageUploadWorker`, mirroring `RecipeImageBackfillWorker`'s shape (bounded scan, batch cap,
attempts recorded *before* the attempt, self-re-enqueue when more remain), enqueued by `SyncWorker`
after a successful sync alongside `scheduleImageBackfill()`.

Candidate query:

```sql
SELECT r.uuid AS recipeId, r.imageUrl, r.localImagePath, r.imageBlobId
FROM recipes r
LEFT JOIN recipe_image_state s ON s.recipeId = r.uuid
WHERE r.deletedAt IS NULL
  AND r.localImagePath IS NOT NULL
  AND r.syncState = 'SYNCED'                      -- the recipe must exist on the server first
  AND COALESCE(s.uploadAttempts, 0) < :maxAttempts
ORDER BY (r.imageUrl = '') DESC,                  -- user photos first: they are the irreplaceable ones
         r.updatedAt DESC
LIMIT :scanLimit
```

Kotlin then drops rows where `imageBlobId != null && file.lastModified() == uploadedFileModifiedAt`
— the same "SQL over-selects, Kotlin narrows" split the backfill already uses, and testable without
WorkManager for the same reason.

`syncState = 'SYNCED'` is what makes ordering work: the recipe row is on the server before its image
is, so the upload can never 404, and #132's *"a pulled `imageUrl` pointing at our storage is useless
if the upload hasn't landed"* problem never arises — the pointer is only ever created **by** a
completed upload.

**Constraints: `NetworkType.UNMETERED`, but not `setRequiresCharging(true)`** — deliberately weaker
than backfill. Backfill waits for a charger because a WebView spin-up per image is expensive and
nothing is lost by waiting. Upload is a single ~300 KB POST, and every hour it waits is an hour the
user's only copy of a photo lives on one device. Different work request, different unique name, both
cancelled by `cancelAllSync()`.

### D5 — Every image is stored server-side and served to anyone who can see the recipe

**Decided (Jose, Aug 2026). This supersedes [ADR-011](../adrs/adr-011-cross-device-recipe-images.md)
Decision 1**, which said user-authored images are the only upload candidates.

An image is worth persisting whatever its origin: a CDN URL may be unreachable from the app and
therefore never render at all, and a URL that renders today can be pulled tomorrow. Both failures
apply equally to a scraped image and a user's own photo, so both get stored.

**Serving turns on recipe visibility alone, not on where the image came from:**

| | Owner | Another user |
|---|---|---|
| `PRIVATE` recipe | serve | **404** |
| `PUBLIC` recipe | serve | serve |

The `PRIVATE` restriction is a plain access-control requirement and is not negotiable. Provenance
does **not** branch the serving rule.

**The rights question, and why it isn't blocking.** Re-hosting a third party's photograph and serving
it publicly is a real posture — EU case law (*Renckhoff*) treats re-publishing a freely-available
photo as a new communication to the public, and the US "server test" that protects hotlinking
(*Perfect 10*, *Hunley*) protects it precisely because the linker doesn't serve the bytes. But it is
a *compliance* posture, not a prohibition: Pinterest does exactly this at scale under DMCA §512(c) —
uploads at users' direction, a registered agent, notice-and-takedown, a repeat-infringer policy. At
zero users none of that machinery is worth building, and food publishers ship Open Graph tags
specifically so other services display their hero image.

The judgement recorded here is therefore **"not now, deliberately"**, not "never" and not "no risk".
Revisit at the 50-user mark — tracked as [#144](https://github.com/jmuci/ChefAI/issues/144) — with the DMCA to-do list in hand.

**What keeps the door open**, all of it cheap and none of it in the way:

- `image_blobs.provenance` (`USER | SCRAPED`) is still stored, derived server-side from
  `recipes.image_url = ''` rather than trusted from the client. Nothing reads it today. It is the
  discriminator needed to re-impose an owner-only rule, or to execute a takedown by source, and
  recording it now costs one column while reconstructing it later would be impossible.
- `imageUrl` is never overwritten, so the source and its attribution survive.
- Two backend config flags make both retreats a config change rather than a release:
  `allowScrapedImageUpload = false` stops accepting them, and
  `serveScrapedBlobsToNonOwners = false` re-imposes exactly the owner-only rule this decision drops.
- Blobs stay keyed `(user_id, content_hash)` — **no cross-user dedup.** That is now a storage
  decision rather than a rights one: it keeps deletion, quota accounting, and takedown per-user and
  refcount-free. It is also the obvious storage optimisation to reach for later, at the same 50-user
  threshold, since many users importing the same Food Network recipe store identical bytes today.

**What it buys:** the bot-walled case is the one that actually hurts. Coil uses OkHttp, so Akamai and
Cloudflare 403 the *hotlink fallback* too — on a second device a Food Network recipe is simply
pictureless until a charging + unmetered WebView sweep runs, which for some users is never. Serving
publicly extends that fix to anyone viewing a `PUBLIC` recipe, who today sees the same broken image.

**What it costs:** storage and egress scale with recipes × users; the per-user quota in §6 is the
only lever on that bill; and uploaded blobs are user-visible, so unwinding this later means deleting
something people can see.

**What this leaves in place:** the WebView scrape ladder stays as tier 3, but stops being
load-bearing. Once device A has uploaded, device B takes tier 2 and never spins up a WebView; the
ladder is only reached in the window before the upload lands, or if it was refused.

### D6 — Anonymous users need no special handling

`SyncWorker` already returns early for anonymous sessions, so no upload is attempted and behaviour is
unchanged from today. On account upgrade, `AccountUpgradeUseCase` re-parents recipes and marks them
`PENDING`; they push, become `SYNCED`, and the next sweep picks them up because `imageBlobId IS
NULL`. #132's *"queue blobs locally and drain on upgrade, or don't upload until there's an
account?"* resolves to the second answer, for free, with no queue.

### D7 — Deletion: the local file still goes immediately, the blob gets a retention window

Stage 1 reclaims the local file at soft-delete time and that does not change. Server-side, a
soft-deleted recipe marks its blob unreferenced; a sweep hard-deletes blobs unreferenced for 30 days.

Be precise about what this does and does not fix: **Stage 2 does not make deletion undoable** — there
is still no undo UI (#129) and the local bytes are still gone at once. What it changes is that for 30
days the bytes still exist server-side, so a future undo becomes *possible* rather than
*impossible*. The reinstall / second-device / lost-phone losses are the ones actually closed here.

### D8 — Out of scope, deliberately

- **Thumbnails.** `imageUrlThumbnail` stays a lie. Server-side derivatives are the right fix and the
  natural place for it, but they mean an image-processing dependency on the backend; that deserves
  its own decision. Tracked separately.
- **Resumable / chunked upload.** Images are ≤5 MB after Stage 1's 2048px + JPEG-85 downscale
  (typically 200–600 KB). One-shot with retry is correct; resumability is machinery for a problem we
  don't have.
- **A CDN, signed URLs, or presigned direct-to-storage upload.** All are swaps behind the endpoint
  shape in §3, none change the client. See the backend prompt's "Later, not now".
- **Cross-user deduplication**, and the refcounting it would need. Storage optimisation for a
  problem that starts at scale, not at zero users. See D5.
- **DMCA plumbing** — a registered agent, a takedown route, a repeat-infringer policy. Deliberately
  deferred with D5; the `provenance` column and the two config flags are what make it addable later
  rather than retrofittable.

---

## 3. API contract (client's view; the backend prompt is authoritative)

```
PUT  /recipes/{recipeId}/image      Authorization: Bearer <jwt>
                                    Content-Type: image/jpeg
                                    X-Content-SHA256: <64 hex>
                                    body: raw bytes, ≤ 5 MiB
     → 200 {"imageBlobId": "<sha256 hex>", "updatedAt": 1755100000000}
     → 403 {"error": "SCRAPED_UPLOAD_DISABLED" | "QUOTA_EXCEEDED"}     (permanent — stop retrying)
     → 404 recipe unknown or not owned                                 (permanent — stop retrying)
     → 413 / 415 / 422                                                 (permanent — stop retrying)

GET  /recipes/{recipeId}/image      Authorization: Bearer <jwt>
     → 200 bytes, ETag: "<hash>", Cache-Control: private, immutable
     → 404 no blob, or not visible to caller

DELETE /recipes/{recipeId}/image    → 204, clears the pointer
```

Client-side error handling turns on **permanent vs. transient**: 4xx (other than 408/429) burns the
attempt counter straight to the cap so a rejected upload is never retried; 5xx / IO errors increment
by one and retry up to three times.

---

## 4. PR breakdown

Same shape as Stage 1 — small, independently reviewable, each green on its own.

### PR 0 — `BuildConfig.API_BASE_URL` (prerequisite, no behaviour change)

`http://10.0.2.2:8080` is currently hardcoded in five services: `SyncApiService`, `AuthApiService`,
`HomeLayoutApiService`, `MealPlanApiService`, `ChefAIApiService`. Stage 2 is definitionally "there is
now a real backend", so this has to become configurable before anything else lands, and a sixth
duplicate would be indefensible.

- `buildConfigField("String", "API_BASE_URL", ...)` per build type (`buildConfig = true` is already
  enabled in `app/build.gradle.kts:52`). Debug keeps the emulator loopback; release takes an HTTPS
  host.
- Replace all five constants.
- Cleartext: the debug default is `http://`, which needs a `networkSecurityConfig` limiting cleartext
  to `10.0.2.2` on debug only. There is no such file today (`app/src/main/res/xml/` has only backup
  rules), so it stays working purely because release has never pointed anywhere real.

### PR 1 — Schema: `imageBlobId` + upload bookkeeping (Room v4)

- `RecipeEntity.imageBlobId: String? = null`; `SyncRecipeDto.imageBlobId: String? = null`.
- `SyncMapper`: `toSyncDto` carries it; `toRecipeEntity(localImagePath)` takes it from the DTO — it
  is server-owned, so it arrives on the wire and gotcha #24's threading problem doesn't apply.
- `RecipeImageStateEntity` += the three columns from D3.
- `MIGRATION_3_4` — plain `ALTER TABLE ... ADD COLUMN` on both tables (only `DROP COLUMN` needs the
  API-34 SQLite), commit `app/schemas/.../4.json`, add the `MigrationTestHelper` case.
- **Thread `imageBlobId` through `Recipe.toRoomEntity()`
  ([RoomDomainMap.kt:136](../../app/src/main/java/com/tenmilelabs/chefai/recipes/data/mapper/RoomDomainMap.kt))**
  — that mapper already hardcodes `deletedAt = null, // or appropriate value`, which is
  [#128](https://github.com/jmuci/ChefAI/issues/128). Adding a field to the entity without adding it
  there means every save through the domain path silently wipes the blob pointer. Worth fixing #128
  in the same PR while the mapper is open.

### PR 2 — Upload

- `RecipeImageStore.read(recipeId): ByteArray?` and `lastModified(recipeId): Long?` (no read accessor
  exists today).
- `RecipeImageUploader` — `PUT`, raw body, `X-Content-SHA256`, using the **default authenticated**
  `HttpClient`. Maps status codes to a sealed `UploadOutcome { Success(blobId), Permanent, Transient }`.
- `UploadRecipeImage` use case — hash, upload, and on success
  `recipeDao.updateImageBlobId(uuid, blobId)` (a targeted `@Query UPDATE`, so no `updatedAt` bump, no
  `PENDING` mark, no full-row rewrite — the server already holds the same value), then record
  `uploadedFileModifiedAt` and clear the state row.
- `RecipeDao.getImageUploadCandidates(...)` + the `RecipeImageUploadCandidate` projection.
- `RecipeImageUploadWorker` + `SyncScheduler.scheduleImageUpload()` + `SyncManager` impl + the three
  fakes (`test/.../sync/FakeSyncManager.kt`, `test/.../testutil/FakeSessionManager.kt`,
  `androidTest/.../sync/FakeSyncScheduler.kt`), + `cancelAllSync()`, + the `SyncWorker` enqueue.

### PR 3 — Download (extend the backfill to tier 2)

- `FetchRecipeImageFromBackend` use case — authenticated `GET`, reusing the capped body reader
  currently private to `CacheRecipeImage` (extract it to a shared `internal` helper), writes through
  `RecipeImageStore`.
- `RecipeImageBackfillWorker` tries tier 2 first when `imageBlobId != null`, falling through to the
  existing ladder.
- Candidate query predicate relaxes from `r.imageUrl != ''` to
  `(r.imageUrl != '' OR r.imageBlobId IS NOT NULL)` — this is what finally lets a **user photo** be
  restored on a second device, which the Stage 1 predicate deliberately excluded.
- Reset `attempts` when a pull changes a recipe's `imageBlobId`, so three strikes collected before
  the blob existed don't permanently blind the device to it.

### PR 4 — Docs

- **ADR-012 — Recipe image upload** (or an amendment block on ADR-011): D1–D7 above, with D5's
  supersession of ADR-011 Decision 1 stated as a reversal-with-reasons rather than slipped in,
  including the rights posture it consciously accepts and the 50-user revisit.
- Update ADR-011's "Stage 2 (not decided here)" section to point at it, and its Consequences bullet
  about irreversible photo loss.
- `docs/claude/decisions.md`, the `CLAUDE.md` gaps table, `.claude/session-context.md`.
- Close [#94](https://github.com/jmuci/ChefAI/issues/94) and [#132](https://github.com/jmuci/ChefAI/issues/132).

---

## 5. Tests

**JVM unit** (fakes over mocks, Given/When/Then):

- `RecipeImageUploadCandidatesTest` — user photos ordered first; a recipe whose file mtime matches
  `uploadedFileModifiedAt` is skipped; a **replaced** photo (mtime moved, `imageBlobId` still set) is
  selected; `syncState != SYNCED` skipped; attempts at cap skipped; batch cap respected.
- `UploadRecipeImageTest` — success writes `imageBlobId` and clears the state row; a 403 burns
  attempts to the cap in one go; a 500 increments by one; a missing local file is a no-op, not a
  crash.
- `RecipeImageUploaderTest` — Ktor `MockEngine`: the request carries `Authorization`, the correct
  `X-Content-SHA256`, and the raw bytes; each status maps to the right `UploadOutcome`.
- `RecipeImageBackfillTest` — tier 2 preferred when `imageBlobId != null`; falls through to the
  scrape ladder when it fails; a blank-`imageUrl` recipe with a blob is now a candidate (it was not
  in Stage 1).
- `SyncMapperTest` — `imageBlobId` survives a pull round-trip and `localImagePath` still does.

**Instrumented:**

- `MigrationTestHelper` v3 → v4: existing rows survive, both new column sets present, defaults right.
- In-memory DAO test for the upload candidate query, including the `LEFT JOIN` and the ordering.

**Manual, once the backend is deployed** — the two cases that justify the work:

1. *Photo cross-device:* device A, pick a photo, save, sync. Device B pulls → recipe initially has no
   image → after an unmetered sweep the photo appears. Verify with
   `adb shell run-as com.tenmilelabs.chefai ls -l files/recipe_images`.
2. *Bot-walled scrape cross-device:* import a Food Network recipe on A, sync. On B confirm the
   hotlink fallback is **broken** (Akamai 403s Coil too), then that tier 2 restores it without a
   WebView.
3. *Replacement:* change the photo on A → B gets the new one, not the old.
4. *Privacy:* a second account must 404 on `GET /recipes/{id}/image` for another user's `PRIVATE`
   recipe, and must succeed for a `PUBLIC` one regardless of the image's provenance.

## 6. Still to settle (numbers, not directions)

Both are backend config values with defaults already in the prompt — neither blocks starting.

1. **Retention window** — 30 days for unreferenced blobs is a guess; it interacts with
   [#55](https://github.com/jmuci/ChefAI/issues/55) (account deletion & data retention).
2. **Per-user quota** — the backend prompt proposes 500 MB. With D5 decided, this is the only thing
   standing between the endpoint and free image hosting, and the only lever on the storage bill.

## 7. Revisit at 50 users — [#144](https://github.com/jmuci/ChefAI/issues/144)

D5 accepts a rights posture on the grounds that the app has no users, and that judgement expires
when it does. Three things come due together at roughly the 50-user mark:

1. **DMCA §512(c) plumbing** — a registered agent, a takedown route, a repeat-infringer policy. This
   is what makes public serving of scraped images a supported posture rather than an accepted risk.
   If the answer turns out to be "not worth it", `serveScrapedBlobsToNonOwners = false` retreats to
   owner-only serving with no release and no data migration.
2. **Cross-user deduplication** — many users importing the same recipe store byte-identical copies
   under D5's per-user keying. This is the first real storage lever after the quota.
3. **Object storage and a CDN** — `LocalDiskBlobStore` is a deliberate placeholder; egress from the
   app server is fine at 50 users and not at 5,000.
