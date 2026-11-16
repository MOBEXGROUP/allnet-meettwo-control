package org.roomscreen.meettwo.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.roomscreen.meettwo.settings.ScreenSettings
import org.roomscreen.meettwo.system.RootShell
import kotlin.math.roundToInt

/**
 * Display on/off, backlight brightness and "wake screen on touch".
 *
 * On/off uses the sleep and wake-up keys as root (Android itself is put to sleep and woken
 * up, so the kiosk app sees the usual screen events). Brightness is Android's
 * `screen_brightness` (1 to 255, manual mode), shown as 1 to 100 %.
 *
 * A touch on the dark screen wakes it in two ways: the kernel touch driver sends a power key
 * through the PMIC power key device (`wdt_ts: send wakeup key`), and Android wakes on the
 * touch itself. With "wake on touch" off, both input devices are inhibited (sysfs
 * `inhibited`) while the screen is off. The hardware power button uses the same device, so it
 * does not wake the screen either; Home Assistant does (the wake-up key is injected).
 */
class ScreenController(private val context: Context, private val root: RootShell) {
    private val power = context.getSystemService(PowerManager::class.java)

    private val _on = MutableStateFlow(power.isInteractive)
    val on: StateFlow<Boolean> = _on.asStateFlow()

    private val _brightness = MutableStateFlow(readBrightnessPercent())
    /** 1 to 100 %. */
    val brightness: StateFlow<Int> = _brightness.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun setOn(on: Boolean) {
        if (on) setWakeInputsInhibited(false)
        root.exec("input keyevent ${if (on) "KEYCODE_WAKEUP" else "KEYCODE_SLEEP"}")
            .onSuccess { _error.value = null }
            .onFailure { _error.value = it.message }
    }

    suspend fun setBrightness(percent: Int) {
        val value = percentToRaw(percent)
        val result = if (Settings.System.canWrite(context)) {
            runCatching {
                val resolver = context.contentResolver
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
                Unit
            }
        } else {
            root.exec("settings put system screen_brightness_mode 0; settings put system screen_brightness $value")
                .map { }
        }
        result.onSuccess { _error.value = null }.onFailure { _error.value = it.message }
    }

    /** Follows screen and brightness changes and applies "wake on touch" until cancelled. */
    suspend fun run(settings: Flow<ScreenSettings>) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                _on.value = power.isInteractive
            }
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                _brightness.value = readBrightnessPercent()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer,
        )
        _on.value = power.isInteractive
        _brightness.value = readBrightnessPercent()
        try {
            combine(_on, settings) { on, s -> !on && !s.wakeOnTouch }
                .distinctUntilChanged()
                .collect { setWakeInputsInhibited(it) }
        } finally {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
            withContext(NonCancellable) { setWakeInputsInhibited(false) }
        }
    }

    private suspend fun setWakeInputsInhibited(inhibited: Boolean) {
        val value = if (inhibited) 1 else 0
        val names = WAKE_INPUT_DEVICES.joinToString("|") { "\"$it\"" }
        root.exec(
            "for i in /sys/class/input/input*; do case \"$(cat \$i/name)\" in $names) " +
                "echo $value > \$i/inhibited;; esac; done",
        ).onFailure { _error.value = "Wake on touch: ${it.message}" }
    }

    private fun readBrightnessPercent(): Int {
        val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        return rawToPercent(raw)
    }

    companion object {
        /** Input devices that wake the screen: touchscreen and PMIC power key (MeetTwo). */
        private val WAKE_INPUT_DEVICES = listOf("goodix-ts", "rk805 pwrkey")

        fun rawToPercent(raw: Int): Int = (raw.coerceIn(1, 255) * 100 / 255.0).roundToInt().coerceIn(1, 100)
        fun percentToRaw(percent: Int): Int = (percent.coerceIn(1, 100) * 255 / 100.0).roundToInt().coerceIn(1, 255)
    }
}
