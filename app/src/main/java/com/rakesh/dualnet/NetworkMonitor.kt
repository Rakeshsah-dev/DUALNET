package com.rakesh.dualnet

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class NetworkSnapshot(
    val key: String,
    val transport: String,
    val interfaceName: String?,
    val downstreamKbps: Int?,
    val metered: Boolean,
    val rxMbps: Double? = null,
    val txMbps: Double? = null
)

class NetworkMonitor(context: Context) {
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val active = ConcurrentHashMap<Network, NetworkSnapshot>()
    private val previous = ConcurrentHashMap<String, Pair<Long, Long>>()
    private val _networks = MutableStateFlow<List<NetworkSnapshot>>(emptyList())
    val networks: StateFlow<List<NetworkSnapshot>> = _networks.asStateFlow()
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            update(network)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            update(network, caps)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) {
            val old = active[network] ?: return
            active[network] = old.copy(interfaceName = linkProperties.interfaceName)
            publish()
        }

        override fun onLost(network: Network) {
            active.remove(network)
            publish()
        }
    }

    fun start() {
        if (registered) return
        registered = true
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivity.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            registered = false
        }
        scope.launch {
            while (isActive) {
                sampleInterfaces()
                delay(1000)
            }
        }
    }

    private fun update(network: Network, supplied: NetworkCapabilities? = null) {
        val caps = supplied ?: connectivity.getNetworkCapabilities(network) ?: return
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> return
        }
        val link = connectivity.getLinkProperties(network)
        active[network] = NetworkSnapshot(
            key = network.toString(),
            transport = transport,
            interfaceName = link?.interfaceName,
            downstreamKbps = caps.linkDownstreamBandwidthKbps.takeIf { it > 0 },
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        )
        publish()
    }

    private fun sampleInterfaces() {
        val updated = active.entries.mapNotNull { (network, snapshot) ->
            val iface = snapshot.interfaceName ?: return@mapNotNull snapshot
            if (Build.VERSION.SDK_INT < 31) return@mapNotNull snapshot
            val rx = TrafficStats.getRxBytes(iface)
            val tx = TrafficStats.getTxBytes(iface)
            if (rx < 0 || tx < 0) return@mapNotNull snapshot.copy(rxMbps = null, txMbps = null)
            val old = previous.put(iface, rx to tx)
            if (old == null) return@mapNotNull snapshot.copy(rxMbps = 0.0, txMbps = 0.0)
            val rxRate = ((rx - old.first).coerceAtLeast(0) * 8.0) / 1_000_000.0
            val txRate = ((tx - old.second).coerceAtLeast(0) * 8.0) / 1_000_000.0
            snapshot.copy(rxMbps = rxRate, txMbps = txRate)
        }
        // Keep only interfaces that still exist.
        updated.forEach { snapshot ->
            active.entries.firstOrNull { it.value.key == snapshot.key }?.let { active[it.key] = snapshot }
        }
        publish()
    }

    private fun publish() {
        _networks.value = active.values.sortedBy { it.transport }
    }

    fun stop() {
        if (registered) {
            try { connectivity.unregisterNetworkCallback(callback) } catch (_: Exception) {}
            registered = false
        }
        scope.cancel()
        active.clear()
    }
}
