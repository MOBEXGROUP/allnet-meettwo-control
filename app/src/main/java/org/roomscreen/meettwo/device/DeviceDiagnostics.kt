package org.roomscreen.meettwo.device

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address
import java.time.Instant

data class DeviceStatus(
    val ip: String? = null,
    /** `Ethernet`, `Wi-Fi`, `Other` or `none`. */
    val network: String = "none",
    val wifiRssi: Int? = null,
    val foregroundApp: String? = null,
    val lastBoot: Instant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime()),
)

/** Network, foreground app and boot time for the diagnostic sensors. */
class DeviceDiagnostics(private val context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)

    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status.asStateFlow()

    suspend fun run() {
        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            private var link: LinkProperties? = null
            private var caps: NetworkCapabilities? = null

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                caps = capabilities
                publish()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                link = linkProperties
                publish()
            }

            override fun onLost(network: Network) {
                link = null
                caps = null
                publish()
            }

            private fun publish() {
                val c = caps
                val type = when {
                    c == null -> "none"
                    c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    else -> "Other"
                }
                val ip = link?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress
                val rssi = (c?.transportInfo as? WifiInfo)?.rssi?.takeIf { type == "Wi-Fi" }
                _status.update { it.copy(ip = ip, network = type, wifiRssi = rssi) }
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        try {
            while (true) {
                val app = foregroundApp()
                _status.update { it.copy(foregroundApp = app) }
                delay(POLL_MS)
            }
        } finally {
            connectivity.unregisterNetworkCallback(callback)
        }
    }

    /** Needs usage access (granted by self-provisioning); `null` without it. */
    private fun foregroundApp(): String? {
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(now - LOOKBACK_MS, now) ?: return null
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = event.packageName
        }
        return last ?: _status.value.foregroundApp
    }

    private companion object {
        const val POLL_MS = 30_000L
        const val LOOKBACK_MS = 24 * 60 * 60 * 1000L
    }
}
