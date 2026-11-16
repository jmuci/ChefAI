package com.tenmilelabs.chefai.auth.ui

/**
 * Login suggestions for the local backend's seeded test accounts.
 *
 * Lives in the debug source set, with an empty twin in `src/release`, so these literals are not
 * compiled into a release APK at all. A `BuildConfig.DEBUG` check only stops them being shown;
 * with minification off, the strings would still ship in the release dex (security review A13).
 */
internal fun debugTestAccounts(): List<EmailSuggestion> = listOf(
    EmailSuggestion("test1@ex.com", "test123!"),
    EmailSuggestion("test2@ex.com", "test123!"),
    EmailSuggestion("test3@ex.com", "test123!"),
)
