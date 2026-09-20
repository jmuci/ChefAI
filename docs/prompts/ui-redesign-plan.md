# UI Redesign Plan — "Modernist" (ChefAI)

**Source of truth:** `~/Projects/design_handoff_chefai_redesign with_dark_mode/`
(README.md, styles.css, `ChefAI Redesign.dc.html`, `screenshots/`).
Ignore the older `design_handoff_chefai_redesign/` folder — same CSS and screenshots,
but its README lacks the Dark mode section. 19 screens, 4 flows.

## Rules for every session

1. Read the handoff README section for your screens and open `screenshots/flow-N-*.png`.
2. Build against `docs/design/modernist.md` (written in PR 1). Never port the HTML.
3. **Never write a literal color.** Every hex in the handoff is the *light* resolution of a
   semantic role. Use `MaterialTheme.colorScheme.*` or `LocalChefColors.current.*`.
   A unit test enforces this (PR 1) — if it fails, you wrote a literal.
4. **Do not invent dark values.** Build light exactly as specced; keep dark compiling and
   crash-free; leave dark as an explicit `TODO(dark)` pointing at the handoff's Dark mode section.
   A plausible-looking placeholder is harder to find later than an obviously unfinished one.
5. Every screen gets light **and** dark previews (dark may look unfinished — that's correct).
6. Run `./gradlew :app:testDebugUnitTest` before finishing. One PR per session.

## Decisions (settled)

- **D1 Dark mode** — kept. Not designed yet; see rules 3–5. Cross-cutting, not a session.
- **D2 Home** — restyle the SDUI components (`ComponentRenderer`, `SduiCarousel`, `LargeCard`).
  No backend contract change. Design 01's Tonight/This Week/Grocery become component styles,
  not a static layout.
- **D3 Meal Plans (06)** — design is a week view; today it lists plan cards. Structural, needs
  VM/data work. Split into its own PR (12), not folded into a restyle.
- **D4 Icons** — vendor Lucide SVGs as vector drawables, drop `material-icons-extended`. See below.
- **D5 Recipes filter chips** — real filter, but deferrable: PR 10 renders the chips per design and
  wires only "All"; the session files a detailed GitHub issue for the filter logic before merging.

## D4 — Lucide icons, elaborated

Today: 41 distinct `Icons.Default/Filled/Outlined/AutoMirrored.*` usages plus 8 custom drawables.
The design specifies Lucide (2px stroke, round caps, 18–20px). Material's filled set is solid-fill
and metrically different — it visibly breaks the Modernist look, so "keep Material" is not viable.

**Chosen: vendor the SVGs.** Lucide is ISC-licensed (commercial use fine; retain the notice).
Android vector drawables support `strokeWidth`/`strokeColor`/`strokeLineCap`, so SVG→XML is lossless.

PR 2 is **purely additive** — it adds icons but changes no call site. That keeps it a leaf node
instead of a repo-wide 41-file diff that conflicts with every screen PR. Each screen session swaps
its own icons while restyling (it is rewriting those call sites anyway), and PR 13 removes the
Material dependency once the last usage is gone.

PR 2 deliverables:
1. `tools/lucide-import.sh` — pinned Lucide version, takes a name list, converts SVG → `<vector>`,
   writes `res/drawable/ic_lucide_<name>.xml`. Committed so adding an icon later is one command.
2. `core/ui/icons/ChefAIIcons.kt` — semantic `@DrawableRes` map (`ChefAIIcons.Search`,
   `.BookmarkFilled`, `.Trash`…) covering every icon the 19 screens need. Call sites never touch
   raw `R.drawable` ids.
3. `NOTICE` entry for Lucide ISC.
4. A mapping table in the PR description: every current `Icons.*` usage → its Lucide replacement,
   so screen sessions look it up rather than choosing.

Deferred to the screen PRs: the 41 call-site replacements.
Deferred to PR 13: removing `androidx-material-icons-extended`, deleting superseded drawables
(keep `ic_chef_hat_*` — brand, not Lucide).

Rejected: a third-party Lucide-Compose artifact — unofficial (Lucide ships no Android target),
new dependency for what is ultimately static XML, unverified maintenance.

---

## Sessions

Foundation is Opus because it sets conventions every later session copies.
Screen sessions are Sonnet 5 because by then the pattern is fixed.

| # | PR | Scope | Model |
|---|----|-------|-------|
| **1** | `redesign/theme` | Zero-radius `Shapes`. Semantic roles in `ColorSchemes.kt` (bg/ink/muted/divider/surfaceVariant). `LocalChefColors` CompositionLocal for what M3 has no slot for: the accent ramp 100–900, neutral ramp, nav sage `#e7f0e0`, divider widths (2dp/1dp), 0dp radius constant — light + dark instances, dark filled with TODOs. Archivo via the existing `GoogleFont` provider, replacing Roboto. **`docs/design/modernist.md`**: token table, type scale, rule rhythm, dark-mode discipline, and a "how to build a screen in this system" recipe. **A unit test that greps `ui/` for `Color(0x`/`.copy(alpha=` on ink and fails** — the guardrail that keeps Sonnet sessions honest. | **Opus 5** |
| **2** | `redesign/icons` | D4 above. Mechanical once the list is fixed; can run parallel with 1. | Sonnet 5 |
| **3** | `redesign/components` | `core/ui/components/flat/`: `FlatChip`, `FlatSwitch`, `FlatField`, `WizardProgressBar`, `SectionRule`, plus `FlatButton` (primary/secondary/ghost, 52dp block, flush-left label), `FlatCheckbox`, `FlatRadio`, `FlatTag`, `SquareAvatar`. Focus ring (2dp accent, 2dp offset), pressed tint one ramp step — no rounded ripples. Light+dark previews each. | **Opus 5** |
| **4** | `redesign/shell` | Bottom nav (sage bar, 10px/800 labels, accent active), `ChefAITopAppBar` → flat header, `UserProfileMenu` panel (2px ink border + `--shadow-lg`; in dark the border carries it, per the handoff) — screen **09**, all three `UserSession` states. | **Opus 5** |
| **4b** | `redesign/shared-cards` | Restyle everything in `core/ui/components/`: `RecipeListCard`, `LargeCard`, `InfoChip`, `CookedToggleButton`, `SharedByBadge`, `RecipePrivacyBadge`, `SectionHeaderWithSubtitle`. **This PR exists so the screen wave never touches `core/ui/components/`** — those seven files have consumers in four different screen PRs. Read handoff sections 1, 3, 4, 5 for their specs. | Sonnet 5 |
| **5** | `redesign/settings-auth` | **10** Settings, **07** Log in, **08** Create account. Exercises `FlatField`, `FlatRadio`, rule rhythm, flush-left buttons, accent-700 field errors. | Sonnet 5 |
| **6** | `redesign/wizard` | **13/14/15** wizard steps. `WizardProgressBar`, `FlatChip`, `FlatSwitch`, servings stepper band, disabled `collectionTooSmall` state, `isSaving`. | Sonnet 5 |
| **7** | `redesign/plan-detail-shopping` | **16** Meal plan detail (per-meal segment bar), **17** Shopping list (aisle order, `≈` weights, "Checked by"). Empty/ungenerated states included. | Sonnet 5 |
| **8** | `redesign/household` | **11** Household (has + empty), **12** Accept invite (all `HouseholdJoinOutcome` states). Square initials tiles, overlapping stack, invite-code box. | Sonnet 5 |
| **9** | `redesign/recipe-editor-import` | **19** Recipe editor (ingredient/step rule rhythm, drag grips, tags), **18** Import (error banner — flag it as a ramp-flip case for dark). Drop "Recently imported". Split 9a/9b if the diff sprawls. | Sonnet 5 |
| **10** | `redesign/search-recipes` | **02** Search browse (flat accent-ramp card tones replacing the gradient — ramp-flip case for dark), **03** results, **04** Recipes grid + filter chips per D5 (+ file the issue), **05** Recipe detail. | Sonnet 5 |
| **11** | `redesign/home-sdui` | **01** Home — restyle `ComponentRenderer` / `SduiCarousel` / `LargeCard` / `InfoChip` to Modernist. No backend change. | Sonnet 5 |
| **12** | `redesign/meal-plans-week` | **06** Meal Plans week view: VM + data work to go from plan-card list to a Mon–Sun day grid with "+ Add meal" empty days, week navigator, household segmented control, grocery summary. Write an ADR. | **Opus 5** |
| **13** | `redesign/cleanup` | Sweep: rounded `Surface`/`Card`, rounded-bound ripples, missing focus rings, emoji still in strings/enum labels, any literal color the test missed, dark `TODO(dark)` inventory listed in one place. | Sonnet 5 |

## Sequencing & parallelization

Three serial foundation waves, then a wide parallel wave. Each parallel session runs in its own
git worktree, branched from the same post-wave-C `main`.

```
Wave A   1 theme [Opus]  ‖  2 icons [Sonnet]          ← additive, no call-site changes
Wave B   3 components [Opus]                           ← everything depends on it, cannot split
Wave C   4 shell [Opus]  ‖  4b shared-cards [Sonnet]
Wave D0  5 settings-auth [Sonnet]                      ← CANARY, run alone
Wave D   6 ‖ 7 ‖ 8 ‖ 9 ‖ 10 ‖ 11 ‖ 12                  ← 7-way parallel
Wave E   13 cleanup [Sonnet]
```

**Why PR 5 runs alone as a canary.** It is the first session to consume the PR 3 component API in
anger. If that API is wrong, you want to find out on one branch, not seven. Its prompt already
tells it to report friction rather than work around it. Fix PR 3, then open the gate.

**Why wave D is safe to parallelize.** After 4b, the seven screen sessions touch disjoint
packages. The collisions that would otherwise occur are all in `core/ui/components/`:

| Shared file | Would collide between | Resolved by |
| --- | --- | --- |
| `RecipeListCard` | 10 (search, recipes) + 11 (home renderer) | 4b owns it |
| `SectionHeaderWithSubtitle` | 10 (recipes) + 11 (home renderer) | 4b owns it |
| `CookedToggleButton` | 7 (meal row) + 10 (recipe detail) | 4b owns it |
| `SharedByBadge` | 7 (detail, shopping) + 12 (plan card) | 4b owns it |

**File ownership inside `mealplans/`** — 7 and 12 are the one remaining risk, so split explicitly:
- PR 7 owns `ui/detail/`, `ui/shoppinglist/`, `ui/components/MealPlanMealRow.kt`
- PR 12 owns `ui/MealPlansScreen.kt`, `ui/MealPlansViewModel.kt`, `ui/components/MealPlanCard.kt`

**Low-conflict hotspots, confirmed not worth serializing:** `strings.xml` emoji stripping is 14
lines, and the Kotlin enum-label emoji sit almost entirely in `mealplans/ui/create/` (PR 6) and
`GrocerySection.kt` (PR 7). `core/ui/preview/` has 8 consumers — if two sessions add preview data
they will conflict trivially; tell them to add fixtures locally rather than to shared preview files.

**The real limit is review bandwidth, not tooling.** Seven PRs landing together is a lot to review
well, and a rubber-stamped redesign PR is how literal colors and rounded corners survive to PR 13.
If you are reviewing alone, run wave D as two batches — `6, 7, 8` then `9, 10, 11, 12` — which
still cuts the critical path roughly in half versus fully serial.

## Budget

**4 Opus sessions (1, 3, 4, 12) · 10 Sonnet.** The leverage is PR 1's `modernist.md` + literal-color
test and PR 3's component API. If those are precise, every screen session is mechanical translation.

Critical path: A → B → C → D0 → D → E is **6 waves** instead of 14 serial sessions.

## Follow-ups to file as issues

- Recipes filter chips → real filtering (D5), written during PR 10.
- Dark palette design — blocked on design; the `TODO(dark)` inventory from PR 13 is its scope.
- "Recently imported" on Import (18) — needs a new query, dropped from PR 9.
