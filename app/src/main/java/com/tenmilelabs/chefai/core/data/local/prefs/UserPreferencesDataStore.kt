package com.tenmilelabs.chefai.core.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The device-local settings store, shared by every repository that keeps a preference in it.
 *
 * It lives on its own, rather than beside the first repository that used it, because DataStore
 * permits exactly one active instance per file per process — a second `preferencesDataStore`
 * delegate naming `chefai_user_prefs` would throw at first access. A repository in any feature
 * package reads this one.
 *
 * A plain, unencrypted store — a sibling of
 * [com.tenmilelabs.chefai.auth.data.local.SecurePreferences] rather than a change to it. That store
 * holds tokens and is Keystore-encrypted for it; nothing in here is worth a cipher.
 */
internal val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "chefai_user_prefs"
)
