# Modernist — the ChefAI design system

The app is being restyled onto **Modernist**: flat, architectural, organized by ruled lines rather
than by cards and shadows, Archivo throughout, zero corner radius.

This document is the contract for that work. The source of truth is the design handoff
(`~/Projects/design_handoff_chefai_redesign with_dark_mode/`: `README.md`, `styles.css`, and the
19-screen prototype `ChefAI Redesign.dc.html`); this is its Compose translation. Where they
disagree, the handoff wins for *what it looks like* and this document wins for *how to write it*.

The redesign lands over a series of PRs, one flow at a time. **The theme shipped first, on
purpose.** Every screen PR after it is a matter of picking roles out of the theme — if you find
yourself typing a color, a font size, or a corner radius, stop and read § Adding a token.

- **Foundation (this PR):** `core/ui/theme/` — color schemes, `LocalChefColors`, Archivo type
  scale, zero-radius `Shapes`, plus the `NoLiteralColorsTest` guardrail.
- **Components (second PR):** `core/ui/components/flat/` — the five custom components plus the
  ones extracted from repeated patterns across the screens. See § 8.
- **Not yet done:** all 19 screens. Screens today are stock Material 3 on the new theme, so they
  look half-converted. That is expected.

---

## 1. Tokens

### 1.1 Roles that Material 3 has a slot for

Read these from `MaterialTheme.colorScheme`. The hex values are the **light** resolution of each
role — they are what the theme resolves to, never what you type.

| Design role | Light value | M3 slot | Notes |
| --- | --- | --- | --- |
| Background (ground) | `#F3F2F2` | `background`, `surface` | One ground for the whole app. There are no cards. |
| Ink | `#201E1D` | `onBackground`, `onSurface` | Full-strength text. |
| Muted ink | `#6A6868` | `onSurfaceVariant` | Captions, meta lines, subtitles, field labels. |
| Accent (fills) | `#0A8080` | `primary` | `onPrimary` is the ground, not white. **Fills and large type only** — see below. |
| Accent (text) | `#0A6363` | `chefColors.accentText` | Accent on small text: section labels, links, the day highlight. |
| Accent tinted fill | `#E3F0EF` / `#053232` | `primaryContainer` / `onPrimaryContainer` | accent-100 fill, accent-900 text. |
| Divider / rule | `#9F9D9D` | `outline` | The 2dp section rule. |
| Hairline | `#C9C8C8` | `outlineVariant` | The 1dp row rule. |
| Neutral fill | `#EAE7E7` | `surfaceVariant` | neutral-200 — unselected tiles, thumbnails, empty slots. |
| Sunken fill | `#EAE9E9` | `surfaceContainer` | `--color-surface`: panel and input grounds. |
| Destructive | `#0A6363` | `error` | **There is no error red.** accent-700 carries destructive and invalid states. |
| Scrim | `#2D2B2B` | `scrim` | Dialog backdrops, at 50%. |

Two deliberate choices in that table:

- **`surfaceTint` is the surface color itself.** Material composites `surfaceTint` over the
  surface as elevation rises, so tinting *with the ground* makes tonal elevation a no-op and an
  elevated `Surface` stays exactly the ground. The rules remain the only depth cue.
  Do **not** "disable" it with `Color.Transparent` — that is `Color(0, 0, 0, 0)`, and Material's
  `surfaceTint.copy(alpha = …)` turns it into *black* at that alpha, dimming every elevated
  surface. `MainActivity`'s `Surface(tonalElevation = 5.dp)` rendered the ground at `#DAD9D9`
  instead of `#F3F2F2` that way.
- **`secondary` and `tertiary` are accent steps** (accent-700 and accent-800), not new hues. The
  system is mono. If a screen needs a second color, it needs a different *step*, not a different
  *hue*.
- **Accent as text is a different role from accent as fill.** The brand accent-600 measures
  4.26:1 on the ground: fine for a fill with ground-colored text on top, fine for large type, but
  under the 4.5:1 AA floor for anything smaller. Nearly every accent *string* in this design is
  11–14px — the Settings and shopping-list section labels, "Plan →", "+ Add meal", the
  Accept-invite kicker, the current-day highlight — so those take `chefColors.accentText`
  (accent-700, 6.31:1). `colorScheme.primary` stays the fill.

  Measured against `#F3F2F2`: ink 14.86:1 · muted ink 4.96:1 · accent-700 6.31:1 · accent-600
  4.26:1 · the rejected 55% muted ink 3.66:1.

### 1.2 Roles that Material 3 has no slot for

Read these from `MaterialTheme.chefColors` (`core/ui/theme/ChefColors.kt`).

| Token | Light value | Used for |
| --- | --- | --- |
| `chefColors.accent.s100 … .s900` | `#E3F0EF` → `#053232` | Tinted fills, deep-accent text, the search cards' `index % 3` cycle. |
| `chefColors.neutral.s100 … .s900` | `#F8F4F4` → `#2D2B2B` | Neutral tiles, member initials, disabled segments, thumbnails. |
| `chefColors.accentText` | `#0A6363` | Accent as text or an icon on the ground. Accent-700, not the brand accent-600. |
| `chefColors.navSurface` | `#E7F0E0` | The bottom-nav bar. A light sage, deliberately *not* the teal. |
| `chefColors.sectionRuleWidth` | `2.dp` | The heavy rule. |
| `chefColors.rowRuleWidth` | `1.dp` | The hairline between rows. |
| `chefColors.cornerRadius` | `0.dp` | Rarely needed directly — the theme's `Shapes` already covers components. |

The ramps are generated in OKLCH on one shared lightness scale, so the same step of any ramp
matches the others in visual value: `accent.s200` and `neutral.s200` are equally light. That is
what makes `accent.steps[index % 3]` legible as a set.

The two widths and the radius live here, with the colors, because in this system a rule weight is
a token exactly as much as a color is — the layout is *built* out of 2dp and 1dp lines. They are
theme-invariant: a 2dp rule is 2dp on any ground. Only the rule's **color** changes with theme.

### 1.3 Spacing

There is no spacing token object; the CSS scale maps onto the existing `R.dimen.padding_*`
resources and plain `dp` literals, which are not colors and are not policed.

| CSS | dp | Typical `R.dimen` |
| --- | --- | --- |
| `--space-1` | 4 | `padding_extra_small` |
| `--space-2` | 8 | `padding_small` |
| `--space-3` | 12 | — |
| `--space-4` | 16 | `padding_medium` |
| `--space-6` | 24 | — |
| `--space-8` | 32 | — |

**Minimum hit target: 44dp.** Chips, switch rows, list rows with controls and menu items are all
sized to it in the design. Primary block buttons are 52dp.

### 1.4 Shape

`ChefShapes` sets `extraSmall` through `extraLarge` to `RoundedCornerShape(0.dp)`. Every Material
component that resolves its shape from the theme is square without a `shape =` argument. **Passing
`RoundedCornerShape(0.dp)` at a call site is duplicating the theme, not obeying it** — delete it.

The one exception in the entire design is the user avatar, which is a circle because it is an
avatar, not because it is a container. Use `CircleShape` there, explicitly.

---

## 2. Type

One family — Archivo, loaded through the existing Google Fonts provider in 400 / 600 / 800.
Headings and body are the same typeface; they differ by weight and tracking. Headings and anything
emphasized are **800 (ExtraBold)**; there is no semibold middle ground in this design.

| Slot | Size / weight | Tracking | Design role |
| --- | --- | --- | --- |
| `displayLarge` | 42 / 800 | −0.015em | h1 — unused in this batch |
| `displayMedium` | 32 / 800 | −0.015em | h2 — recipe detail title, invite household name |
| `displaySmall` | 25 / 800 | −0.015em | h3 — "Tonight" recipe title, household name |
| `headlineLarge` | 20 / 800 | −0.015em | h4 — screen titles ("Settings", "Recipes") |
| `headlineMedium` | 18 / 800 | −0.015em | wordmark, invite code |
| `headlineSmall` | 16 / 800 | −0.015em | h5 — grid card titles |
| `titleLarge` | 17 / 800 | −0.015em | list card titles |
| `titleMedium` | 15 / 800 | −0.015em | profile display name, editor title value |
| `titleSmall` | 13 / 800 | +0.08em | h6 — section headers, **uppercase** |
| `bodyLarge` | 14 / 400 | 0 | body, list row titles |
| `bodyMedium` | 13 / 400 | 0 | secondary body, meta lines, quantities |
| `bodySmall` | 12 / 400 | 0 | muted captions, examples, footnotes |
| `labelLarge` | 14 / 800 | 0 | button labels |
| `labelMedium` | 11 / 800 | +0.08em | section labels, kickers, "14 RESULTS", **uppercase** |
| `labelSmall` | 10 / 800 | +0.1em | nav labels, "SIGNED IN AS", **uppercase** |

Two rules a `TextStyle` cannot carry, so they belong at the call site:

1. **Uppercase is not automatic.** `titleSmall`, `labelMedium` and `labelSmall` carry an uppercase
   label's tracking but not the transform. Pass already-uppercased text —
   `stringResource(R.string.x).uppercase()` — rather than adding a second style.
2. **Color is never baked into a style.** Muted text is `bodySmall` *in*
   `colorScheme.onSurfaceVariant`. There is no "muted style".

A row title that goes bold when selected (Settings, the wizard) changes **weight**, not style:
`style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.ExtraBold else
FontWeight.Normal`.

---

## 3. Rule rhythm

This system organizes by rule weight. Shadows and fills do not separate content; lines do. Every
list group in all 19 screens follows the same three-line rhythm:

```
━━━━━━━━━━━━━━━━━━━━━━  2dp  outline          ← above the first row of a group
   row
────────────────────────  1dp  outlineVariant  ← between rows
   row
────────────────────────  1dp
   row
━━━━━━━━━━━━━━━━━━━━━━  2dp  outline          ← below the last row
```

In Compose:

```kotlin
HorizontalDivider(
    thickness = MaterialTheme.chefColors.sectionRuleWidth,
    color = MaterialTheme.colorScheme.outline,
)
items.forEachIndexed { index, item ->
    if (index > 0) {
        HorizontalDivider(
            thickness = MaterialTheme.chefColors.rowRuleWidth,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
    Row(item)
}
HorizontalDivider(
    thickness = MaterialTheme.chefColors.sectionRuleWidth,
    color = MaterialTheme.colorScheme.outline,
)
```

Rules run **full-bleed**, edge to edge — they are not inset to match the row's content padding.
The row's own horizontal padding sits inside them.

Do not write this out by hand. `RuledGroup` in `core/ui/components/flat/SectionRule.kt` applies
the whole rhythm for you, and `SectionRule()` / `RowRule()` are there for the `LazyColumn` case
where the rows are not children of one `Column`. See § 8.

---

## 4. Interaction states

### 4.1 Pressed and hovered

The handoff: *"a hover/pressed tint one step along the accent ramp. Never a Material ripple with a
rounded bound."* So: no ripple, a flat tint, and the tint comes from a ramp step — never from
alpha over the ground.

| Surface | Rest | Hover | Pressed |
| --- | --- | --- | --- |
| Primary button | `primary` (accent-600) | `accent.s600` | `accent.s700` |
| Secondary / outlined | transparent, 2dp `outline` border | `neutral.s200` | `neutral.s300` |
| Ghost / link | transparent, `primary` label | `accent.s100` | `accent.s200` |
| List row | ground | `neutral.s100` | `neutral.s200` |

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val pressed by interactionSource.collectIsPressedAsState()

Row(
    modifier = Modifier
        .heightIn(min = 44.dp)
        .background(if (pressed) MaterialTheme.chefColors.neutral.s200 else Color.Unspecified)
        .clickable(
            interactionSource = interactionSource,
            indication = null, // no ripple: the tint is the feedback
            onClick = onClick,
        ),
)
```

`Color.Unspecified` in `background()` draws nothing, which is what "the ground shows through"
means here. It is not a color literal and the guardrail does not flag it.

### 4.2 Focus ring

`:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 2px; }` — a 2dp accent
ring, drawn **2dp outside** the component's bounds, on every interactive element.

Compose has no `outline-offset`, so the ring is drawn manually, outside the content bounds. This
shipped as `Modifier.modernistFocusRing` in `core/ui/components/flat/FlatInteraction.kt`, together
with `Modifier.flatClickable`, which bundles the ring *and* the pressed tint *and* the
no-indication `clickable` into the one modifier every tappable row should use:

```kotlin
/**
 * The system focus ring: 2dp accent, 2dp outside the bounds. The parent must not clip.
 */
fun Modifier.modernistFocusRing(
    focused: Boolean,
    color: Color,
    width: Dp = 2.dp,
    offset: Dp = 2.dp,
): Modifier = drawWithContent {
    drawContent()
    if (!focused) return@drawWithContent
    val stroke = width.toPx()
    val gap = offset.toPx()
    drawRect(
        color = color,
        topLeft = Offset(-gap - stroke / 2, -gap - stroke / 2),
        size = Size(size.width + 2 * (gap + stroke / 2), size.height + 2 * (gap + stroke / 2)),
        style = Stroke(width = stroke),
    )
}
```

Prefer the `Modifier.modernistFocusRing(interactionSource)` overload — it reads both the focus
state and the accent itself, so there is nothing to pass wrong. Because the ring is drawn outside
the bounds, leave 2–4dp of breathing room around focusable elements in a tight grid, or it will be
clipped by a `Modifier.clip` / scroll container upstream.

### 4.3 Disabled

45% opacity on the **whole composable** (`Modifier.alpha(0.45f)`), matching `.btn:disabled`. This
is the one place alpha is correct: it is standing in for "this thing is inactive", not for a
color. Do not compute a paler color instead.

---

## 5. Dark-mode discipline

**Dark mode is not designed.** It compiles and renders; it is not finished, and the handoff is
explicit that inventing a plausible-looking dark palette is worse than leaving an obvious hole,
because a plausible placeholder is never found again.

So, three rules:

1. **Never write a color literal in a screen.** This is the whole reason the theme landed first. A
   literal is a value the dark theme cannot reach, and nobody notices until someone opens that
   screen in dark mode months from now. `NoLiteralColorsTest` enforces it.
2. **Never mute ink with alpha.** `onBackground.copy(alpha = 0.6f)` looks identical to
   `onSurfaceVariant` on the light ground, which is exactly why it is dangerous: alpha mixes ink
   *toward whatever is behind it*, so on a dark ground the same 0.6f collapses the contrast
   instead of preserving it. The muted role is a **resolved color per theme**
   (`#6A6868` light — ink at 65% over the ground, 4.85:1; the CSS's literal 55% measures 3.66:1
   and fails AA on the 11–13px captions it is used for). `NoLiteralColorsTest` enforces this too.
3. **Leave what is undesigned obviously undesigned.** Dark tokens with no answer are
   `Color(0xFFFF00FF)` — magenta is not a Modernist color and never will be. Magenta on screen in
   dark mode is an unset token, not a styling bug. Grep `DarkUnset` and `TODO(dark)`.

### What is unfinished, and why it will not invert mechanically

| Token | State | Why a mechanical inversion is wrong |
| --- | --- | --- |
| `chefColors.accent.*` (dark) | **magenta** | Light tinted fills come from the *shallow* end (accent-100/200/300) with accent-900 text. On a dark ground the fill must come from the *deep* end with light text. The ramp direction **flips**; it does not just darken. Darkening each step one-for-one gives fills that vanish into the ground and text that fails contrast. Affects the Search category cards and the Import error banner. |
| `chefColors.navSurface` (dark) | **magenta** | The sage has no dark counterpart at all. It is a lighter treatment chosen to set the nav bar apart from the ground; what plays that role on a dark ground is open — possibly not a tint at all, possibly a rule. |
| `colorScheme.outline` (dark) | provisional `#716F6F` | The 2dp rules carry the entire layout. In dark they must read *as assertive as they do in light*, which is not the same relative contrast. Tune this by eye; do not derive it. |
| `chefColors.accentText` (dark) | **magenta** | Same blocker as the ramp: the legible accent step on a dark ground is not accent-700, and which step it *is* depends on where the ramp lands. |
| `chefColors.neutral.*` (dark) | provisional, inverted 100↔900 | A mono ramp does invert cleanly, so this one is filled in — but it has not been contrast-checked at the steps that carry text. |
| `colorScheme.primary` (dark) | provisional accent-400 | The full-strength accent is too dark to sit on a dark ground, so dark uses accent-400 and flips `onPrimary` from light to dark. That inverts the accent/ink relationship, which is a design call. |
| Shadows | — | `--shadow-sm/md/lg` are tuned to the light ground and read as noise on a dark one. The profile-menu panel uses a shadow *plus* a 2dp border; in dark, the border should do the work alone. |

When you hit one of these while building a screen: build the light state exactly as specified,
make sure dark still renders, and leave the dark value alone. Do not fix it in passing.

---

## 6. The guardrail

`app/src/test/java/com/tenmilelabs/chefai/core/ui/theme/NoLiteralColorsTest.kt` walks
`app/src/main/java/**/ui/**` and fails the build on:

1. `Color(0x…)` literals, and
2. alpha applied to an ink role — `onBackground` / `onSurface` / `onSurfaceVariant` / `Ink`
   followed by `.copy(alpha = …)`.

`core/ui/theme/` is allowlisted; it is where the palette is defined. Comments are stripped before
matching, so a hex in a KDoc is fine. A third test asserts the walk actually finds files and that
the allowlisted package still exists, so a source move cannot silently switch the guardrail off.

Failure messages carry the role table and the fix. If you are reading one: the answer is a role,
not an allowlist entry.

### Adding a token

You will occasionally need a color the theme does not have. In order of preference:

1. **An existing ramp step.** `chefColors.accent.s700`, `chefColors.neutral.s300`. Most
   "new" colors in this design are a step on a ramp that already exists, and the ramp says what
   the color *is*.
2. **An M3 slot that genuinely means what you need.** `surfaceVariant`, `outlineVariant` and
   `surfaceContainer` cover more than people expect.

   Do **not** borrow a slot because its light value happens to match. `inversePrimary` is
   "primary on an inverted surface" — it is accent-300 in light and accent-600 in dark, so code
   that means "accent-300" and writes `inversePrimary` silently changes color between themes and
   breaks the day someone styles a Snackbar. If you mean a ramp step, name the ramp step.
3. **A new field on `ChefColors`.** Add it to the data class, give it a real value in
   `LightChefColors`, and give it `DarkUnset` + a `TODO(dark)` in `DarkChefColors` explaining what
   decision is missing. Update the table in § 1.2.

What you do not do is allowlist a call site.

### Known gaps in the guardrail

Named constants (`Color.White`, `Color.Black`, `Color.Transparent`) **are** flagged, but three
files are grandfathered: the pre-redesign photo scrims in `LargeCard` and `RecipeListCard`, and
`CategoryCard`'s gradient. White text over a black scrim on grayscale imagery is genuinely
theme-invariant; it was simply never expressed as a role.

That list is an exact set, not a prefix allowlist. Adding a named color anywhere else fails, and
clearing the last one out of a listed file *also* fails, with a message telling you to delete the
entry — so it can only shrink. `LargeCard`/`RecipeListCard` come off it in the shared-cards PR,
`CategoryCard` in the search PR when its gradient becomes a flat ramp fill.

Still not caught: a color assembled at runtime (`lerp`, `compositeOver`, `Color(someInt)`). If you
are computing a color on a screen, you are building a token — put it in `ChefColors`.

---

## 7. Worked example: building a screen in this system

Settings (screen 10 in the handoff) is the smallest screen and exercises everything: a section
label, muted supporting copy, a ruled radio group with a selected-weight change, and a footnote.
`settings/ui/SettingsScreen.kt` has **not** been converted yet — this is what converting it looks
like.

The design:

> Back header + "Settings" 20px/800. Section label "Measurement units" in **accent** 11px/800
> uppercase, then the 13px muted subtitle verbatim. Three radio rows in `MeasurementSystemOption`
> order, each with its example line beneath in 12px muted. Selected row's label goes Archivo 800.
> Radio dot: 18dp, accent border + accent fill + 4dp ground inset ring. Rules: 2px above the
> group, 1px between rows, 2px below. Footnote verbatim below the group, 12px muted.

**Step 1 — take the copy and the ordering from the source, not from the design.** The handoff was
built against the real `strings.xml` and the real enums; where it shows text, that text is already
in the app. `MeasurementSystemOption.entries` is the row order. Emoji in string resources are
dropped in this design (Modernist does not use emoji) — keep the string, strip the leading glyph.

**Step 2 — translate each line of the design to a role.** Nothing is invented:

| Design says | You write |
| --- | --- |
| "Settings" 20px/800 | `typography.headlineLarge` |
| section label, accent, 11px/800 uppercase | `typography.labelMedium`, `chefColors.accentText`, `.uppercase()` |
| 13px muted subtitle | `typography.bodyMedium`, `colorScheme.onSurfaceVariant` |
| row label | `typography.bodyLarge`, `colorScheme.onBackground` |
| selected row label goes 800 | same style, `fontWeight = FontWeight.ExtraBold` |
| 12px muted example line | `typography.bodySmall`, `colorScheme.onSurfaceVariant` |
| accent radio dot | `colorScheme.primary`, ring inset in `colorScheme.background` — a *fill*, so the brand step is right |
| 2px above / 1px between / 2px below | `chefColors.sectionRuleWidth` + `outline`, `rowRuleWidth` + `outlineVariant` |
| no corner radius anywhere | nothing — the theme already did it |

**Step 3 — write it.** Stateless content composable, state hoisted, one `onAction` lambda, exactly
as the rest of the app does it:

```kotlin
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.settings_measurement_units_title).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            // accentText, not primary: this is 11px, below the size where accent-600 clears AA.
            color = MaterialTheme.chefColors.accentText,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.settings_measurement_units_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(16.dp))

        // 2dp above the group.
        HorizontalDivider(
            thickness = MaterialTheme.chefColors.sectionRuleWidth,
            color = MaterialTheme.colorScheme.outline,
        )
        Column(Modifier.selectableGroup()) {
            MeasurementSystemOption.entries.forEachIndexed { index, option ->
                if (index > 0) {
                    // 1dp between rows.
                    HorizontalDivider(
                        thickness = MaterialTheme.chefColors.rowRuleWidth,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                MeasurementSystemRow(
                    option = option,
                    isSelected = uiState.measurementSystem == option.system,
                    onSelect = { onAction(SettingsAction.MeasurementSystemChanged(option.system)) },
                )
            }
        }
        // 2dp below the group.
        HorizontalDivider(
            thickness = MaterialTheme.chefColors.sectionRuleWidth,
            color = MaterialTheme.colorScheme.outline,
        )

        Text(
            text = stringResource(R.string.settings_measurement_units_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}
```

And the row — 44dp minimum, flat pressed tint, no ripple, selection carried by weight:

```kotlin
@Composable
private fun MeasurementSystemRow(
    option: MeasurementSystemOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(
                if (pressed) MaterialTheme.chefColors.neutral.s100 else Color.Unspecified,
            )
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlatRadioDot(selected = isSelected)          // 18dp; see below
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(option.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(option.exampleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

The radio dot is the one genuinely custom piece — Material's `RadioButton` cannot do "accent fill
with a 4dp ground-colored inset ring", and it brings a ripple with it:

```kotlin
@Composable
private fun FlatRadioDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .border(
                width = 2.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = CircleShape,
            )
            .padding(2.dp)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Unspecified
                },
                shape = CircleShape,
            ),
    )
}
```

(A dot is round for the same reason the avatar is: it is a dot, not a container. The zero-radius
rule is about boxes.)

**Step 4 — previews, light and dark, both.** Dark will look wrong in places. That is information,
not a bug to fix in your PR — if something in dark is *magenta*, it is an unset token from § 5,
and it stays magenta until dark mode is designed.

```kotlin
@Preview(name = "Settings — light")
@Preview(name = "Settings — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsPreview() {
    ChefAITheme { SettingsContent(uiState = SettingsUiState(), onAction = {}) }
}
```

Every preview must be wrapped in `ChefAITheme`. `LocalChefColors` has no default value on purpose
— a preview that forgets the theme fails loudly with "No ChefColors provided" instead of quietly
rendering the light ramp inside a dark screen.

**Step 5 — run the guardrail.**

```
./gradlew :app:testDebugUnitTest
```

---

## 8. The component library

`core/ui/components/flat/` holds every piece of the system that Material 3 cannot express at zero
radius. **Reach for these before writing a `Button`, `Chip`, `Switch`, `OutlinedTextField`,
`Checkbox`, `RadioButton` or a hand-rolled `HorizontalDivider`.** If one of them does not fit,
that is worth a conversation — it is more likely that the screen is drifting off-system than that
the component is wrong.

| Composable | Replaces | Notes |
| --- | --- | --- |
| `RuledGroup` / `SectionRule` / `RowRule` | hand-written dividers | § 3's rhythm in one call. Two forms: `RuledGroup(items) { item -> … }` and a `RuledGroup { row { } }` scope for heterogeneous rows. Pass `key = { it.id }` for a list whose items can change. |
| `FlatBlockButton` / `FlatButton` | `Button` / `OutlinedButton` / `TextButton` | `Primary` / `Secondary` / `Ghost` / `Destructive`. Block form is 52dp, full width, **label flush left**; inline wraps and centers. `loading = true` swaps the label for a spinner. |
| `FlatChip` | `FilterChip` | Selected = accent fill + ExtraBold ground label; unselected = 2dp rule. 44dp, 14dp padding. |
| `FlatSwitch` | `Switch` | 48×28 visual in a 44dp hit target. |
| `FlatField` | `OutlinedTextField` | Label above the box, border carries focus/error, `errorText` alone drives the error state. `minLines > 1` makes it multiline. |
| `FlatCheckbox` / `FlatRadio` | `Checkbox` / `RadioButton` | 18dp. Pass `onCheckedChange`/`onClick` as `null` when the enclosing row is the control. |
| `FlatTag` | `AssistChip`, `Badge` | Non-interactive by construction — a tappable tag is a `FlatChip`. |
| `SquareAvatar` / `CircleAvatar` | — | 36dp square initials tile; the 40dp circle is the one exception to zero radius. `avatarInitials(name)` derives the letters. |
| `WizardProgressBar` | the old `LinearProgressIndicator` one | One segment per step, `currentStepIndex` is **zero-based**. |
| `Modifier.flatClickable` / `flatToggleable` / `flatSelectable` | `clickable` / `toggleable` / `selectable` + ripple | § 4. The row primitives: no ripple, flat pressed tint, focus ring. Use the toggle/select ones whenever the row *has* a state — selection here is carried by fill and weight, so a screen reader gets nothing unless the state is in the semantics. **Apply any `border` after them**, or the pressed tint paints over it. |
| `Modifier.modernistFocusRing` | — | § 4.2. 2dp accent, 2dp outside the bounds. Already applied by the three above. |

Every one of them carries light **and** dark previews of every state. Dark is still un-designed;
the dark previews exist to prove the components render and to keep the un-designed tokens visible.

### Where the components deviate from a literal reading of the handoff

Three pressed/tinted fills read a Material role instead of the `chefColors` ramp step the light
design names, because the accent ramp is `DarkUnset` (magenta) in dark and a control that flashes
magenta on every tap is noise rather than the honest signal an un-designed *resting* color is.
Each resolves to the specified light value or within one ramp step of it:

| Where | Design says | Code reads | Light resolves to |
| --- | --- | --- | --- |
| Primary button pressed | `accent-700` | `colorScheme.secondary` | `accent-700` — exact |
| Ghost button pressed | `accent-200` | `colorScheme.primaryContainer` | `accent-100` — the hover step |
| `FlatTag` accent tone | `accent-100` / `accent-800` | `primaryContainer` / `onPrimaryContainer` | `accent-100` / `accent-900` |

Everything else that needs a ramp step reads `chefColors.neutral.*`, which **is** filled in for
dark. When the dark accent ramp is designed, revisit this table — these three may well go back to
the literal steps.
