package org.roomscreen.meettwo.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

data class MqttSettings(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val username: String = "",
    val password: String = "",
    val discoveryPrefix: String = DEFAULT_DISCOVERY_PREFIX,
) {
    val isUsable: Boolean get() = enabled && host.isNotBlank()

    companion object {
        const val DEFAULT_PORT = 1883
        const val DEFAULT_DISCOVERY_PREFIX = "homeassistant"
    }
}

data class BeaconSettings(
    val enabled: Boolean = false,
    /** 32 hex digits without dashes; empty until [SettingsRepository.ensureDefaults] ran. */
    val uuid: String = "",
    val major: Int = 1,
    val minor: Int = 1,
) {
    companion object {
        /** Accepts the UUID with or without dashes and returns 32 lower-case hex digits. */
        fun normalizeUuid(value: String): String? {
            val hex = value.trim().replace("-", "").lowercase()
            return hex.takeIf { it.length == 32 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
        }

        fun uuidWithDashes(hex: String): String =
            if (hex.length != 32) hex
            else "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
                "${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}

data class ScreenSettings(
    /**
     * When off, touching the dark screen does not wake it (and neither does the power button,
     * see [org.roomscreen.meettwo.screen.ScreenController]); only Home Assistant does.
     */
    val wakeOnTouch: Boolean = true,
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val mqtt: Flow<MqttSettings> = dataStore.data.map { it.toMqtt() }.distinctUntilChanged()

    val beacon: Flow<BeaconSettings> = dataStore.data.map { it.toBeacon() }.distinctUntilChanged()

    val screen: Flow<ScreenSettings> = dataStore.data
        .map { ScreenSettings(wakeOnTouch = it[WAKE_ON_TOUCH] ?: true) }
        .distinctUntilChanged()

    /** Creates the random beacon UUID on first start. */
    suspend fun ensureDefaults() {
        dataStore.edit {
            if (it[BEACON_UUID].isNullOrEmpty()) it[BEACON_UUID] = UUID.randomUUID().toString().replace("-", "")
        }
    }

    suspend fun updateMqtt(transform: (MqttSettings) -> MqttSettings) {
        dataStore.edit { p ->
            val s = transform(p.toMqtt())
            p[MQTT_ENABLED] = s.enabled
            p[MQTT_HOST] = s.host.trim()
            p[MQTT_PORT] = s.port
            p[MQTT_USER] = s.username.trim()
            p[MQTT_PASSWORD] = s.password
            p[MQTT_PREFIX] = s.discoveryPrefix.trim()
        }
    }

    suspend fun updateBeacon(transform: (BeaconSettings) -> BeaconSettings) {
        dataStore.edit { p ->
            val s = transform(p.toBeacon())
            p[BEACON_ENABLED] = s.enabled
            p[BEACON_UUID] = s.uuid
            p[BEACON_MAJOR] = s.major.coerceIn(0, 65535)
            p[BEACON_MINOR] = s.minor.coerceIn(0, 65535)
        }
    }

    suspend fun setWakeOnTouch(enabled: Boolean) {
        dataStore.edit { it[WAKE_ON_TOUCH] = enabled }
    }

    private fun Preferences.toMqtt() = MqttSettings(
        enabled = this[MQTT_ENABLED] ?: false,
        host = this[MQTT_HOST].orEmpty(),
        port = this[MQTT_PORT] ?: MqttSettings.DEFAULT_PORT,
        username = this[MQTT_USER].orEmpty(),
        password = this[MQTT_PASSWORD].orEmpty(),
        discoveryPrefix = this[MQTT_PREFIX]?.takeIf { it.isNotBlank() } ?: MqttSettings.DEFAULT_DISCOVERY_PREFIX,
    )

    private fun Preferences.toBeacon() = BeaconSettings(
        enabled = this[BEACON_ENABLED] ?: false,
        uuid = this[BEACON_UUID].orEmpty(),
        major = this[BEACON_MAJOR] ?: 1,
        minor = this[BEACON_MINOR] ?: 1,
    )

    private companion object {
        val MQTT_ENABLED = booleanPreferencesKey("mqtt_enabled")
        val MQTT_HOST = stringPreferencesKey("mqtt_host")
        val MQTT_PORT = intPreferencesKey("mqtt_port")
        val MQTT_USER = stringPreferencesKey("mqtt_user")
        val MQTT_PASSWORD = stringPreferencesKey("mqtt_password")
        val MQTT_PREFIX = stringPreferencesKey("mqtt_discovery_prefix")
        val BEACON_ENABLED = booleanPreferencesKey("beacon_enabled")
        val BEACON_UUID = stringPreferencesKey("beacon_uuid")
        val BEACON_MAJOR = intPreferencesKey("beacon_major")
        val BEACON_MINOR = intPreferencesKey("beacon_minor")
        val WAKE_ON_TOUCH = booleanPreferencesKey("screen_wake_on_touch")
    }
}
