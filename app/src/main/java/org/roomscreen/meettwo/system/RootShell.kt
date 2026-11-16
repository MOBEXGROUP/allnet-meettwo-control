package org.roomscreen.meettwo.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Runs commands as root through the firmware's setuid `su` (`/system/xbin/su`).
 * The MeetTwo ships with it; the vendor's own tools use it the same way.
 */
class RootShell {
    suspend fun exec(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val process = start(command)
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val exit = process.waitFor()
            if (exit != 0) throw IOException("'$command' exited with $exit: $output")
            output
        }
    }

    /** Starts a long-running root command; stdout and stderr are merged. */
    fun start(command: String): Process =
        ProcessBuilder("su", "0", "sh", "-c", command).redirectErrorStream(true).start()

    suspend fun isAvailable(): Boolean = exec("id -u").getOrNull() == "0"
}
