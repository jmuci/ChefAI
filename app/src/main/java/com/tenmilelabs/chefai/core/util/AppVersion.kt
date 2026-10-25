package com.tenmilelabs.chefai.core.util

import com.tenmilelabs.chefai.BuildConfig

/**
 * The version label shown at the bottom of the login and Settings screens: the plain version name
 * on release builds, `<versionName>-<commit>-debug` on debug ones so a bug report pins the commit.
 */
fun formatAppVersion(versionName: String, isDebug: Boolean, commitHash: String): String =
    if (isDebug) "$versionName-$commitHash-debug" else versionName

/** [formatAppVersion] for the running build. */
val appVersionLabel: String =
    formatAppVersion(BuildConfig.VERSION_NAME, BuildConfig.DEBUG, BuildConfig.GIT_COMMIT_HASH)
