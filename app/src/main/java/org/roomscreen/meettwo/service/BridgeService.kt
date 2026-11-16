package org.roomscreen.meettwo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.roomscreen.meettwo.R
import org.roomscreen.meettwo.container
import org.roomscreen.meettwo.mqtt.MqttStatus
import org.roomscreen.meettwo.ui.MainActivity

/**
 * Keeps the app running: card reader, beacon, observers and the MQTT connection.
 * Started at boot, after updates and when the app is opened.
 */
class BridgeService : LifecycleService() {
    private var started = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(getString(R.string.mqtt_disabled)),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (!started) {
            started = true
            lifecycleScope.launch { container.runBackground() }
            lifecycleScope.launch {
                combine(container.mqtt.status, container.light.error) { mqtt, led -> mqtt to led }
                    .collect { (mqtt, led) ->
                        val text = led?.let { getString(R.string.led_error, it) } ?: statusText(mqtt)
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
                    }
            }
        }
        return START_STICKY
    }

    private fun statusText(status: MqttStatus) = when (status) {
        MqttStatus.Disabled -> getString(R.string.mqtt_disabled)
        MqttStatus.Connecting -> getString(R.string.mqtt_connecting)
        MqttStatus.Connected -> getString(R.string.mqtt_connected)
        is MqttStatus.Failed -> getString(R.string.mqtt_failed, status.reason)
    }

    private fun notification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_status), NotificationManager.IMPORTANCE_MIN),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "status"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BridgeService::class.java))
        }
    }
}
