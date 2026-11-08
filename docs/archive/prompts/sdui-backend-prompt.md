# SDUI Home Screen — Backend Implementation Prompt

> Paste this document into a Claude session in the ChefAI Ktor backend repo.
> It is self-contained and provides everything needed to implement the SDUI home layout endpoint.

---

## 1. Context and Goal

**PocketChef** is an offline-first Android recipe management app. The Home screen uses **Server-Driven UI (SDUI)**: the Android client fetches a JSON layout from the backend and renders whatever components the server describes. The client contains no hardcoded home screen structure — the server owns it entirely.

### What you need to build

A single endpoint:

```
GET /api/v1/home/layout
```

The server returns a `HomeLayoutResponse` containing a list of typed UI components. The Android client deserializes the response and renders each component according to its `type` field.

### MVP scope

- Return a **static JSON layout** (read from a bundled resource file or hardcoded data object).
- Support `ETag` / `If-None-Match` cache revalidation so the client can avoid re-downloading unchanged layouts.
- No personalization logic yet — all users get the same layout.

### Future scope (do not implement now, but design for it)

- Dynamic layout generation per user (dietary preferences, recipe history, A/B experiments).
- Multiple layout variants served from a DB config table.

---

## 2. API Contract

### Endpoint

```
GET /api/v1/home/layout
```

### Request headers

| Header | Required | Description |
|---|---|---|
| `Authorization: Bearer <jwt>` | No | Optional — anonymous users may call this endpoint |
| `If-None-Match: <checksum>` | No | Client sends the cached `layoutChecksum` for revalidation |

### Response — 200 OK

```http
HTTP/1.1 200 OK
Content-Type: application/json
ETag: "<layoutChecksum>"
Cache-Control: max-age=300
X-Min-Schema-Version: 1.0.0
```

Body: `HomeLayoutResponse` JSON (schema below).

### Response — 304 Not Modified

When `If-None-Match` header value matches the current `layoutChecksum`, return an empty body with status `304`. No `Content-Type` or body needed.

### Error responses

| Status | Condition |
|---|---|
| `500 Internal Server Error` | Layout resource unreadable or serialization failure |

---

## 3. JSON Schema

### Top-level `HomeLayoutResponse`

```json
{
  "schemaVersion": "1.0.0",
  "layoutChecksum": "a1b2c3d4...",
  "components": [ ... ]
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `schemaVersion` | `String` | Yes | Semver string, e.g. `"1.0.0"` |
| `layoutChecksum` | `String` | Yes | MD5 hex of the canonical `components` JSON |
| `components` | `List<Component>` | Yes | Ordered list of UI components |

### Component discriminator

Every component object has:
- `"id"`: `String` (required) — stable unique identifier, used by the client for diffing/logging
- `"type"`: `String` (required) — discriminator field

Unknown `type` values must be handled gracefully by the client (rendered as nothing). The backend should never remove or rename an existing `type` without a major version bump.

---

### Component: `section_header`

Renders a section title row, optionally with a subtitle and a "See all" action link.

```json
{
  "type": "section_header",
  "id": "header-for-you",
  "title": "For You",
  "subtitle": "Personalized recipe picks",
  "actionText": "See all",
  "actionUrl": "chefai://screen/recipes?filter=for_you"
}
```

| Field | Type | Required |
|---|---|---|
| `id` | `String` | Yes |
| `title` | `String` | Yes |
| `subtitle` | `String` | No |
| `actionText` | `String` | No |
| `actionUrl` | `String` | No |

---

### Component: `carousel`

A horizontal scrolling list of card components.

```json
{
  "type": "carousel",
  "id": "carousel-for-you",
  "items": [ ... ]
}
```

| Field | Type | Required |
|---|---|---|
| `id` | `String` | Yes |
| `items` | `List<Component>` | Yes |

`items` contains `large_card`, `squared_card`, or `list_card` objects. Never `section_header` or `carousel`.

---

### Component: `large_card`

Full-detail recipe card shown in horizontal carousels. Used for featured/hero items.

```json
{
  "type": "large_card",
  "id": "for-you-paella",
  "recipeId": "uuid-here",
  "title": "Valencian Paella",
  "description": "A classic Spanish rice dish loaded with saffron, fresh seafood, and seasonal vegetables.",
  "imageUrl": "https://...",
  "prepTimeMinutes": 15,
  "cookTimeMinutes": 25,
  "servings": 4,
  "labels": ["Spicy", "Spanish"],
  "tags": ["Dinner"]
}
```

| Field | Type | Required |
|---|---|---|
| `id` | `String` | Yes |
| `recipeId` | `String` (UUID) | No — omit for placeholder/editorial cards |
| `title` | `String` | Yes |
| `description` | `String` | No |
| `imageUrl` | `String` | No |
| `prepTimeMinutes` | `Int` | No |
| `cookTimeMinutes` | `Int` | No |
| `servings` | `Int` | No |
| `labels` | `List<String>` | No |
| `tags` | `List<String>` | No |

---

### Component: `squared_card`

Compact square recipe thumbnail. Used for denser carousels.

```json
{
  "type": "squared_card",
  "id": "italian-margherita-pizza",
  "recipeId": "uuid-here",
  "title": "Margherita Pizza",
  "imageUrl": "https://...",
  "subtitle": "20 min · Easy",
  "tag": "Italian"
}
```

| Field | Type | Required |
|---|---|---|
| `id` | `String` | Yes |
| `recipeId` | `String` (UUID) | No |
| `title` | `String` | Yes |
| `imageUrl` | `String` | No |
| `subtitle` | `String` | No |
| `tag` | `String` | No — nullable, may be omitted or `null` |

---

### Component: `list_card`

Horizontal list-style card with image on the left and detail on the right.

```json
{
  "type": "list_card",
  "id": "seafood-salmon-teriyaki",
  "recipeId": "uuid-here",
  "title": "Grilled Salmon Teriyaki",
  "description": "Glazed salmon fillet with a sweet and savory teriyaki sauce.",
  "imageUrl": "https://...",
  "prepTimeMinutes": 10,
  "cookTimeMinutes": 15,
  "labels": ["Healthy", "Asian"],
  "tags": ["Dinner", "Fish"]
}
```

| Field | Type | Required |
|---|---|---|
| `id` | `String` | Yes |
| `recipeId` | `String` (UUID) | No |
| `title` | `String` | Yes |
| `description` | `String` | No |
| `imageUrl` | `String` | No |
| `prepTimeMinutes` | `Int` | No |
| `cookTimeMinutes` | `Int` | No |
| `labels` | `List<String>` | No |
| `tags` | `List<String>` | No |

---

## 4. Schema Versioning Strategy

The `schemaVersion` field follows **semver** (`major.minor.patch`).

### Minor version bump (backward-compatible)

Example: `1.0.0` → `1.1.0`

- Adding a new optional field with a sensible default.
- Adding a new component `type` (clients render unknown types as nothing).
- Old clients continue working without change.

### Major version bump (breaking)

Example: `1.x.x` → `2.0.0`

- Removing a required field.
- Renaming a `type` discriminator value.
- Changing the semantics of an existing field.
- When a major bump ships: **keep serving the old major version** for at least 6 months or until the minimum supported app version enforces the new schema.

### `X-Min-Schema-Version` response header

Include this header on every response. Clients compare it against their known schema version to decide whether to prompt an app update.

```http
X-Min-Schema-Version: 1.0.0
```

### Client behavior contract (for backend awareness)

- Clients must silently ignore unknown `type` values (map to `Unknown`, render nothing).
- Clients must ignore unknown JSON fields (use `ignoreUnknownKeys = true` in kotlinx.serialization).
- Clients must not crash when optional fields are missing.

---

## 5. Checksum Strategy

The `layoutChecksum` enables efficient cache revalidation.

### Computing the checksum

```
layoutChecksum = MD5( canonicalJson(components) ).toHex()
```

Where `canonicalJson(components)` is the JSON string of the `components` array with:
- Fields in a consistent order (or sorted by key).
- No extra whitespace (compact/minified).
- Components sorted by `id` if ordering is not guaranteed by the data source.

Use a standard MD5 implementation. The checksum does not need to be cryptographically secure — it is only used for equality checks.

### Flow

1. Server computes `layoutChecksum` for every response.
2. Server includes it in the response body and as the `ETag` header value.
3. Client caches both the layout JSON and the checksum locally.
4. On the next request, client sends `If-None-Match: <cached-checksum>`.
5. Server compares the incoming value against the current checksum:
   - **Match** → `304 Not Modified` (empty body).
   - **No match** → `200 OK` with the full updated layout and new `ETag`.

---

## 6. Reference JSON — Full Home Layout

This is the exact layout the Android client currently bundles as a fallback asset (`app/src/main/assets/home.json`). The backend should return a structurally identical response (field names and types must match exactly).

```json
{
  "schemaVersion": "1.0.0",
  "layoutChecksum": "sdui-v1-bundled",
  "components": [
    {
      "type": "section_header",
      "id": "header-for-you",
      "title": "For You",
      "subtitle": "Personalized recipe picks"
    },
    {
      "type": "carousel",
      "id": "carousel-for-you",
      "items": [
        {
          "type": "large_card",
          "id": "for-you-paella",
          "title": "Valencian Paella",
          "description": "A classic Spanish rice dish loaded with saffron, fresh seafood, and seasonal vegetables.",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/paella-closeup.jpeg",
          "prepTimeMinutes": 15,
          "cookTimeMinutes": 25,
          "servings": 4,
          "labels": ["Spicy", "Spanish"],
          "tags": ["Dinner"]
        },
        {
          "type": "large_card",
          "id": "for-you-grilled-chicken",
          "title": "Delicious Grilled Chicken",
          "description": "Juicy lemon-herb marinated chicken grilled to perfection.",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/lem-chicken.jpeg",
          "prepTimeMinutes": 15,
          "cookTimeMinutes": 20,
          "servings": 4,
          "labels": ["Dinner"],
          "tags": ["Healthy", "Quick"]
        },
        {
          "type": "large_card",
          "id": "for-you-thai-green-curry",
          "title": "Thai Green Curry",
          "description": "Aromatic green curry with coconut milk, vegetables, and fragrant Thai basil.",
          "imageUrl": "https://plus.unsplash.com/premium_photo-1713089366140-814130d69933?q=80&w=1740&auto=format&fit=crop",
          "prepTimeMinutes": 15,
          "cookTimeMinutes": 25,
          "servings": 4,
          "labels": ["Spicy", "Asian"],
          "tags": ["Dinner", "Thai"]
        },
        {
          "type": "large_card",
          "id": "for-you-margherita-pizza",
          "title": "Classic Margherita Pizza",
          "description": "Simple and delicious pizza with San Marzano tomatoes, fresh mozzarella, and basil.",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/pizza-marg .jpeg",
          "prepTimeMinutes": 20,
          "cookTimeMinutes": 15,
          "servings": 2,
          "labels": ["Italian", "Vegetarian"],
          "tags": ["Italian", "Dinner"]
        },
        {
          "type": "large_card",
          "id": "for-you-beef-stew",
          "title": "Classic Beef Stew",
          "description": "Hearty slow-cooked beef stew with root vegetables and rich gravy.",
          "imageUrl": "https://images.unsplash.com/photo-1608500218861-01091cdc501e?q=80&w=987&auto=format&fit=crop",
          "prepTimeMinutes": 15,
          "cookTimeMinutes": 20,
          "servings": 4,
          "labels": ["Comfort Food"],
          "tags": ["Slow Cook", "Beef"]
        }
      ]
    },
    {
      "type": "section_header",
      "id": "header-italian-classics",
      "title": "Italian Classics",
      "subtitle": "Pasta, pizza and more"
    },
    {
      "type": "carousel",
      "id": "carousel-italian-classics",
      "items": [
        {
          "type": "large_card",
          "id": "italian-carbonara",
          "title": "Traditional Carbonara Pasta",
          "description": "Rich and creamy carbonara made with guanciale, pecorino, and egg yolks.",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/pasta-carbonara2.jpeg",
          "prepTimeMinutes": 10,
          "cookTimeMinutes": 25,
          "servings": 6,
          "labels": ["Weeknight"],
          "tags": ["Pasta", "Comfort Food"]
        },
        {
          "type": "squared_card",
          "id": "italian-margherita-pizza",
          "title": "Margherita Pizza",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/pizza-marg .jpeg",
          "subtitle": "20 min · Easy",
          "tag": "Italian"
        },
        {
          "type": "squared_card",
          "id": "italian-lasagna",
          "title": "Lasagna Bolognese",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/lasagne.jpeg",
          "subtitle": "50 min · Medium",
          "tag": "Classic"
        },
        {
          "type": "large_card",
          "id": "italian-tuscan-sausage-pasta",
          "title": "Tuscan Sausage Pasta",
          "description": "Penne with Italian sausage, sun-dried tomatoes, and a creamy garlic sauce.",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/pasta-carbonara.jpeg",
          "prepTimeMinutes": 10,
          "cookTimeMinutes": 25,
          "servings": 6,
          "labels": ["Weeknight"],
          "tags": ["Pasta", "Comfort Food"]
        }
      ]
    },
    {
      "type": "section_header",
      "id": "header-quick-healthy",
      "title": "Quick & Healthy",
      "subtitle": "Ready in under 30 minutes"
    },
    {
      "type": "carousel",
      "id": "carousel-quick-healthy",
      "items": [
        {
          "type": "squared_card",
          "id": "quick-salmon-teriyaki",
          "title": "Grilled Salmon Teriyaki",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/salmon-ter.jpeg",
          "subtitle": "25 min · Easy",
          "tag": "Healthy"
        },
        {
          "type": "squared_card",
          "id": "quick-sushi-nigiris",
          "title": "Sushi Nigiris",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/sushi.jpeg",
          "subtitle": "25 min · Medium",
          "tag": null
        },
        {
          "type": "squared_card",
          "id": "quick-shrimp-ceviche",
          "title": "Shrimp Ceviche",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/shrimp-ceviche.jpeg",
          "subtitle": "25 min · Easy",
          "tag": "Fresh"
        },
        {
          "type": "squared_card",
          "id": "quick-med-chicken",
          "title": "Mediterranean Grilled Chicken",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/chicken-med.jpeg",
          "subtitle": "35 min · Easy",
          "tag": "Healthy"
        }
      ]
    },
    {
      "type": "section_header",
      "id": "header-seafood",
      "title": "Seafood",
      "subtitle": "From the ocean to your plate"
    },
    {
      "type": "carousel",
      "id": "carousel-seafood",
      "items": [
        {
          "type": "list_card",
          "id": "seafood-salmon-teriyaki",
          "title": "Grilled Salmon Teriyaki",
          "description": "Glazed salmon fillet with a sweet and savory teriyaki sauce.",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/salmon-ter.jpeg",
          "prepTimeMinutes": 10,
          "cookTimeMinutes": 15,
          "labels": ["Healthy", "Asian"],
          "tags": ["Dinner", "Fish"]
        },
        {
          "type": "list_card",
          "id": "seafood-sushi-nigiris",
          "title": "Sushi Nigiris",
          "description": "Fresh hand-pressed sushi with assorted fish over seasoned rice.",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/sushi.jpeg",
          "prepTimeMinutes": 10,
          "cookTimeMinutes": 15,
          "labels": ["Healthy", "Asian"],
          "tags": ["Dinner", "Fish"]
        },
        {
          "type": "list_card",
          "id": "seafood-shrimp-ceviche",
          "title": "Shrimp Ceviche",
          "description": "Zesty citrus-marinated shrimp with fresh herbs and avocado.",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/shrimp-ceviche.jpeg",
          "prepTimeMinutes": 10,
          "cookTimeMinutes": 15,
          "labels": ["Healthy"],
          "tags": ["Dinner", "Fish"]
        },
        {
          "type": "list_card",
          "id": "seafood-battered-cod",
          "title": "Battered Cod with Lemon",
          "description": "Crispy beer-battered cod fillets served with lemon wedges and tartar sauce.",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/shrimp_ceviche.jpeg",
          "prepTimeMinutes": 20,
          "cookTimeMinutes": 25,
          "labels": ["British"],
          "tags": ["Pub Food", "Dinner", "Fish"]
        }
      ]
    },
    {
      "type": "section_header",
      "id": "header-desserts",
      "title": "Desserts",
      "subtitle": "Sweet treats"
    },
    {
      "type": "carousel",
      "id": "carousel-desserts",
      "items": [
        {
          "type": "squared_card",
          "id": "dessert-choc-chip-cookies",
          "title": "Chocolate Chip Cookies",
          "imageUrl": "https://images.unsplash.com/photo-1499636136210-6f4ee915583e?q=80&w=1064&auto=format&fit=crop",
          "subtitle": "27 min · Easy",
          "tag": "Baking"
        },
        {
          "type": "squared_card",
          "id": "dessert-blueberry-cheesecake",
          "title": "Blue Berry Cheesecake",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/cheesecake.jpeg",
          "subtitle": "27 min · Medium",
          "tag": "Dessert"
        },
        {
          "type": "squared_card",
          "id": "dessert-ghirardelli-cookies",
          "title": "Ghirardelli Cookies",
          "imageUrl": "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/cookies.jpeg",
          "subtitle": "27 min · Easy",
          "tag": "Baking"
        }
      ]
    }
  ]
}
```

---

## 7. Ktor Backend Implementation

### Serialization DTOs

Use `kotlinx.serialization` with a polymorphic `@Serializable` hierarchy. The `type` field is the discriminator.

```kotlin
@Serializable
data class HomeLayoutResponse(
    val schemaVersion: String,
    val layoutChecksum: String,
    val components: List<HomeComponent>,
)

@Serializable(with = HomeComponentSerializer::class)
sealed interface HomeComponent {
    val id: String
    val type: String
}

@Serializable
data class SectionHeaderComponent(
    override val id: String,
    override val type: String = "section_header",
    val title: String,
    val subtitle: String? = null,
    val actionText: String? = null,
    val actionUrl: String? = null,
) : HomeComponent

@Serializable
data class CarouselComponent(
    override val id: String,
    override val type: String = "carousel",
    val items: List<HomeComponent>,
) : HomeComponent

@Serializable
data class LargeCardComponent(
    override val id: String,
    override val type: String = "large_card",
    val recipeId: String? = null,
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val servings: Int? = null,
    val labels: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
) : HomeComponent

@Serializable
data class SquaredCardComponent(
    override val id: String,
    override val type: String = "squared_card",
    val recipeId: String? = null,
    val title: String,
    val imageUrl: String? = null,
    val subtitle: String? = null,
    val tag: String? = null,
) : HomeComponent

@Serializable
data class ListCardComponent(
    override val id: String,
    override val type: String = "list_card",
    val recipeId: String? = null,
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val labels: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
) : HomeComponent
```

You will need a custom `KSerializer` for `HomeComponent` that reads the `type` field and dispatches to the correct subtype. Unknown types should deserialize to an `UnknownComponent` sentinel (or be dropped from the list) rather than throwing.

### Checksum computation

```kotlin
import java.security.MessageDigest

fun computeLayoutChecksum(componentsJson: String): String {
    val digest = MessageDigest.getInstance("MD5")
    val hashBytes = digest.digest(componentsJson.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}
```

Serialize the `components` list to a compact JSON string first, then hash it.

### Service layer

```kotlin
class HomeLayoutService(
    private val json: Json,
) {
    // For MVP: load from a bundled resource file
    fun getHomeLayout(userId: String? = null): HomeLayoutResponse {
        val rawJson = loadLayoutFromResources() // read from resources/home_layout.json
        val components = json.decodeFromString<List<HomeComponent>>(rawJson)
        val canonicalJson = json.encodeToString(components)
        val checksum = computeLayoutChecksum(canonicalJson)
        return HomeLayoutResponse(
            schemaVersion = "1.0.0",
            layoutChecksum = checksum,
            components = components,
        )
    }
}
```

### Route definition

```kotlin
fun Route.homeRoutes(homeLayoutService: HomeLayoutService) {
    get("/api/v1/home/layout") {
        val clientChecksum = call.request.headers["If-None-Match"]
        val layout = homeLayoutService.getHomeLayout(
            userId = call.principal<UserPrincipal>()?.id
        )

        if (clientChecksum != null && clientChecksum == layout.layoutChecksum) {
            call.respond(HttpStatusCode.NotModified)
            return@get
        }

        call.response.headers.append(HttpHeaders.ETag, layout.layoutChecksum)
        call.response.headers.append(HttpHeaders.CacheControl, "max-age=300")
        call.response.headers.append("X-Min-Schema-Version", "1.0.0")
        call.respond(HttpStatusCode.OK, layout)
    }
}
```

### Notes

- Register the route in the application module under the existing authenticated + unauthenticated route structure. Anonymous users must be able to call this endpoint.
- If the project uses `ContentNegotiation` with `kotlinx.serialization`, the `call.respond(layout)` call handles serialization automatically.
- Store the layout JSON file in `src/main/resources/` (or equivalent for the backend project structure).
- Do not hardcode the layout in Kotlin source — keep it in the JSON resource file so it can be updated without a recompile.

---

## 8. Future Personalization Hooks

When personalization is added, the endpoint signature stays the same. The server swaps the returned `components` list based on:

```
GET /api/v1/home/layout
Authorization: Bearer <user-jwt>
```

The resolved user identity (from the JWT) drives:
- Dietary preference filtering (e.g., hide meat dishes for vegetarians)
- Recipe history weighting (surface less-seen recipes)
- A/B experiment cohort assignment (different section ordering per cohort)

The client never needs to change — it just renders whatever the server returns. This is the core value of SDUI.

Optional future query param for debug/testing:

```
GET /api/v1/home/layout?experimentGroup=A
```

### Database config table (future)

When layouts need to be managed without deploys, introduce a `home_layout_configs` table:

```sql
CREATE TABLE home_layout_configs (
    id          UUID PRIMARY KEY,
    version     TEXT NOT NULL,          -- e.g. "1.0.0"
    checksum    TEXT NOT NULL,
    components  JSONB NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

The service queries the active row instead of reading from a file.

---

## Acceptance Criteria

- [ ] `GET /api/v1/home/layout` returns `200 OK` with valid `HomeLayoutResponse` JSON
- [ ] `ETag` header is present and equals `layoutChecksum` in the response body
- [ ] `Cache-Control: max-age=300` header is present
- [ ] `X-Min-Schema-Version` header is present
- [ ] `If-None-Match` with the current checksum returns `304 Not Modified` with empty body
- [ ] `If-None-Match` with a stale/wrong checksum returns `200 OK` with the full layout
- [ ] Anonymous requests (no `Authorization` header) are accepted
- [ ] The returned JSON structure matches the reference JSON in section 6 exactly (field names, nesting, types)
- [ ] Unknown `type` values in the resource file do not crash the server
