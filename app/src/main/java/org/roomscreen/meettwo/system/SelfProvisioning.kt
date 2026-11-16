package org.roomscreen.meettwo.system

import android.content.Context
import android.util.Log

/**
 * Grants the app what it needs for unattended operation, as root, once per service start:
 * runtime permissions (notifications, Bluetooth advertising), app-ops (system settings for
 * the screen brightness, usage access for the foreground app) and the battery-optimisation
 * whitelist. Every step is idempotent; failures only get logged.
 */
class SelfProvisioning(private val context: Context, private val root: RootShell) {
    suspend fun run() {
        val pkg = context.packageName
        val commands = listOf(
            "pm grant $pkg android.permission.POST_NOTIFICATIONS",
            "pm grant $pkg android.permission.BLUETOOTH_ADVERTISE",
            "pm grant $pkg android.permission.BLUETOOTH_CONNECT",
            "appops set $pkg WRITE_SETTINGS allow",
            "appops set $pkg GET_USAGE_STATS allow",
            "dumpsys deviceidle whitelist +$pkg",
        )
        root.exec(commands.joinToString("; ") { "$it >/dev/null 2>&1" })
            .onFailure { Log.w(TAG, "Self-provisioning failed (no root?)", it) }
    }

    private companion object {
        const val TAG = "SelfProvisioning"
    }
}
