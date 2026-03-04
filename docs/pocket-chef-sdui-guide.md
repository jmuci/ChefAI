# Server-Driven UI for Pocket Chef — Implementation Guide
*For use with Claude/Cursor to implement the feature. Context: Monstro R2 demo prep.*

---

## What We're Building

Transform the Pocket Chef home page so the **layout and component order is controlled by a JSON payload** rather than hard-coded UI. The server (or a local JSON file) returns a list of typed components; the Android client resolves each type to a Jetpack Compose composable.

**3 components for the MVP:**
- `section_header` — title + optional subtitle
- `carousel` — horizontal scrolling row of recipe cards
- `squared_card` — individual recipe tile (used inside carousels)

**Minimum viable version for the demo:** The JSON can be served from a local `assets/home.json` file instead of a real API endpoint. The architectural pattern is identical — the interviewer cares about the sealed class hierarchy, the component resolver, and the design decisions, not whether the data travels over the network. You can wire it to a real endpoint later.

---

## How This Maps to Spotify's HubFramework

Use these comparisons when explaining the pattern in the interview:

| HubFramework (Spotify iOS, ~2017) | This implementation (Jetpack Compose, 2024) |
|---|---|
| `HUBComponent` protocol | `@Composable` function per component type |
| `HUBComponentModel` (title, subtitle, imageData, customData) | Sealed `ComponentModel` data class |
| `HUBComponentFactory` — resolves component by name | `when (component)` block in `ComponentRenderer` |
| Content loading chain (operations mutate view model builder) | Repository → ViewModel → `StateFlow<HomeUiState>` |
| Layout traits (FullWidth, Stackable, etc.) | `Modifier` + `LazyColumn` handles this natively |
| JSON serialization → backend controls rendering | `@JsonClassDiscriminator("type")` sealed class |
| `HUBComponentWithChildren` (carousel → cards) | `Carousel` model contains `List<SquaredCard>` |

**Key insight to mention in the demo:** Compose made this pattern dramatically simpler to implement. HubFramework had to build "UI = f(state)" on top of UIKit, which fights it. In Compose, that's the default paradigm — composables ARE pure functions of state. The server-driven part is just routing that state through a JSON discriminator instead of hard-coding it.

---

## JSON Schema

Save this as `app/src/main/assets/home.json` for the local-file MVP, or return it from your backend `/home` endpoint.

```json
{
  "version": 1,
  "components": [
    {
      "type": "section_header",
      "id": "header_suggested",
      "title": "Suggested for You",
      "subtitle": "Based on what's in your fridge"
    },
    {
      "type": "carousel",
      "id": "carousel_quick_meals",
      "title": "Quick Meals",
      "items": [
        {
          "type": "squared_card",
          "id": "card_carbonara",
          "title": "Pasta Carbonara",
          "subtitle": "20 min · Easy",
          "imageUrl": "https://your-image-url.com/carbonara.jpg",
          "recipeId": "recipe_123",
          "tag": "Quick"
        },
        {
          "type": "squared_card",
          "id": "card_avocado_toast",
          "title": "Avocado Toast",
          "subtitle": "10 min · Easy",
          "imageUrl": "https://your-image-url.com/toast.jpg",
          "recipeId": "recipe_124",
          "tag": null
        }
      ]
    },
    {
      "type": "section_header",
      "id": "header_trending",
      "title": "Trending This Week"
    },
    {
      "type": "carousel",
      "id": "carousel_trending",
      "title": "Trending",
      "items": [
        {
          "type": "squared_card",
          "id": "card_ramen",
          "title": "Spicy Ramen",
          "subtitle": "45 min · Medium",
          "imageUrl": "https://your-image-url.com/ramen.jpg",
          "recipeId": "recipe_200",
          "tag": "Trending"
        }
      ]
    }
  ]
}
```

**Design decision to discuss in the demo:** `version` field at the top-level. Old clients can check version and fall back to a hard-coded layout if they can't parse a newer schema. This is how you handle schema evolution without forcing app updates — important for a banking app where you can't assume all users are on the latest version.

---

## Data Models (Kotlin + kotlinx.serialization)

```kotlin
// build.gradle.kts — if not already present:
// implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
// plugins { id("org.jetbrains.kotlin.plugin.serialization") }

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

// Top-level page model
@Serializable
data class HomePageModel(
    val version: Int = 1,
    val components: List<ComponentModel>
)

// Sealed class — the "type" field in JSON is the discriminator
@Serializable
@JsonClassDiscriminator("type")
sealed class ComponentModel {

    @Serializable
    @SerialName("section_header")
    data class SectionHeader(
        val id: String,
        val title: String,
        val subtitle: String? = null
    ) : ComponentModel()

    @Serializable
    @SerialName("carousel")
    data class Carousel(
        val id: String,
        val title: String,
        val items: List<SquaredCard>
    ) : ComponentModel()

    @Serializable
    @SerialName("squared_card")
    data class SquaredCard(
        val id: String,
        val title: String,
        val subtitle: String? = null,
        val imageUrl: String,
        val recipeId: String,
        val tag: String? = null
    ) : ComponentModel()

    // CRITICAL: Unknown types must not crash the app.
    // When the server introduces a new component type,
    // older clients that don't know it yet should skip it gracefully.
    @Serializable
    @SerialName("unknown")
    object Unknown : ComponentModel()
}
```

**⚠️ Critical design decision to mention in the demo:**
The `Unknown` sealed class variant. When the backend introduces a new component type (e.g., `"type": "banner"`), clients on an older version will receive a type they can't recognize. Without `Unknown`, kotlinx.serialization throws a `SerializationException` and your home screen crashes. With it, unknown types are deserialized to `Unknown` and silently skipped in the renderer. This is non-obvious to engineers who haven't operated server-driven UI in production — it's one of the first bugs that bites teams.

### JSON parsing setup

```kotlin
// In your DI / repository setup
val json = Json {
    ignoreUnknownKeys = true       // forward compatibility — new fields don't break old clients
    coerceInputValues = true       // null where the model expects non-null → use default value
    isLenient = true               // tolerate minor JSON formatting issues
    classDiscriminator = "type"    // matches our @JsonClassDiscriminator
}
```

---

## Repository

### Option A: Local JSON file (MVP for demo)

```kotlin
class HomeRepository(
    private val context: Context,
    private val json: Json
) {
    fun getHomeComponents(): Flow<HomePageModel> = flow {
        val raw = context.assets.open("home.json")
            .bufferedReader()
            .use { it.readText() }
        emit(json.decodeFromString<HomePageModel>(raw))
    }.flowOn(Dispatchers.IO)
}
```

### Option B: Real API endpoint (wire in later)

```kotlin
// Retrofit interface
interface HomeApi {
    @GET("home")
    suspend fun getHomeComponents(): HomePageModel
}

class HomeRepository(private val api: HomeApi) {
    fun getHomeComponents(): Flow<HomePageModel> = flow {
        emit(api.getHomeComponents())
    }.catch { e ->
        // Could emit a fallback hard-coded model here
        emit(HomePageModel(components = emptyList()))
    }.flowOn(Dispatchers.IO)
}
```

**Demo talking point:** The repository interface is identical whether the data comes from a local file or a live API. The ViewModel and UI don't change — you swap the data source implementation. This is the same separation we had at Spotify between content operations (the data pipeline) and the rendering layer.

---

## ViewModel

```kotlin
data class HomeUiState(
    val components: List<ComponentModel> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class HomeViewModel(
    private val repository: HomeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHome()
    }

    private fun loadHome() {
        viewModelScope.launch {
            repository.getHomeComponents()
                .collect { pageModel ->
                    _uiState.update {
                        it.copy(
                            // Filter out Unknown types — don't render what we don't know
                            components = pageModel.components.filter { c ->
                                c !is ComponentModel.Unknown
                            },
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }
}
```

---

## Composables

### Component Renderer — The Core of the Pattern

```kotlin
/**
 * The component resolver. Maps each ComponentModel type to its Composable.
 * This is the equivalent of HubFramework's component factory + render pipeline.
 *
 * Adding a new component type requires:
 * 1. Add a JSON type to the server schema
 * 2. Add a sealed class variant
 * 3. Add a when-branch here
 * 4. Write the Composable
 *
 * Client code outside this function never needs to change.
 */
@Composable
fun ComponentRenderer(
    component: ComponentModel,
    onRecipeClick: (String) -> Unit
) {
    when (component) {
        is ComponentModel.SectionHeader -> SectionHeaderComponent(component)
        is ComponentModel.Carousel -> CarouselComponent(component, onRecipeClick)
        is ComponentModel.SquaredCard -> SquaredCardComponent(component, onRecipeClick)
        is ComponentModel.Unknown -> { /* skip — no UI rendered */ }
    }
}
```

### Home Screen

```kotlin
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onRecipeClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    when {
        uiState.isLoading -> LoadingIndicator()
        uiState.error != null -> ErrorState(uiState.error!!)
        else -> HomeContent(uiState.components, onRecipeClick)
    }
}

@Composable
private fun HomeContent(
    components: List<ComponentModel>,
    onRecipeClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = components,
            key = { component ->
                // Stable keys for efficient recomposition
                when (component) {
                    is ComponentModel.SectionHeader -> component.id
                    is ComponentModel.Carousel -> component.id
                    is ComponentModel.SquaredCard -> component.id
                    else -> component.hashCode().toString()
                }
            }
        ) { component ->
            ComponentRenderer(component, onRecipeClick)
        }
    }
}
```

### SectionHeader Composable

```kotlin
@Composable
fun SectionHeaderComponent(
    model: ComponentModel.SectionHeader,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = model.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        model.subtitle?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
```

### Carousel Composable

```kotlin
@Composable
fun CarouselComponent(
    model: ComponentModel.Carousel,
    onRecipeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = model.items,
                key = { it.id }
            ) { card ->
                SquaredCardComponent(card, onRecipeClick)
            }
        }
    }
}
```

### SquaredCard Composable

```kotlin
@Composable
fun SquaredCardComponent(
    model: ComponentModel.SquaredCard,
    onRecipeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .size(160.dp)
            .clickable { onRecipeClick(model.recipeId) },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Image — use whatever image loading library is already in Pocket Chef (Coil recommended)
            AsyncImage(
                model = model.imageUrl,
                contentDescription = model.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient scrim for text legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                            startY = 80f
                        )
                    )
            )

            // Title at the bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                model.tag?.let { tag ->
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                model.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
```

---

## Handling Unknown Component Types (Non-Obvious, High Signal)

The `Unknown` variant above handles the happy path. For production robustness, also configure kotlinx.serialization with a custom deserializer that catches truly malformed components and maps them to `Unknown`:

```kotlin
// If a component has an unrecognized "type" field, it will deserialize to Unknown.
// This requires the @JsonClassDiscriminator annotation and a fallback in Json config:

val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    // kotlinx.serialization sealed class polymorphism handles unknown subtypes
    // by returning the @SerialName("unknown") variant if no match found.
}
```

**Why this matters for Monstro specifically:** A banking app will iterate its home screen frequently — product will want to add promotional banners, compliance notices, product upsell cards. If you don't handle unknown types gracefully from day one, every new component type requires a coordinated client + server release. With `Unknown` handling, the server can add new types and old clients silently skip them. You can ship server-side experiments to users who haven't updated the app.

---

## Talking Points for the Demo

### Lead with the earned insight (don't volunteer everything — deploy when asked)

**When they ask "why server-driven UI for the home screen?"**
> "The home screen is the highest-experimentation surface in any consumer app — it's where you test what to surface, in what order, to which users. Hard-coding the layout means every experiment requires a release cycle. With server-driven UI, the component list comes from the backend. We can reorder sections, add a promotional banner, or A/B test carousel content with a config change. We did this at Spotify's homepage at 100M+ users — that's where I built my intuition for when this pattern pays off and when it gets in the way."

**When they ask "what would you do differently?"**
> "Two things. First: I served the JSON from a local file for this demo, but in production you'd want a backend endpoint with caching — show cached content immediately, refresh in the background. Loading spinners on a home screen destroy perceived performance. Second: I'd add schema versioning earlier. The `version` field is there, but I haven't written the fallback logic for when a client receives a schema version it doesn't support. In a banking context where you can't force-update users, that matters."

**When they ask about the Unknown type / forward compatibility:**
> "This is the non-obvious one that bites teams. When the backend adds a new component type, older clients receive a JSON `type` field they've never seen. Without explicit handling, that's a crash. I added an `Unknown` sealed class variant that acts as a catch-all — unknown types deserialize to `Unknown` and the renderer skips them with no UI. Old clients don't crash, they just don't render the new component. It's opt-in rendering of new features."

**When they ask how you'd extend this:**
> "Adding a new component is four steps: add a JSON type to the backend, add a sealed class variant in the client, add a `when` branch in the `ComponentRenderer`, write the Composable. The UI code outside the renderer never touches. At Spotify we'd add a new component for a campaign or experiment and the change was isolated — designers, product, and backend could move without coordinating on every consumer of the component list."

**When they ask about performance:**
> "LazyColumn with stable `key` lambdas means Compose only recomposes the items that actually change — not the entire list. At 100M+ users, Spotify's equivalent used RecyclerView cell reuse; the principle is the same. For images, Coil handles memory caching and progressive loading. The main perf risk in server-driven UI is the first load — the initial network round trip before anything renders. Cache-first strategy (load from Room/DataStore, refresh from network) is the fix."

---

## Tradeoffs to Be Ready to Discuss

| Tradeoff | What to say |
|---|---|
| Slightly slower initial render | Cache-first: show cached JSON immediately, refresh async. Solved. |
| Animation constraints | Component-level animations (enter/exit transitions, shared element) work fine. Cross-component choreography is harder — you'd handle that client-side as a special case. |
| UI limitations | Some designs are hard to express in a flat component list. Rich stateful interactions (forms, real-time updates) fight the model. Server-driven UI is the right choice for scroll-based content surfaces, not for complex interactive UIs. |
| Backend coupling | When the server returns an empty list, the screen is empty. Fallback to a locally-defined default component list in the ViewModel if the API returns nothing. |
| "Why not just use a RecyclerView with multiple view types?" | That's exactly what this is, architecturally — except the type mapping happens via JSON discriminator instead of a hard-coded `viewType` integer. The server-driven part is just moving the type decision to the backend. Compose sealed classes make this cleaner than the old ViewHolder pattern. |

---

## Implementation Checklist

```
[ ] Add kotlinx.serialization dependency + plugin to build.gradle.kts
[ ] Create app/src/main/assets/home.json with sample data
[ ] Create ComponentModel.kt (sealed class with 3 variants + Unknown)
[ ] Configure Json instance (ignoreUnknownKeys, coerceInputValues)
[ ] Create HomeRepository.kt (local file reader OR Retrofit interface)
[ ] Create HomeViewModel.kt (StateFlow<HomeUiState>)
[ ] Create ComponentRenderer.kt (when-block)
[ ] Create SectionHeaderComponent.kt
[ ] Create CarouselComponent.kt
[ ] Create SquaredCardComponent.kt
[ ] Wire HomeScreen to replace/augment existing home screen
[ ] Test: add an unknown "type" to home.json → verify no crash
[ ] Test: remove a component from home.json → verify screen updates
```

---

## Context for This Document

**Source:** Architecture synthesized from three inputs:
1. Jose's direct experience as an Android feature builder on Spotify's Homepage team (~2 years, implemented components, ran A/B experiments on server-driven surfaces serving 100M+ users)
2. HubFramework open-source documentation (spotify.github.io/HubFramework/) — Spotify's iOS server-driven UI framework
3. John Sundell's UMT2016 talk on component-driven UIs at Spotify

**What's the same vs. modern Android:**
- Core pattern is identical: UI = f(state), component model, backend-controlled rendering, component resolver
- What changed: Compose makes "UI = f(state)" the default — no framework required to enforce it. Sealed classes + `when` replace protocol + factory registration. kotlinx.serialization replaces custom JSON path DSLs. `StateFlow` replaces the content operation loading chain.

**Honest framing for the demo:** "I built on top of this architecture at Spotify — I wasn't the one who designed it. But working within it for two years gave me a strong sense of when it works and when it doesn't. Building a minimal version in Pocket Chef this weekend was a chance to implement the core pattern myself, in modern Android, end-to-end."
