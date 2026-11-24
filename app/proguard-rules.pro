# R8 rules for the release build (isMinifyEnabled = true).
#
# Most libraries ship their own consumer rules (Room, Hilt, WorkManager, kotlinx.serialization,
# Ktor, Coil, OkHttp), so this file only covers what they don't. Before adding a rule, check
# app/build/outputs/mapping/release/missing_rules.txt and prefer the narrowest keep that works.

# Keep file and line numbers so Play Console stack traces stay readable after de-obfuscation with
# mapping.txt, but hide the original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Credential Manager loads its Play Services provider by reflection; without this, Sign in with
# Google fails at runtime with "no provider dependencies found" in a minified build.
# https://developer.android.com/identity/sign-in/credential-manager#proguard
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *** *(...);
}
