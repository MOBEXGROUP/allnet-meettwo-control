package org.roomscreen.meettwo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch
import org.roomscreen.meettwo.container

/**
 * Sets the MQTT settings from a PC without touching the tablet (only the shell and the
 * system may send it, see the manifest), for example:
 *
 * ```
 * adb shell am broadcast -n org.roomscreen.meettwo/.service.ConfigReceiver \
 *   --es mqtt_host 192.168.1.10 --ei mqtt_port 1883 --es mqtt_user ha --es mqtt_password secret \
 *   --ez mqtt_enabled true
 * ```
 * Extras that are not given keep their value.
 */
class ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = context.container
        container.appScope.launch {
            try {
                container.settings.updateMqtt { s ->
                    s.copy(
                        enabled = if (intent.hasExtra(ENABLED)) intent.getBooleanExtra(ENABLED, s.enabled) else s.enabled,
                        host = intent.getStringExtra(HOST) ?: s.host,
                        port = port(intent) ?: s.port,
                        username = intent.getStringExtra(USER) ?: s.username,
                        password = intent.getStringExtra(PASSWORD) ?: s.password,
                        discoveryPrefix = intent.getStringExtra(PREFIX) ?: s.discoveryPrefix,
                    )
                }
                BridgeService.start(context)
                pending.resultCode = RESULT_OK
                pending.resultData = "saved"
            } catch (e: Exception) {
                Log.e(TAG, "Saving settings failed", e)
                pending.resultCode = RESULT_FAILED
                pending.resultData = e.message
            } finally {
                pending.finish()
            }
        }
    }

    /** Accepts the port as `--ei` or `--es`. */
    private fun port(intent: Intent): Int? {
        @Suppress("DEPRECATION")
        val value = intent.extras?.get(PORT) ?: return null
        return (value as? Int ?: value.toString().toIntOrNull())?.takeIf { it in 1..65535 }
    }

    private companion object {
        const val TAG = "ConfigReceiver"
        const val RESULT_OK = 1
        const val RESULT_FAILED = 2
        const val ENABLED = "mqtt_enabled"
        const val HOST = "mqtt_host"
        const val PORT = "mqtt_port"
        const val USER = "mqtt_user"
        const val PASSWORD = "mqtt_password"
        const val PREFIX = "mqtt_discovery_prefix"
    }
}
