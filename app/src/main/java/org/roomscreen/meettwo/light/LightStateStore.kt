package org.roomscreen.meettwo.light

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/** Keeps the last light state so it survives app and tablet restarts. */
class LightStateStore(private val dataStore: DataStore<Preferences>) {
    suspend fun load(): LightState {
        val p = dataStore.data.first()
        val default = LightState()
        return LightState(
            on = p[ON] ?: default.on,
            color = p[COLOR]?.let(Rgb::parseHex) ?: default.color,
            brightness = p[BRIGHTNESS] ?: default.brightness,
            effect = p[EFFECT]?.let(Effect::fromId) ?: default.effect,
        )
    }

    suspend fun save(state: LightState) {
        dataStore.edit {
            it[ON] = state.on
            it[COLOR] = state.color.hex
            it[BRIGHTNESS] = state.brightness
            it[EFFECT] = state.effect.id
        }
    }

    private companion object {
        val ON = booleanPreferencesKey("light_on")
        val COLOR = stringPreferencesKey("light_color")
        val BRIGHTNESS = intPreferencesKey("light_brightness")
        val EFFECT = stringPreferencesKey("light_effect")
    }
}
