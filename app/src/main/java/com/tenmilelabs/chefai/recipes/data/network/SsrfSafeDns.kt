package com.tenmilelabs.chefai.recipes.data.network

import com.tenmilelabs.chefai.recipes.data.repository.isBlockedAddress
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * DNS for the scraper client that refuses any host resolving to a non-public address.
 *
 * `isSafeHost` validates a URL before it is fetched, but OkHttp then resolves the host again to
 * connect — a DNS-rebinding server can answer with a public IP the first time and an internal one
 * the second (or on a retry). Filtering here, at the resolution OkHttp actually connects with,
 * closes that window.
 */
internal class SsrfSafeDns(private val delegate: Dns = Dns.SYSTEM) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (addresses.any { it.isBlockedAddress() }) {
            throw UnknownHostException("$hostname resolves to a disallowed address")
        }
        return addresses
    }
}
