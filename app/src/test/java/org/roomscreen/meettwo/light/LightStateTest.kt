package org.roomscreen.meettwo.light

import org.junit.Assert.assertEquals
import org.junit.Test

class LightStateTest {
    @Test
    fun fullColourAtFullBrightnessIsMaxLevel() {
        assertEquals(Levels(15, 0, 15), ColorMath.toLevels(Rgb(255, 0, 255), 255))
    }

    @Test
    fun brightnessScalesLevels() {
        assertEquals(Levels(8, 0, 0), ColorMath.toLevels(Rgb(255, 0, 0), 128))
    }

    @Test
    fun dimColourKeepsStrongestChannelAtLevelOne() {
        assertEquals(Levels(1, 0, 0), ColorMath.toLevels(Rgb(255, 40, 0), 5))
    }

    @Test
    fun zeroBrightnessIsOff() {
        assertEquals(Levels.OFF, ColorMath.toLevels(Rgb(255, 255, 255), 0))
    }

    @Test
    fun offStateIsOffRegardlessOfEffect() {
        assertEquals(Levels.OFF, LightState(on = false, effect = Effect.RAINBOW).levelsAt(1234))
    }

    @Test
    fun blinkAlternates() {
        val s = LightState(on = true, color = Rgb(0, 255, 0), effect = Effect.BLINK)
        assertEquals(Levels(0, 15, 0), s.levelsAt(0))
        assertEquals(Levels.OFF, s.levelsAt(LightState.BLINK_HALF_PERIOD_MS))
    }

    @Test
    fun hsvPrimaries() {
        assertEquals(Rgb(255, 0, 0), ColorMath.hsvToRgb(0f, 1f, 1f))
        assertEquals(Rgb(0, 255, 0), ColorMath.hsvToRgb(120f, 1f, 1f))
        assertEquals(Rgb(0, 0, 255), ColorMath.hsvToRgb(240f, 1f, 1f))
    }

    @Test
    fun parsesHex() {
        assertEquals(Rgb(255, 136, 0), Rgb.parseHex("#FF8800"))
        assertEquals("FF8800", Rgb(255, 136, 0).hex)
    }
}
