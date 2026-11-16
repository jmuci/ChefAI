package com.tenmilelabs.chefai.auth.data.local

/**
 * How long a previewed invite waits for its sign-in. Long enough for a sign-up round trip, short
 * enough that a token opened by one person can't walk someone who signs in later into that
 * household.
 */
internal const val PENDING_INVITE_TTL_MS: Long = 30 * 60 * 1000L

/** [token] while it is younger than [PENDING_INVITE_TTL_MS]; a token with no timestamp is stale. */
internal fun freshPendingInviteToken(token: String?, savedAt: Long?, now: Long): String? =
    token?.takeIf { savedAt != null && now - savedAt in 0..PENDING_INVITE_TTL_MS }
