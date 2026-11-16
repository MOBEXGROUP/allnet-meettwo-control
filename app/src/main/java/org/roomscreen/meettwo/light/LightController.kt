package org.roomscreen.meettwo.light

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.roomscreen.meettwo.hardware.LedDevice

/**
 * Single owner of the LED state. Every change goes through [update]; the controller
 * renders it (including effects) and stores it for the next start.
 */
class LightController(
    private val device: LedDevice,
    private val store: LightStateStore,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(LightState())
    val state: StateFlow<LightState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** Last hardware error, `null` while writes succeed. */
    val error: StateFlow<String?> = _error.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)
    private var lastWritten: Levels? = null

    init {
        scope.launch {
            _state.value = store.load()
            launch { persistChanges() }
            render()
        }
    }

    fun update(transform: (LightState) -> LightState) = _state.update(transform)

    @OptIn(FlowPreview::class)
    private suspend fun persistChanges() {
        _state.drop(1).debounce(PERSIST_DEBOUNCE_MS).collect { store.save(it) }
    }

    private suspend fun render() {
        _state.collectLatest { s ->
            if (!s.on || s.effect == Effect.NONE) {
                write(s.levelsAt(0))
            } else {
                val start = SystemClock.elapsedRealtime()
                while (true) {
                    write(s.levelsAt(SystemClock.elapsedRealtime() - start))
                    delay(FRAME_MS)
                }
            }
        }
    }

    private suspend fun write(levels: Levels) = withContext(io) {
        if (levels == lastWritten) return@withContext
        device.write(levels)
            .onSuccess {
                lastWritten = levels
                _error.value = null
            }
            .onFailure {
                lastWritten = null
                if (_error.value != it.message) Log.e(TAG, "LED write failed", it)
                _error.value = it.message ?: it.toString()
            }
    }

    private companion object {
        const val TAG = "LightController"
        const val FRAME_MS = 40L
        const val PERSIST_DEBOUNCE_MS = 500L
    }
}
