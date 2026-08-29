# ADR 013 – Measurement Unit Conversion

**Date:** August 2026
**Status:** Accepted
**Author:** Claude (Opus 5), on behalf of Jose Mucientes
**Related:** [ADR-005](adr-0005-feature-based-package-structure.md),
[ADR-010](adr-010-client-side-recipe-scraping.md)

---

## Context

Recipes carry the measuring culture of wherever they were written. European sources give ingredients
by **weight** — `300 g garbanzos` — and American ones by **volume** — `2 cups flour`. Pocket Chef
imports from arbitrary sites via `:recipe-scraper` (ADR-010), so a single user's library is a mix of
both, and until now there was no way to read a recipe in the units they own measuring tools for.

Three things constrain the answer, and all three are properties of the domain rather than of this
codebase:

1. **Volume→volume and mass→mass conversion is exact.** It needs no knowledge of what is in the cup.
2. **Volume→weight is a per-ingredient estimate, and a shaky one.** Published charts disagree by
   roughly 15% on plain flour alone (120 g/cup to 142 g/cup across reputable sources); scoop-versus-
   spoon technique moves a cup of it by another 30–40 g, which is larger than the disagreement
   between the charts; and for a cup of *chopped* anything, packing dominates and there is no honest
   number at all.
3. **"Cup" is four different units** — US customary 236.59 ml, US legal 240 ml, metric 250 ml,
   imperial (UK) 284 ml. The spread between the smallest and largest standard is about 20%, and
   nothing in a scraped page's markup says which one its author meant.

Surveying how the field handles this found three strategies. NYT Cooking, Serious Eats and King
Arthur don't convert at all — King Arthur's answer to "how many grams is a cup of flour" is a
hand-curated chart of hundreds of ingredients, prefaced by a recommendation to just use a scale.
Paprika and Samsung Food both ship a three-way *Original / Metric / Imperial* toggle that converts
only within a dimension. A third group of dedicated converter apps does cross volume and weight, and
markets ingredient-density awareness as the differentiator — because it is the hard part.

---

## Decision 1: Conversion is a display-time transform, never a write

`recipe_ingredients.quantity` and `.unit` are never touched. Conversion happens on the way to the
screen and nowhere else.

This is the same relationship `RecipeScaling` already has to a recipe, and its KDoc states the
principle: the chosen reading "is a way of reading the recipe, not an edit to it." Three consequences
follow, and all three are the point:

- Switching back to **As written** restores the recipe's own numbers *exactly*, with no accumulated
  rounding.
- No schema migration, no sync-payload field, no backend agreement. `recipe_ingredients.unit` stays
  the free-text `String` it has always been (its `//TODO Make enum` stays open); `UnitNormalizer`
  reads it at display time instead.
- The estimate never becomes the record. A recipe that said `2 cups` still says `2 cups` to every
  other device, and to this one tomorrow.

The transform lives in `core/domain/units/` — pure Kotlin, no Android dependencies — because two
features consume it (`recipes/` details and `mealplans/` shopping list), which is exactly the
threshold ADR-005 sets for moving out of a feature package.

**Order matters: scale first, convert second.** Multiplying an already-converted, already-rounded
value compounds the rounding. Both call sites apply them in that order.

---

## Decision 2: Dimensional conversion is the floor; a curated density table is the exception

Every conversion is volume→volume or mass→mass unless the ingredient appears in
`IngredientDensity` — a table of about 40 pantry staples (some 130 name spellings between them), in grams per US
cup, following King Arthur's chart where it lists the ingredient.

- A cup of **all-purpose flour** becomes `≈ 120 g`, because that is what a European reader wants and
  a tenth-off answer beats no answer.
- A cup of **chicken stock** becomes `240 ml`, because nobody weighs stock and the conversion is
  exact.
- A cup of **chopped walnuts** becomes `240 ml` too: walnuts *are* in the table, but the name carries
  a preparation word (`chopped`, `diced`, `shredded`, `sliced`, `minced`, `grated`, `crushed`,
  `cubed`), which suppresses the density path. A cup of chopped anything weighs whatever it was
  pressed to weigh.

Density-derived amounts are marked `isApproximate` and render with a leading `≈`, so the one class of
figure that rests on an assumption is the one class the user can see resting on it. The alternative —
converting silently — would make the app's least trustworthy numbers indistinguishable from its most.

The table is matched by normalised name with longest-phrase-wins, mirroring `GrocerySectionClassifier`
so that "almond flour" resolves to almond flour rather than to plain flour, and "all-purpose flour,
sifted" resolves at all. A full density database (USDA FoodData Central's portion weights are public
domain) was considered and rejected for now: it adds a data pipeline and a fuzzy-matching problem, and
it is still wrong about chopped onion.

---

## Decision 3: Counting units are never converted, in any mode

`UnitNormalizer` recognises only mass and volume units and returns `null` for everything else —
`clove`, `can`, `jar`, `slice`, `stick`, `sprig`, `pinch`, `dash`, a blank unit, the literal `"unit"`
that `ScrapedRecipeMapper` writes for an amount with no unit, and the long tail nobody anticipated
(`punnet`, `knob`, `glug`). `null` means "leave it alone".

Modelling the *convertible* set rather than the *unconvertible* one is what makes this safe against a
free-text column fed by arbitrary recipe sites: an unrecognised unit degrades to the recipe's own
words rather than to a wrong number.

---

## Decision 4: The US customary cup, stated as an assumption

`MeasurementUnit.CUP` is 236.5882365 ml. Given that a page's markup never says which cup it meant,
this is a guess — the commonest one on the sites the importer sees, and the one that makes
`1 cup → 240 ml` come out matching every published chart once rounded.

Rounding is deliberately coarse and dimension-aware, because a converted amount that reads
`236.588 ml` helps nobody: metric volume rounds to 10 ml above 100 ml and to 0.5 ml below 10; metric
mass keeps a finer grid (5 g above 100 g) because 5 g matters where 5 ml does not; and imperial
amounts snap to the exact set of fractions `QuantityFormat.cooking` has glyphs for, so a converted
value renders `¾ cup` rather than `0.78 cup`.

Every grid has a floor rule: **a real amount is never allowed to round to zero.** 1 g of saffron is
0.035 oz, and snapping that to the nearest measurable fraction gives a flat `0 oz` — which tells the
cook they need none of it. When rounding would zero out a positive amount the unrounded value is let
through instead, so it renders `0.04 oz`: ungainly, but true, and a signal that the unit is the wrong
size for the amount rather than that the amount is nothing. `QuantityFormat.cooking` avoids the same
trap by keeping whole numbers out of its own fraction table.

---

## Decision 5: The preference is device-local

Stored in a plain (unencrypted) DataStore, `chefai_user_prefs` — a sibling of the Keystore-encrypted
`chefai_secure_prefs`, not a change to it. It is not in the sync payload and not keyed by user.

This follows the precedent already set by the meal-plan cooked toggle and `shopping_list_checks`: a
setting describing how *this device* presents things, rather than what the account owns, does not
need to travel. It also means the setting works identically for an anonymous session, which never
pulls.

**Default: `AS_WRITTEN`.** An existing library must not silently change under a user on update —
including recipes they typed in themselves, in the units they chose.

---

## Consequences

**Good**

- A mixed library becomes readable in one system without ever losing the original.
- The shopping list gains something the recipe screen doesn't need: converting *before* aggregation
  means a cup of flour from one recipe and 125 g of it from another sum to one line instead of two
  joined by `+`.
- Zero database, sync or backend surface. The whole feature is reversible by deleting a package.

**Bad / deferred**

- **Step text is untouched**, temperatures included. An instruction saying "bake at 350°F, add 2 cups
  of flour" keeps saying exactly that, even in Metric mode — the same rule `RecipeScaling` follows.
  Steps are free prose from arbitrary sites and regex-rewriting them risks mangling instructions.
  This is the most likely follow-up.
- **No per-recipe override.** Paprika and Samsung Food both offer one; the details screen already
  carries a servings stepper, and a second control alongside it was judged not worth the crowding
  until the profile setting proves insufficient.
- **The density table will need curating.** Forty staples covers the American baking pantry and
  little else; ingredients outside it silently fall back to millilitres, which is correct but not
  always what the user hoped for.
- **The cup assumption is wrong for Australian and British sources**, by 6% and 20% respectively.
  Detecting the source's locale from `recipeExternalUrl` would fix most of it and is not attempted.
- **The shopping list rounds each row before summing it.** Five recipes each wanting 40 ml of milk
  round to `2⅔ tbsp` apiece and total `13.33 tbsp` against a true `13.53` — about 1.4% low, and it
  grows with the number of rows sharing a bucket. Summing raw amounts in base units and rounding once
  would fix it, but that means restructuring the aggregation to group by dimension before choosing an
  output unit. Left alone deliberately: a shopping-list total answers "how much do I buy", where 1.4%
  is below the granularity of what a shop sells.
- **A generic table entry can be matched in preference to no entry at all.** "Condensed milk" resolves
  through the bare `milk` entry (242 g/cup) and is really nearer 306 g/cup. The longest-phrase match
  is doing its job; the table simply lacks the more specific row. Adding entries is the fix, and the
  approximation is marked `≈` either way.
