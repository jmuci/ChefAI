# Authentication System

## Overview

The ChefAI app implements a comprehensive authentication system with secure token management. The
system is designed to:

- Securely store authentication tokens using Android's EncryptedDataStore
- Manage user session state across the application
- Automatically add auth tokens to network requests
- Support token refresh functionality
- Provide a mock user for development until backend authentication is ready

## Architecture

### Components

#### 1. **SessionManager** (`SessionManager.kt`)

The core singleton class that manages user authentication state.

**Key Features:**

- Exposes `userSession: StateFlow<UserSession>` for observing authentication state
- Handles login, logout, and token refresh operations
- Automatically loads session on app startup
- Uses mock user (`F47AC10B58CC4372A5670E02B2C3D479`) for development

**Main Methods:**

```kotlin
// Load saved session from secure storage
suspend fun loadSession()

// Login with credentials 
suspend fun login(email: String, password: String): Result<Unit>

// Logout and clear session
suspend fun logout()

// Refresh access token using refresh token
suspend fun refreshToken(): Result<Unit>

// Get current authenticated user
fun getCurrentUser(): User?

// Get current access token
fun getAccessToken(): String?

// Check if token is expired
fun isTokenExpired(bufferMillis: Long = 5 * 60 * 1000): Boolean
```

#### 2. **SecurePreferences** (`SecurePreferences.kt`)

Manages encrypted storage of authentication data.

**Stored Data:**

- User UUID
- Access Token
- Refresh Token
- Token Expiry (timestamp in milliseconds)

**Security:**

- Uses Android Security Crypto library
- AES256-GCM encryption scheme
- DataStore for structured preferences storage
- MasterKey managed by Android KeyStore

**Key Methods:**

```kotlin
suspend fun saveAuthData(userUuid: UUID, accessToken: String, refreshToken: String, tokenExpiry: Long)
suspend fun clearAuthData()
suspend fun updateAccessToken(accessToken: String, tokenExpiry: Long)
fun getUserUuid(): Flow<UUID?>
fun getAccessToken(): Flow<String?>
fun getRefreshToken(): Flow<String?>
fun getTokenExpiry(): Flow<Long?>
```

#### 3. **AuthInterceptor** (`AuthInterceptor.kt`)

Ktor client plugin that automatically adds authentication headers to HTTP requests.

**Functionality:**

- Intercepts all HTTP requests
- Adds `Authorization: Bearer <token>` header if user is authenticated
- Logs auth status for debugging

#### 4. **Domain Models**

**AuthToken** (`AuthToken.kt`):

```kotlin
data class AuthToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long // Timestamp when access token expires
)
```

**UserSession** (`UserSession.kt`):

```kotlin
sealed class UserSession {
    data class Authenticated(val user: User, val authToken: AuthToken) : UserSession()
    data object Unauthenticated : UserSession()
    data object Loading : UserSession()
}
```

## Usage Examples

### 1. Observing Authentication State in Composables

```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    val userSession by rememberUserSession()
    
    when (userSession) {
        is UserSession.Authenticated -> {
            val user = (userSession as UserSession.Authenticated).user
            Text("Welcome, ${user.displayName}!")
        }
        is UserSession.Unauthenticated -> {
            // Show login button or redirect to login
            LoginPrompt()
        }
        is UserSession.Loading -> {
            CircularProgressIndicator()
        }
    }
}
```

### 2. Using SessionManager in ViewModels

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {
    
    fun doAuthenticatedAction() {
        viewModelScope.launch {
            val user = sessionManager.getCurrentUser()
            if (user != null) {
                // Perform action with authenticated user
            } else {
                // Handle unauthenticated state
            }
        }
    }
    
    // Observe session changes
    val isAuthenticated = sessionManager.userSession
        .map { it is UserSession.Authenticated }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
```

### 3. Manual Login/Logout

```kotlin
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {
    
    fun login(email: String, password: String) {
        viewModelScope.launch {
            val result = sessionManager.login(email, password)
            result.onSuccess {
                // Navigate to main screen
            }.onFailure { error ->
                // Show error message
            }
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            sessionManager.logout()
            // Navigate to login screen
        }
    }
}
```

### 4. Accessing Current User

```kotlin
// In ViewModel
@HiltViewModel
class CreateRecipeViewModel @Inject constructor(
    private val recipesRepository: RecipesRepository,
    private val sessionManager: SessionManager
) : ViewModel() {
    
    fun saveRecipe(recipe: Recipe) {
        viewModelScope.launch {
            val currentUser = sessionManager.getCurrentUser()
            if (currentUser != null) {
                val recipeWithCreator = recipe.copy(creator = currentUser)
                recipesRepository.createRecipe(recipeWithCreator)
            } else {
                // Handle not authenticated
            }
        }
    }
}
```


### 5. Add User Profile Menu to Your Screen
```kotlin
@Composable
fun MyTopAppBar() {
    TopAppBar(
        title = { Text("My App") },
        actions = {
            UserProfileMenu(
                onLogout = {
                    // Navigate to login screen or show logged out message
                }
            )
        }
    )
}
```

## Integration with Network Layer

The `AuthInterceptor` is automatically installed in the Ktor `HttpClient` (see `NetworkModule.kt`),
so all API requests will include the auth token if available:

```kotlin
// In NetworkModule.kt
@Provides
@Singleton
fun provideHttpClient(sessionManager: SessionManager) = HttpClient(CIO) {
    install(AuthInterceptor) {
        this.sessionManager = sessionManager
    }
    // ... other configurations
}
```

## Current Development Setup (Feb 2026)

**Auth API calls are implemented.** `SessionManager.login()`, `register()`, and `refreshToken()` all
make real Ktor HTTP calls to the backend at `http://10.0.2.2:8080` (emulator localhost).

**Remaining gaps:**

- `loadSession()` reconstructs a `User` with placeholder `displayName = "User"` — user details
  should be fetched from backend or stored locally during login.
- `RecipesViewModel` and `DefaultRecipeRepository` still use a hardcoded test user UUID
  (`F47AC10B58CC4372A5670E02B2C3D479`) instead of getting the actual user from `SessionManager`.
- Anonymous-first flow is **not implemented** — the app requires login; there is no anonymous
  session, no local-only user, and no anonymous → authenticated upgrade path yet.

**What still needs work:**

1. Wire `SessionManager` into `RecipesViewModel` and `DefaultRecipeRepository` to use the real user.
2. Implement anonymous-first: allow app usage without login, create local-only session.
3. Implement anonymous → authenticated upgrade (merge local data into authenticated account).
4. Store user profile details locally during login so `loadSession()` can fully restore them.

## API Requests

All HTTP requests automatically include authentication headers. The `AuthInterceptor` adds
`Authorization: Bearer <token>` to every request if the user is authenticated.

**No additional code needed!** Just make your API calls as normal:

```kotlin
// The auth token is automatically added
val response = httpClient.get("https://api.chefai.com/recipes")
```


## Common Patterns

### Check if User is Authenticated

```kotlin
if (sessionManager.isAuthenticated()) {
    // User is logged in
}
```

### Get Access Token for Manual API Calls

```kotlin
val token = sessionManager.getAccessToken()
if (token != null) {
    // Use token in request
}
```

### Check if Token is Expired

```kotlin
if (sessionManager.isTokenExpired()) {
    // Refresh token or prompt re-login
}
```

## Troubleshooting

### User is Always Null

- Check if SessionManager is properly injected
- Ensure the app has initialized (allow time for init block to run)

### Auth Token Not Added to Requests

- Verify `AuthInterceptor` is installed in `NetworkModule`
- Check SessionManager is passed to HttpClient

### Tests Failing

- Use `advanceUntilIdle()` in tests to let coroutines complete
- Use `FakeSecurePreferences` instead of real SecurePreferences


## Security Best Practices Implemented

1. **Encrypted Storage**: All auth data encrypted at rest using AES256-GCM
2. **Token Expiry Management**: Access tokens have expiry timestamps and can be refreshed
3. **Secure KeyStore**: MasterKey stored in Android KeyStore hardware-backed when available
4. **Memory Safety**: Tokens stored in secure preferences, not in plain memory
5. **Automatic Cleanup**: Logout clears all stored authentication data

## Future Enhancements

- [x] Implement actual login API call
- [x] Implement token refresh API call
- [ ] Anonymous-first session (use app without login)
- [ ] Anonymous → authenticated upgrade flow
- [ ] Store user profile locally during login
- [ ] Wire real user ID into repositories (replace hardcoded test UUID)
- [ ] Add biometric authentication option
- [ ] Implement "Remember Me" functionality
- [ ] Add session timeout handling
- [ ] Implement automatic token refresh before expiry
- [ ] Add logout on 401 Unauthorized responses
