package com.tenmilelabs.chefai.auth.ui.google

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Reads the `email` claim of a Google ID token **without verifying it** — only ever to prefill the
 * login field after a `GOOGLE_EMAIL_EXISTS` answer, where the backend has already judged the
 * token. Never use the result for anything trusted; never log the token.
 */
internal fun googleIdTokenEmail(idToken: String): String? = runCatching {
    val payload = idToken.split('.').getOrNull(1) ?: return null
    val json = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
    val claims: JsonObject = Json.parseToJsonElement(json).jsonObject
    claims["email"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}.getOrNull()
