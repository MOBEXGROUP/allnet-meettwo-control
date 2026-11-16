package org.roomscreen.meettwo.hardware

import android.system.Os
import org.roomscreen.meettwo.light.Levels
import java.io.IOException

/** Output for the LED bar: one level per channel, 0 to [Levels.MAX]. */
fun interface LedDevice {
    fun write(levels: Levels): Result<Unit>
}

/** Writes to the vendor driver `/dev/ledjni` through the app's own JNI library. */
class NativeLedDevice : LedDevice {
    private val library = runCatching { System.loadLibrary("meettwoled") }

    override fun write(levels: Levels): Result<Unit> {
        library.onFailure { return Result.failure(it) }
        val rc = nativeWrite(levels.r, levels.g, levels.b)
        return if (rc == 0) {
            Result.success(Unit)
        } else {
            Result.failure(IOException("/dev/ledjni: ${Os.strerror(-rc)}"))
        }
    }

    private external fun nativeWrite(red: Int, green: Int, blue: Int): Int
}
