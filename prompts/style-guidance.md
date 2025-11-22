# Style Guidance 
- Kotlin style: idiomatic, null-safety, extension functions sparingly.
- Compose: remember{} only when needed; hoist state; stable parameters; avoid unnecessary recomposition.
- Logging: Timber; structured messages; avoid noisy logs in production code.
- Coroutines: structured concurrency; Dispatchers.IO for I/O; never block main.
