package org.roomscreen.meettwo.light

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/** 8-bit colour as used by Home Assistant and the UI. */
data class Rgb(val r: Int, val g: Int, val b: Int) {
    init {
        require(r in 0..255 && g in 0..255 && b in 0..255) { "RGB values must be 0..255" }
    }

    val hex: String get() = "%02X%02X%02X".format(r, g, b)

    companion object {
        val WHITE = Rgb(255, 255, 255)

        fun parseHex(value: String): Rgb? {
            val hex = value.removePrefix("#")
            if (hex.length != 6) return null
            val n = hex.toIntOrNull(16) ?: return null
            return Rgb(n shr 16 and 0xFF, n shr 8 and 0xFF, n and 0xFF)
        }
    }
}

/** Levels the LED driver accepts, 0 to [MAX] per channel. */
data class Levels(val r: Int, val g: Int, val b: Int) {
    companion object {
        const val MAX = 15
        val OFF = Levels(0, 0, 0)
    }
}

enum class Effect(val id: String, val label: String) {
    NONE("none", "None"),
    BLINK("blink", "Blink"),
    BREATHE("breathe", "Breathe"),
    RAINBOW("rainbow", "Rainbow");

    companion object {
        fun fromId(id: String): Effect? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

data class LightState(
    val on: Boolean = false,
    val color: Rgb = Rgb.WHITE,
    /** 0 to 255, as in Home Assistant. */
    val brightness: Int = 255,
    val effect: Effect = Effect.NONE,
) {
    /** Levels to show at [elapsedMs] since the state was set; static states ignore the time. */
    fun levelsAt(elapsedMs: Long): Levels {
        if (!on) return Levels.OFF
        return when (effect) {
            Effect.NONE -> ColorMath.toLevels(color, brightness)
            Effect.BLINK -> if ((elapsedMs / BLINK_HALF_PERIOD_MS) % 2 == 0L) {
                ColorMath.toLevels(color, brightness)
            } else {
                Levels.OFF
            }
            Effect.BREATHE -> {
                val phase = (elapsedMs % BREATHE_PERIOD_MS).toDouble() / BREATHE_PERIOD_MS
                val factor = (1 - cos(2 * Math.PI * phase)) / 2
                ColorMath.toLevels(color, (brightness * factor).roundToInt())
            }
            Effect.RAINBOW -> {
                val hue = (elapsedMs % RAINBOW_PERIOD_MS).toFloat() / RAINBOW_PERIOD_MS * 360f
                ColorMath.toLevels(ColorMath.hsvToRgb(hue, 1f, 1f), brightness)
            }
        }
    }

    companion object {
        const val BLINK_HALF_PERIOD_MS = 500L
        const val BREATHE_PERIOD_MS = 3_000L
        const val RAINBOW_PERIOD_MS = 10_000L
    }
}

object ColorMath {
    /**
     * Scales an 8-bit colour by [brightness] (0..255) down to the 16 levels of the driver.
     * A visible colour never rounds down to black: the strongest channels keep level 1.
     */
    fun toLevels(color: Rgb, brightness: Int): Levels {
        val bri = brightness.coerceIn(0, 255)
        fun scale(c: Int) = (c * bri * Levels.MAX / (255.0 * 255.0)).roundToInt()
        val levels = Levels(scale(color.r), scale(color.g), scale(color.b))
        if (levels != Levels.OFF || bri == 0) return levels
        val max = maxOf(color.r, color.g, color.b)
        if (max == 0) return levels
        return Levels(
            if (color.r == max) 1 else 0,
            if (color.g == max) 1 else 0,
            if (color.b == max) 1 else 0,
        )
    }

    /** [hue] 0..360, [saturation] and [value] 0..1. */
    fun hsvToRgb(hue: Float, saturation: Float, value: Float): Rgb {
        val h = ((hue % 360f) + 360f) % 360f
        val c = value * saturation
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = value - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun to8(v: Float) = ((v + m) * 255).roundToInt().coerceIn(0, 255)
        return Rgb(to8(r), to8(g), to8(b))
    }
}
