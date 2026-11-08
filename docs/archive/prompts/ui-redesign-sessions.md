# UI Redesign — per-session kickoff prompts

Copy one block as the first message of a fresh session. Each is self-contained: it names the
model to set, the branch, the handoff sections to read, and the definition of done.

Plan: [`docs/prompts/ui-redesign-plan.md`](ui-redesign-plan.md).

---

## Shared preamble (every session)

> Every prompt below already embeds this. It is repeated here so you can re-paste it if a
> session drifts.

```
Design source of truth: ~/Projects/design_handoff_chefai_redesign with_dark_mode/
(README.md, styles.css, screenshots/). Ignore ~/Projects/design_handoff_chefai_redesign/ —
same CSS and screenshots, but its README lacks the Dark mode section.

Non-negotiable rules:
1. Recreate in Compose. Never port the HTML prototype.
2. Never write a literal color. Every hex in the handoff is the LIGHT resolution of a semantic
   role. Use MaterialTheme.colorScheme.* or LocalChefColors.current.*. A unit test enforces this.
3. Do not invent dark values. Build light exactly as specced; keep dark compiling and crash-free;
   leave dark as TODO(dark) referencing the handoff's "Dark mode" section.
4. Every screen and component gets light AND dark previews. Dark may look unfinished — correct.
5. Zero corner radius everywhere except the user avatar circle. 44dp minimum hit targets.
6. Emoji in strings.xml and enum labels are dropped — keep the string, strip the leading glyph.
7. Read docs/design/modernist.md before writing any UI code.
8. Run ./gradlew :app:testDebugUnitTest before finishing. Report pass/fail counts.
9. Propose diffs, not file rewrites. One PR, rebased on main.
```

---

## PR 1 — Theme foundation · **Opus 5**

```
Set model: Opus 5. Branch: redesign/theme.

Read ~/Projects/design_handoff_chefai_redesign with_dark_mode/README.md in full — especially
"Design Tokens" and "Dark mode" — plus styles.css (the :root block is the token source).

This is the foundation PR for a full app redesign onto the "Modernist" design language. Twelve
later sessions copy whatever conventions you set here, most of them run by a smaller model, so
precision and a written spec matter more than breadth.

Build:
1. core/ui/theme/ColorSchemes.kt — map the handoff's semantic roles onto M3 slots:
   background #f3f2f2, onBackground #201e1d, outline = divider (40% ink), onSurfaceVariant =
   muted ink, primary #0A8080, surfaceVariant = neutral-200. Resolve muted ink to a real color
   per theme — do NOT port the CSS color-mix() as alpha on ink (it loses contrast on dark).
2. A LocalChefColors CompositionLocal for what M3 has no slot for: accent ramp 100-900, neutral
   ramp 100-900, nav sage #e7f0e0, rule widths (2dp section / 1dp row), radius 0dp. Light and
   dark instances; fill dark with TODO(dark) and obviously-unfinished values.
3. core/ui/theme/Type.kt — Archivo replacing Roboto via the existing GoogleFont provider. Map
   the handoff's scale (h1 42 / h2 32 / h3 25 / h4 20 / h5 16 / h6 13 uppercase .08em; body
   13-14px; section labels 10-12px/800 uppercase .08-.1em) onto M3 TextStyles.
4. Theme-level Shapes(extraSmall..extraLarge = RoundedCornerShape(0.dp)).
5. app/src/test/.../core/ui/theme/NoLiteralColorsTest.kt — walks app/src/main/java/**/ui/**
   and fails on Color(0x… literals and alpha-muted ink, allowlisting core/ui/theme/ itself.
   This is the guardrail that keeps later sessions honest; make its failure message explain
   the fix.
6. docs/design/modernist.md — the deliverable that makes later sessions cheap. Token table,
   type scale, rule rhythm (2dp above a group, 1dp between rows, 2dp below), dark-mode
   discipline, focus-ring and pressed-state spec, and a worked "how to build a screen in this
   system" recipe using Settings as the example.

Do not restyle any screen yet — they come in later PRs. Existing screens must still compile and
render; expect them to look half-converted, which is fine.

[shared preamble rules 1-9]
```

---

## PR 2 — Lucide icons · **Sonnet 5** · parallel with PR 1

```
Set model: Sonnet 5. Branch: redesign/icons.

Read the "Assets" section of ~/Projects/design_handoff_chefai_redesign with_dark_mode/README.md.
Icons in the design are Lucide: 2px stroke, round caps, 18-20px.

The app uses 41 distinct Icons.Default/Filled/Outlined/AutoMirrored.* usages plus 8 custom
drawables in res/drawable. Material's filled set is solid-fill and metrically wrong for this
design. Replace it with vendored Lucide vector drawables.

Build:
1. tools/lucide-import.sh — pins a Lucide release, takes an icon-name list, converts SVG to
   Android <vector> XML (strokeWidth/strokeColor/strokeLineCap — the conversion is lossless),
   writes res/drawable/ic_lucide_<name>.xml. Commit it so adding an icon later is one command.
2. core/ui/icons/ChefAIIcons.kt — semantic @DrawableRes map (ChefAIIcons.Search, .BookmarkFilled,
   .Trash…). Call sites must never reference raw R.drawable ids.
3. NOTICE entry for Lucide (ISC license — the notice must be retained).
4. In the PR description: a mapping table of every current Icons.* usage to its Lucide
   replacement, so later screen sessions look it up rather than choosing for themselves.
   Inventory the 41 usages with a grep first; surface any with no clean Lucide match rather
   than guessing.

THIS PR IS PURELY ADDITIVE — do not change a single call site. Later screen PRs swap their own
icons while restyling (they rewrite those call sites anyway), and the cleanup PR removes
androidx-material-icons-extended once the last usage is gone. Touching call sites here would
produce a 41-file diff that conflicts with every screen PR and serializes the whole redesign.

Show me the mapping table before you commit.

[shared preamble rules 1-9]
```

---

## PR 3 — Flat component library · **Opus 5** · after PR 1

```
Set model: Opus 5. Branch: redesign/components.

Read the "New component work" section of the handoff README, plus the .btn / .input / .tag /
.seg classes in styles.css, and docs/design/modernist.md from PR 1.

Build core/ui/components/flat/. These composables are used across 16 screens by later sessions,
most run by a smaller model — the API you design here is the main lever on whether those
sessions go smoothly. Prefer obvious, hard-to-misuse signatures over flexible ones.

The five specced in the handoff:
- FlatChip — selectable. Selected: accent fill, bg-colored label, 13px/800, no border.
  Unselected: 2dp outline border, transparent, 13px regular. 44dp min height, 14dp h-padding.
  Disabled 45% opacity.
- FlatSwitch — 48x28dp. On: 2dp accent border + accent fill, 20x20dp bg-colored knob flush
  right, 2dp inset. Off: 2dp outline border, transparent, knob in outline color flush left.
- FlatField — replaces OutlinedTextField. 2dp border (outline; accent focused; accent-700
  error), 0 radius, 48dp min height, label ABOVE the box in 12px muted (not a floating Material
  label), 17dp leading icon slot, error text beneath in 12px accent-700.
- WizardProgressBar — three equal segments, 5dp tall, 4dp gap, completed accent / remaining
  neutral-200, caption beneath 11px uppercase .08em. Replaces the existing one in
  mealplans/ui/components/WizardProgressBar.kt.
- SectionRule — the ruled-grid rhythm: 2dp above a group, 1dp between rows, 2dp below.
  Make this ergonomic; it appears on every list in the design.

Plus, extracted from repeated patterns across the screens:
- FlatButton — primary / secondary / ghost variants, 52dp block form, label FLUSH LEFT not
  centered, optional leading icon, loading state that swaps the label for a progress indicator.
- FlatCheckbox (18dp square, accent fill + bg tick), FlatRadio (18dp, accent border + fill +
  4dp bg inset ring), FlatTag (accent / neutral, non-interactive), SquareAvatar (36dp initials
  tile; the circular avatar variant is the one exception to zero radius).

All interactive elements need the system focus ring (2dp accent, 2dp offset) and a pressed tint
one step along the accent ramp — never a Material ripple with a rounded bound.

Light + dark previews for every component and every state. Flag in the PR description any
component whose dark treatment is a judgment call.

[shared preamble rules 1-9]
```

---

## PR 4 — App shell + profile menu (09) · **Opus 5** · after PR 3

```
Set model: Opus 5. Branch: redesign/shell.

Read handoff README sections "1. Home" (bottom nav spec) and "9. Profile menu", and look at
screenshots/flow-2-account-and-household.png.

This sets the header and scaffold pattern every subsequent screen inherits.

Build:
1. core/ui/navigation/BottomNavigationBar.kt — sage nav surface (LocalChefColors nav role,
   NOT the teal accent), icon + 10px/800 label, accent active tint, zero radius, no Material
   pill indicator. The 4 tabs and their order are already correct. NavigationRail variant too.
2. core/ui/navigation/ChefAITopAppBar.kt — flat header: title 20px/800, back/close icon button
   left, action slot right, no elevation, no Material tonal surface. The handoff shows several
   header shapes (title only; back + title; back + title + subtitle + action; X + title + tag) —
   support them as explicit variants, not a pile of nullable params.
3. auth/ui/UserProfileMenu.kt — screen 09. Replace the Material DropdownMenu surface with the
   panel treatment: 228dp wide, 2dp onBackground border, shadow-lg, zero radius, anchored below
   the avatar. Header block "Signed in as" 10px uppercase muted + display name 15px/800, 2dp
   rule beneath. Rows 48dp min, 18dp Lucide icon + 14px label, 1dp dividers. Logout in
   accent-700. Implement all three UserSession states: Authenticated, Anonymous (Guest /
   Log in / Register / Settings), Loading (single disabled "Loading…" row).
   The 40dp avatar circle is the one exception to zero radius in the whole system.
   In dark, the 2dp border carries the panel — shadows read as noise on a dark ground (handoff
   "Dark mode", point 3 and the note beneath it).

[shared preamble rules 1-9]
```

---

## PR 4b — Shared cards & badges · **Sonnet 5** · parallel with PR 4

```
Set model: Sonnet 5. Branch: redesign/shared-cards.

Read handoff README sections 1, 3, 4 and 5 (these components appear across all four), plus
docs/design/modernist.md.

This PR exists to keep the parallel screen wave conflict-free. Every file in
core/ui/components/ has consumers in two or more of the later screen PRs, so it is restyled
once, here, and no screen session touches this package afterwards.

Restyle to Modernist:
- RecipeListCard.kt — the search-results and recipe-list row. 76x60 grayscale thumbnail, title
  Archivo 14px/800, meta line "10m · Serves 1", bookmark icon right (filled accent when in
  collection, outline muted otherwise), 1dp row dividers, zero radius. Keep the existing
  isInCollection / onSaveToCollection / modifier API.
- LargeCard.kt — the Home "Tonight" hero. Full-width grayscale 16:10 image, two tags
  (accent + neutral), title h3, meta row, full-width primary button.
- InfoChip.kt, RecipePrivacyBadge.kt, SharedByBadge.kt — flat tags: zero radius, accent or
  neutral fill per the .tag classes in styles.css.
- CookedToggleButton.kt — the chef-hat cooked toggle, flat treatment.
- SectionHeaderWithSubtitle.kt — section label 10-12px/800 uppercase .08em + 13px muted
  subtitle, matching the rule rhythm.

Do not change any consumer's call site beyond what a signature change forces, and prefer not to
change signatures at all — seven screen PRs branch off this one. If a component genuinely needs
a new parameter, say so in the PR description so I can tell the dependent sessions.

Light + dark previews for each.

[shared preamble rules 1-9]
```

---

## PR 5 — Settings + auth (10, 07, 08) · **Sonnet 5**

```
Set model: Sonnet 5. Branch: redesign/settings-auth.

Read handoff README sections 7, 8, 10 and the "Interactions & Behavior" bullet on auth.
Screenshot: screenshots/flow-2-account-and-household.png.

Restyle, do not rewrite — these screens' ViewModels and state are correct; you are changing
presentation only.

- settings/ui/SettingsScreen.kt (10): back header + "Settings" 20px/800. Section label
  "Measurement units" in ACCENT 11px/800 uppercase. The 13px muted subtitle and the footnote
  are verbatim from strings.xml — do not reword. Three FlatRadio rows in MeasurementSystemOption
  order with example lines beneath in 12px muted; selected row's label goes 800. Rule rhythm:
  2dp above the group, 1dp between, 2dp below.
- auth/ui/LoginScreen.kt (07): NOT centered — the whole column is flush left, vertically
  centered in the viewport. Accent wordmark 13px/800 uppercase .1em. H2 "Welcome Back" + 14px
  muted subtitle. Two FlatFields (mail icon; lock icon with eye-off trailing toggle).
  Remember-me row: FlatCheckbox, 44dp min row height. Primary 52dp "Log In" with a FLUSH LEFT
  label. Footer: muted prompt + accent bold link.
- auth/ui/RegisterScreen.kt (08): same shell with a back-arrow header carrying the accent
  wordmark. Four FlatFields. Primary 52dp "Create Account".

Field-level validation errors render as 12px accent-700 text beneath the field, and the field
border takes the same color — there is no separate error red in this mono system. Submit buttons
disable while isLoading and swap their label for a progress indicator.

This is the first screen session; if you find yourself fighting a component from
core/ui/components/flat/, say so in the PR rather than working around it locally.

[shared preamble rules 1-9]
```

---

## PR 6 — Plan wizard (13, 14, 15) · **Sonnet 5**

```
Set model: Sonnet 5. Branch: redesign/wizard.

Read handoff README sections 13, 14, 15 and the "Wizard" bullet under "Interactions & Behavior".
Screenshot: screenshots/flow-3-building-a-plan.png.

Restyle mealplans/ui/create/ — the three Wizard*Screen.kt files and their components/.
State and CreateMealPlanViewModel are correct; presentation only.

- 13 Basics: X + "New plan" header, WizardProgressBar step 1, caption "Step 1 of 3 · The Basics"
  (the source's emoji heading "Let's plan your meals! 🍳" is replaced by the caption). Three
  equal 52dp day cells 3/5/7, default 5. Two stacked 52dp meal rows, default dinners only.
  Servings: 2dp-ruled band, 44dp − button, count at 28px/800 with "servings per meal" beneath
  in 12px muted, 44dp + button. Sticky footer primary 52dp "Next".
- 14 Preferences: wrapping FlatChip row in DietaryRestriction order, multi-select. Two stacked
  52dp RecipeSource rows; "My collection only" renders disabled at 45% opacity for the
  collectionTooSmall case, with the explanatory note beneath in 12px muted. Footer: secondary
  "Back" + primary "Next", equal flex.
- 15 Final touches: two ToggleOptionRows using FlatSwitch (title 14px/800, subtitle 12px muted).
  Max-prep-time chips, variety chips in VarietyPreference order. Footer: secondary "Back"
  (flex 1) + primary "Create Plan" (flex 1.4); isSaving disables both and swaps the primary
  label for a progress indicator.

Behavior to preserve: no step validates, Next is always enabled, Back preserves all state, the
plan is only written on "Create Plan", and on save failure it still lands as DRAFT and navigates
to detail.

[shared preamble rules 1-9]
```

---

## PR 7 — Plan detail + shopping list (16, 17) · **Sonnet 5**

```
Set model: Sonnet 5. Branch: redesign/plan-detail-shopping.

FILE OWNERSHIP (PR 12 runs in parallel and also lives in mealplans/): you own ui/detail/,
ui/shoppinglist/ and ui/components/MealPlanMealRow.kt. You do NOT touch ui/MealPlansScreen.kt,
ui/MealPlansViewModel.kt or ui/components/MealPlanCard.kt — PR 12 owns those.
core/ui/components/ is already restyled by PR 4b; do not touch it.

Read handoff README sections 16 and 17 plus their "Interactions & Behavior" bullets.
Screenshot: screenshots/flow-3-building-a-plan.png.

Do NOT touch mealplans/ui/MealPlansScreen.kt — the week-view restructure is PR 12 and will
conflict.

- mealplans/ui/detail/MealPlanDetailScreen.kt + MealPlanBoard.kt (16): header with back,
  "Week plan" 20px/800, date + "Shared by …" 12px muted, printer icon button. Progress:
  "3 of 5 meals cooked" 14px/800 + percentage muted, then ONE 6dp segment per meal (3dp gaps),
  accent when cooked, neutral-200 when not — this replaces the continuous bar; segment count is
  the plan length. Use meal_plan_progress_none/_complete for the 0 and 100% cases. Day rows:
  18dp cooked checkbox, 40dp day column (3-letter day 10px/800 + date 17px/800, today in
  accent), then the meal. Cooked meals struck through at 50% ink. "Plan preferences" disclosure
  row, collapsed by default. Sticky footer primary 52dp "Shopping list · N items" with cart icon.
  Ungenerated state copy comes verbatim from strings.xml.
- mealplans/ui/shoppinglist/ (17): header back + "Shopping list" + ghost "Uncheck all";
  "9 of 24 picked up" 13px muted and a 6dp neutral-200 track with an accent fill at the
  percentage. Grouped by aisle in GrocerySection DECLARATION order, emoji dropped, section
  headers in ACCENT 11px/800 uppercase. Rows: 18dp checkbox, name, right-aligned quantity 13px
  muted. Checked items struck through at 50% ink with "Checked by <name>" beneath in 11px muted
  (shopping_list_checked_by) whenever the checker isn't the current user — check state is shared
  across the household and can change under you. Estimated weights keep the ≈ prefix.
  Empty state copy verbatim from strings.xml.

[shared preamble rules 1-9]
```

---

## PR 8 — Household (11, 12) · **Sonnet 5**

```
Set model: Sonnet 5. Branch: redesign/household.

Read handoff README sections 11 and 12. Screenshot: screenshots/flow-2-account-and-household.png.

- household/ui/HouseholdScreen.kt + HouseholdMemberRow.kt (11): household name as h3 +
  "N members · sharing one plan". Member rows: 36dp SQUARE initials tile (owner = accent fill,
  members = neutral-200), name 14px/800, email or "you" beneath 12px muted, then either an
  accent Owner tag or a small secondary "Remove" button (12px, 6/10 padding). Action pair:
  primary "Invite via link" + secondary "By email", equal flex, 8dp gap. "Pending invites"
  group: email + expiry + ghost "Revoke". Sticky footer: full-width secondary "Leave household"
  in accent-700 text and border — the system is mono, there is no separate destructive red; the
  deep accent step carries that intent. Also implement the empty state (copy verbatim from
  strings.xml: "No household yet" + subtitle + a FlatField for the name + "Create household").
- household/ui/AcceptInviteScreen.kt (12): close (X) header + "Invite". Accent 11px kicker
  "Invited by …", h2 household name, 14px description. Member stack: three 34dp square tiles
  overlapping at -2dp (first accent, rest neutral) + "N people already cooking here". Invite
  code in a 2dp-bordered box, 18px/800, letter-spacing .22em. Sticky footer: primary 52dp
  "Join household" + ghost "Try another code".
  Implement every other HouseholdJoinOutcome state on the same shell with copy swapped from
  strings.xml: invalid, already-in-a-household, joined ("You're in!" → "Go to household"), and
  signed-out ("Sign in to join" → Log in / Register buttons).

[shared preamble rules 1-9]
```

---

## PR 9 — Recipe editor + import (19, 18) · **Sonnet 5**

```
Set model: Sonnet 5. Branch: redesign/recipe-editor-import.

Read handoff README sections 18 and 19. Screenshot: screenshots/flow-4-adding-recipes.png.
This is the largest surface in the redesign — if the diff sprawls, split into 9a (editor) and
9b (import) and say so rather than shipping one unreviewable PR.

- recipes/ui/editor/ (19): header X + "Edit recipe" + neutral Draft tag. ImageUploadContent as
  a full-width 16:9 slot. FlatFields for Title (value 15px/800) and Description (76dp
  multiline). Three-column grid, 8dp gaps: Prep / Cook / Serves, values 800.
  IngredientInput rows: 16dp drag handle (6-dot grip, 35% ink), 42dp qty column (14px/800),
  44dp unit column (13px muted), name (14px, flex), 16dp trash icon at 45% ink. Rule rhythm
  2dp/1dp/2dp. Secondary block "Add ingredient". StepCard: 26dp accent-filled SQUARE number
  badge (bg-colored numeral, 13px/800) + step text 14px/1.5, same rhythm, secondary "Add Step".
  Tags: accent/neutral FlatTags + an outline "+ Add Tags" chip. Sticky footer primary 52dp
  "Save recipe". AutoCompleteInput and the unsaved-changes dialog in EditorDialogs.kt aren't
  drawn — use the profile-menu panel treatment from PR 4 (2dp onBackground border, shadow-lg,
  0 radius) for both.
  Preserve: rows are drag-reorderable via the grip, trash deletes without confirmation, back/X
  prompts if dirty.
- recipes/ui/urlimport/ImportRecipeScreen.kt (18): X header + "Import Recipe" 20px/800 + 14px
  muted lede. FlatField "Recipe URL" with a link icon, border in accent-700 when errorRes != null,
  URL truncates with ellipsis. Error banner: accent-100 fill, 16dp alert icon and 13px text both
  in accent-900. Primary 52dp "Import" (disabled unless canImport), then ghost "Enter it
  manually" rendered only when showManualEntryOption. Preserve the clipboard pre-fill on first
  composition. DROP the "Recently imported" list — it has no backing query in ImportRecipeState;
  file a GitHub issue for it instead.
  For BrowserImportScreen.kt, reuse the same header and error-banner treatment.

DARK MODE FLAG: the import error banner is one of the three cases the handoff says will not
invert mechanically — on a dark ground the fill must come from the DEEP end of the accent ramp
with light text, not a darkened accent-100. Leave it TODO(dark) and call it out in the PR.

[shared preamble rules 1-9]
```

---

## PR 10 — Search + recipes + recipe detail (02, 03, 04, 05) · **Sonnet 5**

```
Set model: Sonnet 5. Branch: redesign/search-recipes.

FILE OWNERSHIP: core/ui/components/ is already restyled by PR 4b — RecipeListCard,
SectionHeaderWithSubtitle, InfoChip, RecipePrivacyBadge and CookedToggleButton are done. Consume
them, do not modify them. If one doesn't fit, say so rather than forking it locally.

Read handoff README sections 2, 3, 4, 5. Screenshot: screenshots/flow-1-browsing-and-planning.png.

- search/ui/ (02, 03): the M3 SearchBar loses its pill — 2dp outline border, magnifier icon,
  placeholder from R.string.placeholder_search, zero radius; accent border and accent icon when
  expanded, trailing X clears and collapses. Browse: two sections (By meal, Popular categories)
  with h6 headers, 2-column card grid, 8dp gaps, 76dp min height, label flush left and
  bottom-aligned 14px/800, 2dp rule between sections.
  CategoryCard.kt currently paints a Brush.linearGradient — Modernist forbids gradients.
  Replace with a flat fill cycling accent-100 / -200 / -300 by index % 3 (same cycling as
  CategoryCardTone.entries), text in accent-900.
  Results: count label 11px/800 uppercase muted, then RecipeListCard rows — 76x60 thumbnail,
  title 14px/800, meta "10m · Serves 1", bookmark icon filled accent when in bookmarkedRecipeIds
  else outline muted, 1dp row dividers. Also style Searching / Empty / Error and the
  offline-results banner with the same muted label treatment — they exist in the ViewModel but
  aren't drawn in the design.
- recipes/ui/RecipesScreen.kt (04): header "Recipes" + primary icon button. Horizontal filter
  chips (All active, Quick & Easy, Vegetarian, Seafood outline). 2-column grid: 4:3 photo, title
  h5, meta line.
  THE FILTER DOES NOT EXIST TODAY. Render the chips per the design and wire only "All"; then
  file a detailed GitHub issue for real filtering — it must specify where the filter state lives
  (RecipesViewModel), how it maps to tags/labels, whether it queries Room or filters in memory,
  and how it interacts with the existing search pipeline. Do not half-build it in this PR.
- recipes/ui/details/RecipeDetailsScreen.kt (05): full-bleed 4:3 hero with back and save icon
  buttons overlaid. Tags row, h2 title, one-line description. Stats bar with 2dp top/bottom
  rules, 3 columns: prep, cook, servings stepper (− / count / +). Ingredients as checklist rows
  (checkbox, name, quantity, 1dp dividers) — tappable to check off while cooking. Instructions
  as numbered rows with an accent-filled number badge. Sticky footer: primary "Add to Meal Plan"
  (flex-grow) + secondary icon button. The servings stepper scales ingredient quantities
  proportionally — that behavior already exists, preserve it.
  This screen also renders from the MEAL_PLAN_RECIPE_DETAIL route with a cooked toggle; don't
  regress that.

DARK MODE FLAG: the search category card tones are one of the three cases the handoff says will
not invert mechanically — the ramp direction flips on a dark ground rather than darkening. Leave
TODO(dark) and call it out in the PR.

[shared preamble rules 1-9]
```

---

## PR 11 — Home, SDUI restyle (01) · **Sonnet 5**

```
Set model: Sonnet 5. Branch: redesign/home-sdui.

FILE OWNERSHIP: core/ui/components/ is already restyled by PR 4b — LargeCard, RecipeListCard and
SectionHeaderWithSubtitle are done. You own home/ui/ only, including ComponentRenderer.kt and
SduiCarousel.kt. Consume the shared components, do not modify them.

Read handoff README section 1. Screenshot: screenshots/flow-1-browsing-and-planning.png.

Home is server-driven: HomeScreen renders an ordered list of ComponentModels through
home/ui/components/ComponentRenderer.kt. The decision for this redesign is to RESTYLE the SDUI
components, not to replace Home with a static layout. No backend contract change, no new
component types.

Map the design onto what the renderer already supports:
- Header: "ChefAI" wordmark 20px/800 + date subtitle, 40dp avatar circle top right.
- The "Tonight" hero — full-width grayscale 16:10 image, two tags (accent + neutral), title h3,
  meta row (time + servings), full-width primary "View Recipe" — is a restyle of LargeCard.
- "This Week": section label + "Plan →" link, then a flush-left day list (day abbreviation +
  title, current day in accent/bold).
- Grocery teaser: label + item count + "View" secondary button.
- 2dp rules between all three sections.

Restyle core/ui/components/LargeCard.kt, InfoChip.kt, home/ui/components/SduiCarousel.kt and
ComponentRenderer.kt. If a design element has no ComponentModel that can carry it, say so in the
PR and file an issue rather than inventing a type or hardcoding it into HomeScreen.

Note the bottom nav is already redesigned in PR 4 — don't touch it.

[shared preamble rules 1-9]
```

---

## PR 12 — Meal Plans week view (06) · **Opus 5** · do not race PR 7

```
Set model: Opus 5. Branch: redesign/meal-plans-week.

FILE OWNERSHIP (PR 7 runs in parallel and also lives in mealplans/): you own
ui/MealPlansScreen.kt, ui/MealPlansViewModel.kt and ui/components/MealPlanCard.kt. You do NOT
touch ui/detail/, ui/shoppinglist/ or ui/components/MealPlanMealRow.kt — PR 7 owns those.
core/ui/components/ is already restyled by PR 4b; do not touch it.

Read handoff README section 6 and its "Interactions & Behavior" bullets.
Screenshot: screenshots/flow-1-browsing-and-planning.png.

This is the one structurally new screen in the redesign, which is why it gets Opus. Today
mealplans/ui/MealPlansScreen.kt renders a LazyColumn of MealPlanCards. The design is a week
view: one row per day, Mon-Sun. That needs ViewModel and data work, not a restyle.

Build:
- Header "Meal Plans" + a segmented control top right ("Just me" / "Family" household toggle).
- Week navigator: prev/next icon buttons + centered date range label.
- Weekly list, one row per day: day abbreviation + date number in a 44dp fixed column, then
  either a planned meal (title bold + time/category meta) or a "+ Add meal" accent link for
  empty days. Current day's label in accent. 2dp row dividers.
- Below the week: grocery summary line ("4 meals planned · 24 ingredients") + full-width primary
  "Generate Grocery List".

Open product questions — resolve by asking me, do not guess:
- The household toggle switches between single-serving and family-scaled plans and quantities.
  The scaling logic is explicitly not specified in the prototype.
- "+ Add meal" opens a meal/recipe picker for that day. That flow is not designed.
- "Generate Grocery List" aggregates across the week. ShoppingListBuilder already does per-plan
  aggregation — determine whether this is the same path or a new one.

Before writing code, give me a short design note covering: how a week maps onto the existing
MealPlan/MealPlanDay model, what happens when a week spans two plans or none, and where the
selected week lives. Then write an ADR in docs/adrs/ following the existing format.

Do not touch mealplans/ui/detail/ or shoppinglist/ — PR 7 owns those.

[shared preamble rules 1-9]
```

---

## PR 13 — Cleanup sweep · **Sonnet 5** · last

```
Set model: Sonnet 5. Branch: redesign/cleanup.

The redesign is landed across PRs 1-12. This session hunts what leaked through.

Sweep the whole app/src/main/java/**/ui/** tree for:
1. Any remaining rounded corners — Surface, Card, Chip, TextField, Button, Dialog, Snackbar,
   BottomSheet. Zero radius everywhere except the user avatar circle.
2. Material ripples with rounded bounds — should be the flat pressed tint from PR 3.
3. Missing focus rings on interactive elements (2dp accent, 2dp offset).
4. Literal colors the PR 1 test didn't catch — including alpha-muted ink, tints passed as
   Color(...) into icons, and hardcoded values in Preview parameters.
5. Leftover androidx.compose.material.icons usages — every call site should be on ChefAIIcons
   from PR 2 by now. Once the last one is gone, remove androidx-material-icons-extended from
   gradle/libs.versions.toml and app/build.gradle.kts, and delete the superseded drawables
   (keep ic_chef_hat_* — brand, not Lucide). Confirm the build still resolves.
6. Emoji still present in strings.xml values and in enum labels (DietaryRestriction,
   VarietyPreference, GrocerySection, MealType, RecipeSource…). Keep the string, strip the glyph.
7. Screens still on Roboto or M3 default typography rather than the Archivo scale.
8. Composables missing a dark preview.

Then produce docs/design/dark-mode-todo.md: a single inventory of every TODO(dark) left in the
codebase, grouped by what kind of decision each needs, with the three "will not invert
mechanically" cases from the handoff called out first. This becomes the scope document for the
dark palette design work.

Report the sweep as a list of findings with file:line before fixing, so I can triage.

[shared preamble rules 1-9]
```
