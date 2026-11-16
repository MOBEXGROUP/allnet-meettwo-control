package org.roomscreen.meettwo.mqtt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.roomscreen.meettwo.light.Effect
import org.roomscreen.meettwo.light.LightState
import org.roomscreen.meettwo.light.Rgb

class LightJsonTest {
    private fun cmd(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun colourCommandSwitchesOnAndStopsEffect() {
        val s = LightJson.applyCommand(
            LightState(on = false, effect = Effect.RAINBOW),
            cmd("""{"state":"ON","color":{"r":255,"g":0,"b":0}}"""),
        )
        assertEquals(LightState(on = true, color = Rgb(255, 0, 0), effect = Effect.NONE), s)
    }

    @Test
    fun brightnessWithoutStateSwitchesOn() {
        val s = LightJson.applyCommand(LightState(on = false), cmd("""{"brightness":100}"""))
        assertEquals(true, s.on)
        assertEquals(100, s.brightness)
    }

    @Test
    fun offKeepsColour() {
        val start = LightState(on = true, color = Rgb(1, 2, 3))
        assertEquals(start.copy(on = false), LightJson.applyCommand(start, cmd("""{"state":"OFF"}""")))
    }

    @Test
    fun effectCommand() {
        val s = LightJson.applyCommand(LightState(on = true), cmd("""{"state":"ON","effect":"breathe"}"""))
        assertEquals(Effect.BREATHE, s.effect)
    }

    @Test
    fun stateJson() {
        val json = LightJson.state(LightState(on = true, color = Rgb(10, 20, 30), brightness = 99)).toString()
        assertEquals(
            """{"state":"ON","brightness":99,"color_mode":"rgb","color":{"r":10,"g":20,"b":30},"effect":"none"}""",
            json,
        )
    }
}
