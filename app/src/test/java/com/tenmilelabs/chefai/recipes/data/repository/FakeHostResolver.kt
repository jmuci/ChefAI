package com.tenmilelabs.chefai.recipes.data.repository

import com.tenmilelabs.chefai.recipes.domain.repository.HostResolver
import java.net.InetAddress

/**
 * A [HostResolver] that never touches real DNS: [resolve] looks [host] up in [addresses], falling
 * back to an ordinary public address for anything unmapped so tests that don't care about DNS can
 * use whatever hostname reads best.
 */
class FakeHostResolver(
    private val addresses: Map<String, List<InetAddress>> = emptyMap(),
) : HostResolver {

    override fun resolve(host: String): List<InetAddress> = addresses[host] ?: DEFAULT_PUBLIC_ADDRESS

    companion object {
        /** An ordinary, non-blocked address. Parsing a literal IP never touches the network. */
        val DEFAULT_PUBLIC_ADDRESS: List<InetAddress> = listOf(InetAddress.getByName("93.184.216.34"))

        /** Parses each literal IP into an [InetAddress] — safe, a literal never triggers a DNS lookup. */
        fun literal(vararg ip: String): List<InetAddress> = ip.map { InetAddress.getByName(it) }
    }
}
