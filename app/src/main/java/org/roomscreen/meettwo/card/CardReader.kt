package org.roomscreen.meettwo.card

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.roomscreen.meettwo.system.RootShell
import java.time.Instant

data class Card(val id: String, val time: Instant)

/**
 * Reports cards held to the tablet's NFC reader (NXP PN7xxx).
 *
 * The ALLNET firmware does not dispatch tags to apps: its NFC service sends every UID to
 * the input method ("NFC auto input", typed into the focused text field) and only an
 * activity in the foreground with reader mode would get the tag. To read cards while the
 * kiosk app stays in front, this reader follows the NFC HAL log (tag `NxpNciR`, logged by
 * the firmware by default) as root and decodes the NCI activation messages.
 */
class CardReader(private val root: RootShell) {
    private val _lastCard = MutableStateFlow<Card?>(null)
    val lastCard: StateFlow<Card?> = _lastCard.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun run() {
        while (true) {
            runCatching { follow() }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    Log.w(TAG, "NFC log reader stopped", it)
                    _error.value = it.message ?: it.toString()
                }
            delay(RESTART_DELAY_MS)
        }
    }

    private suspend fun follow(): Nothing = coroutineScope {
        // Start at "now" so cards from before the start are not reported again.
        val since = "%.3f".format(java.util.Locale.ROOT, System.currentTimeMillis() / 1000.0)
        val process = root.start("exec logcat -v raw -s $LOG_TAG:D -T $since")
        try {
            val reader = launch(Dispatchers.IO) {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        _error.value = null
                        val hex = HEX_AFTER_ARROW.find(line)?.groupValues?.get(1) ?: return@forEach
                        NciActivation.uidFromHex(hex)?.let(::onCard)
                    }
                }
            }
            reader.join()
            val exit = withContext(Dispatchers.IO) { process.waitFor() }
            throw java.io.IOException("logcat exited with $exit (root missing?)")
        } finally {
            process.destroy()
        }
    }

    private fun onCard(id: String) {
        val now = Instant.now()
        val last = _lastCard.value
        if (last != null && last.id == id && now.toEpochMilli() - last.time.toEpochMilli() < DEBOUNCE_MS) return
        Log.i(TAG, "Card $id")
        _lastCard.value = Card(id, now)
    }

    private companion object {
        const val TAG = "CardReader"
        const val LOG_TAG = "NxpNciR"
        const val RESTART_DELAY_MS = 10_000L
        const val DEBOUNCE_MS = 2_000L
        val HEX_AFTER_ARROW = Regex(">\\s*([0-9A-Fa-f]+)\\s*$")
    }
}
