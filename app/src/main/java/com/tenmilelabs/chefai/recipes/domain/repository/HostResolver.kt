package com.tenmilelabs.chefai.recipes.domain.repository

import java.net.InetAddress

/**
 * Resolves a hostname to the addresses it actually points at.
 *
 * The SSRF guard in [com.tenmilelabs.chefai.recipes.data.repository.normalizeAndValidateUrl] hooks
 * through this rather than calling [InetAddress] directly so a fake can hand back a deterministic
 * address in tests — including a hostname resolving to a blocked address, the DNS-rebinding shape
 * of attack a string-only host check can never catch.
 */
fun interface HostResolver {

    /** Returns every address [host] resolves to, or an empty list if it doesn't resolve at all. */
    fun resolve(host: String): List<InetAddress>
}
