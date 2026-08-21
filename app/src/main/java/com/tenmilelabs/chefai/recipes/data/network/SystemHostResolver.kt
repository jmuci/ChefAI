package com.tenmilelabs.chefai.recipes.data.network

import com.tenmilelabs.chefai.recipes.domain.repository.HostResolver
import java.net.InetAddress
import java.net.UnknownHostException
import javax.inject.Inject

/** [HostResolver] backed by the platform's real DNS resolution. */
class SystemHostResolver @Inject constructor() : HostResolver {

    override fun resolve(host: String): List<InetAddress> = try {
        InetAddress.getAllByName(host).toList()
    } catch (e: UnknownHostException) {
        emptyList()
    }
}
