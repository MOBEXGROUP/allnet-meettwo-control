package org.roomscreen.meettwo.mqtt

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.roomscreen.meettwo.light.Effect
import org.roomscreen.meettwo.light.LightState
import org.roomscreen.meettwo.light.Rgb

/** Home Assistant MQTT light, JSON schema. */
object LightJson {
    fun state(s: LightState): JsonObject = buildJsonObject {
        put("state", if (s.on) "ON" else "OFF")
        put("brightness", s.brightness)
        put("color_mode", "rgb")
        putJsonObject("color") {
            put("r", s.color.r)
            put("g", s.color.g)
            put("b", s.color.b)
        }
        put("effect", s.effect.id)
    }

    /**
     * Applies a command such as `{"state":"ON","brightness":128,"color":{"r":255,"g":0,"b":0}}`.
     * A colour stops a running effect; a colour, brightness or effect without `state` switches on.
     */
    fun applyCommand(current: LightState, command: JsonObject): LightState {
        var s = current
        val color = runCatching { command["color"]?.jsonObject }.getOrNull()?.let {
            val r = it["r"]?.jsonPrimitive?.intOrNull
            val g = it["g"]?.jsonPrimitive?.intOrNull
            val b = it["b"]?.jsonPrimitive?.intOrNull
            if (r != null && g != null && b != null) Rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255)) else null
        }
        val brightness = command["brightness"]?.jsonPrimitive?.intOrNull?.coerceIn(0, 255)
        val effect = command["effect"]?.jsonPrimitive?.content?.let(Effect::fromId)
        if (color != null) s = s.copy(color = color, effect = Effect.NONE)
        if (brightness != null) s = s.copy(brightness = brightness)
        if (effect != null) s = s.copy(effect = effect)
        val state = command["state"]?.jsonPrimitive?.content
        s = when {
            state.equals("ON", ignoreCase = true) -> s.copy(on = true)
            state.equals("OFF", ignoreCase = true) -> s.copy(on = false)
            color != null || brightness != null || effect != null -> s.copy(on = true)
            else -> s
        }
        if (s.on && s.brightness == 0) s = s.copy(brightness = current.brightness.takeIf { it > 0 } ?: 255)
        return s
    }
}
