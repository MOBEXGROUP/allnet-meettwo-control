package org.roomscreen.meettwo.system

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings

/** How the tablet identifies itself in MQTT topics and in Home Assistant. */
data class DeviceIdentity(
    /** Lower-case, topic-safe ID (the serial number when root is available). */
    val id: String,
    val serial: String?,
    val model: String = Build.MODEL,
    val firmware: String = Build.DISPLAY,
    /** MAC addresses of Ethernet and Wi-Fi, for Home Assistant device connections. */
    val macs: List<String> = emptyList(),
) {
    companion object {
        suspend fun resolve(context: Context, root: RootShell): DeviceIdentity {
            val serial = root.exec("getprop ro.serialno").getOrNull()?.takeIf { it.isNotBlank() }
            val macs = root.exec("cat /sys/class/net/eth0/address /sys/class/net/wlan0/address 2>/dev/null")
                .getOrNull().orEmpty()
                .lines().map { it.trim().lowercase() }
                .filter { MAC.matches(it) && it != "00:00:00:00:00:00" }
            val id = (serial ?: androidId(context)).lowercase().replace(Regex("[^a-z0-9_-]"), "_")
            return DeviceIdentity(id = id, serial = serial, macs = macs)
        }

        @SuppressLint("HardwareIds")
        private fun androidId(context: Context): String =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

        private val MAC = Regex("^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")
    }
}
