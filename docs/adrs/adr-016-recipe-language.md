# ADR 016 – Recipe Language

**Date:** October 2026
**Status:** Accepted
**Author:** Claude (Opus 5.5), on behalf of Jose Mucientes
**Related:** [ADR-010](adr-010-client-side-recipe-scraping.md),
[ADR-013](adr-013-measurement-unit-conversion.md), [ADR-006](adr-006-sync-protocol.md)

---

## Context

URL import (ADR-010) worked on any site but assumed English throughout. Nothing in the pipeline
read a page's language — not the scraper, not either fetch path, not `RecipeDraft`, Room or sync —
and `IngredientTextParser` knew only English units. A Spanish recipe imported "successfully", with
names like `de harina` (from `200 g de harina`) and ranges like `2 a 3 cucharadas` left unparsed.

The goal is to import an English page and a Spanish page with equal quality, which needs three
things: know which language a recipe is in, parse it accordingly, and keep the answer.

Pages declare language in several places, none of them reliable on its own: schema.org
`inLanguage` (rare, but recipe-specific when present), `<html lang>`, `og:locale`,
`<meta http-equiv="content-language">`, and the HTTP `Content-Language` header. The page-level ones
are frequently a theme's template default — `lang="en-US"` on a Spanish WordPress blog is common.

---

## Decision 1: Store an ISO 639-1 code, nullable, as a raw string

`recipes.language` and `recipe_drafts.language` are nullable `TEXT` holding a two-letter lowercase
code (`"en"`, `"es"`); `null` means unknown. Regional variants collapse (`es-MX` → `es`) — nothing
downstream distinguishes them, and keeping them would make "is this Spanish?" a prefix match.

The domain carries the code as `String?` (`Recipe.languageCode`), **not** an enum.
`Recipe.toRoomEntity()` writes the full row, so mapping an unrecognised code to `null` would erase
it on the next edit — the same hazard that made `imageBlobId` and calories thread through the
domain model. `RecipeLanguage` (EN, ES, FR, IT, PT, DE) exists only as the picker vocabulary.

Existing recipes are **not** backfilled. Most are probably English, but guessing would hide the ones
that aren't; `null` is honest and the editor shows "Not set".

## Decision 2: Detection cross-checks markup against text, in `:recipe-scraper`

`RecipeHtmlParser` returns `ScrapedRecipe.language: DetectedLanguage?` — a code, a `HIGH`/`LOW`
confidence and the source signal. The highest-priority markup signal (`inLanguage` → `<html lang>`
→ `og:locale` → content-language meta → HTTP header) is weighed against a text verdict from
stop-word scoring of the title, description, ingredients and instructions:

| Markup                | Text                          | Result       |
|-----------------------|-------------------------------|--------------|
| agrees                | confident                     | markup, HIGH |
| absent                | confident                     | text, HIGH   |
| disagrees             | confident                     | text, **LOW**|
| explicit `inLanguage` | not confident                 | markup, HIGH |
| page-level only       | not confident, leans same way | markup, HIGH |
| page-level only       | leans elsewhere, or no lean   | markup, LOW  |
| absent                | not confident                 | `null`       |

Text wins a disagreement because of template defaults, but only at `LOW` — the user decides.
"Confident" means at least 8 profile-word hits and at least 2× the runner-up.

Below that bar the text can still *lean* one way — a strict lead, ties count as no lean. A lean
that matches page-level markup upgrades it to `HIGH`. Without this, every stub-length recipe on a
correctly tagged site would prompt (a three-ingredient English recipe rarely reaches 8 hits), and a
prompt that fires on obviously-English recipes trains users to dismiss it. The accepted cost: a
short Spanish recipe on an English-template site whose handful of words happen to lean English is
saved as English without asking — rare, and fixable from the editor's language row. (Added after
review of PR #271.)

**Stop-word scoring over ML Kit Language ID.** It is pure Kotlin, lives in the KMP module beside the
parser, is unit-testable on the JVM, and adds no dependency. Recipe text is short but formulaic, so
a few dozen function and kitchen words per language separate languages reliably. Six profiles are
kept, not two: without Portuguese and Italian, a Portuguese page would score confidently Spanish.
Markup naming a language with no profile (e.g. `nl`) can't be contradicted by text, since the
scorer can't recognise it.

The HTTP `Content-Language` header is passed only from the plain-HTTP fetch;
the WebView paths have no headers and don't need them (`<html lang>` survives in the DOM snapshot).
`Accept-Language` is deliberately **not** sent — it could make a site serve a translated variant
different from the page the user shared.

## Decision 3: Unit vocabulary is a union; only connector words are language-gated

After detection the parser re-parses each ingredient from its untouched `raw` line with the
detected language. Spanish unit words (`taza`, `cucharada`, `cdta`, `diente`, `pizca`, …) are
recognised on every page, mapped to the same canonical short forms as English, because they never
collide with English text. Only connector words switch on language: Spanish ranges (`"2 a 3"`,
`"1 o 2"`), stripping `de`/`del`/`de la` between unit and ingredient, and whether a bare `c` means
cup (English or undetected pages only — which also fixes French `c. à soupe`).

The consequence that matters: a wrong or missing language costs little, so the editor never needs
to re-parse ingredients when the user corrects the language.

## Decision 4: Ask only when unsure, in the editor

The import mapper flags a draft `languageNeedsConfirmation` unless detection came back `HIGH`. The
editor opens a "What language is this recipe in?" dialog for a flagged draft, pre-ticking the guess
(or the fallback setting when there's no guess, or the guess isn't an offered language). "Not now"
keeps the guess unconfirmed; tapping **Save** while still unconfirmed re-opens the dialog, and
confirming from there carries on with the save. Picking from the editor's permanent language row
also counts as confirming.

The prompt lives in the editor, not the import screen, because both import entry points — the
plain import screen and the visible-browser fallback — already converge on the editor via a saved
draft. One prompt, no duplication.

## Decision 5: One setting — a fallback language

Profile → Settings gains **Recipe language**: the language pre-ticked when an import must be
confirmed, and given to recipes written by hand (no prompt). Default: the device language when
offered, else English. Device-local DataStore (`chefai_user_prefs`, key `fallback_recipe_language`,
stored as the code), not synced — the same posture as the measurement-units preference (ADR-013).

A mode switch ("auto-detect" / "always ask") was considered and left out: confident detections are
right often enough that asking every time is friction, and the permanent language row covers
correcting the rare miss.

## Decision 6: Sync it, without letting an older backend erase it

`SyncRecipeDto.language` is optional and defaulted. On pull, `dto.language ?: existing.language` —
a backend that predates the column (or omits the field) can never wipe a value this device has.
The cost is that no device can clear a language back to unknown, which nothing needs to do.

---

## Consequences

- Room v10 → v11 (`MIGRATION_10_11`): `recipes.language`, `recipe_drafts.language`,
  `recipe_drafts.languageNeedsConfirmation`.
- `:recipe-scraper` public API grows additively: `ScrapedRecipe.language`, `DetectedLanguage`,
  `LanguageConfidence`, `LanguageSource`, `RecipeHtmlParser.parse(…, contentLanguage = null)`.
- The backend (`ktor-chefai`) needs a nullable `recipes.language` column and the `SyncRecipe` field
  before the language travels across devices; until then it stays local, safely.

## Follow-ups

- **Per-language backend search.** The `tsvector` is generated with the `'english'` config;
  `to_tsvector(language::regconfig, …)` isn't IMMUTABLE, so a per-row config needs a trigger or a
  `CASE` over fixed configs.
- **Spanish beyond the scraper:** `GrocerySectionClassifier` (English keywords, and `[^a-z0-9]`
  mangles `piña`), `IngredientDensity` (assumes the head noun comes last), `StepDurationParser`
  (`minutos`/`horas`, `y`), and the meal-plan diet keywords (`vegano`, `sin gluten`).
- Showing the language on the recipe details screen.
