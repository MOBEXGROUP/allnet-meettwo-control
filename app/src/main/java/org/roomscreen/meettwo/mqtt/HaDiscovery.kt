package org.roomscreen.meettwo.mqtt

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.roomscreen.meettwo.light.Effect
import org.roomscreen.meettwo.system.DeviceIdentity

/**
 * Home Assistant device-based MQTT discovery: one retained message describes the device
 * and all its entities (`<prefix>/device/meettwo_<id>/config`).
 */
object HaDiscovery {
    fun payload(
        topics: Topics,
        identity: DeviceIdentity,
        appVersion: String,
    ): JsonObject = buildJsonObject {
        val uid = "meettwo_${identity.id}"
        putJsonObject("device") {
            putJsonArray("identifiers") { add(uid) }
            // Named by serial number like the room screens; renamed in Home Assistant if needed.
            put("name", "MeetTwo ${identity.serial ?: identity.id}")
            put("manufacturer", "ALLNET")
            put("model", "MeetTwo")
            put("model_id", identity.model)
            put("sw_version", "${identity.firmware}, LED app $appVersion")
            identity.serial?.let { put("serial_number", it) }
            if (identity.macs.isNotEmpty()) {
                putJsonArray("connections") {
                    identity.macs.forEach { mac -> addJsonArray { add("mac"); add(mac) } }
                }
            }
        }
        putJsonObject("origin") {
            put("name", "MeetTwo LED")
            put("sw_version", appVersion)
        }
        put("availability_topic", topics.availability)
        put("qos", 0)
        putJsonObject("components") {
            component("led", "light", uid) {
                put("name", "LED")
                put("schema", "json")
                put("state_topic", topics.lightState)
                put("command_topic", topics.lightSet)
                putJsonArray("supported_color_modes") { add("rgb") }
                put("brightness", true)
                put("effect", true)
                putJsonArray("effect_list") { Effect.entries.forEach { add(it.id) } }
                put("icon", "mdi:led-strip-variant")
            }
            component("card", "sensor", uid) {
                put("name", "Last card")
                put("state_topic", topics.card)
                put("value_template", "{{ value_json.id }}")
                put("json_attributes_topic", topics.card)
                put("json_attributes_template", "{{ {'time': value_json.time} | tojson }}")
                put("icon", "mdi:card-account-details")
            }
            component("screen", "switch", uid) {
                put("name", "Screen")
                put("state_topic", topics.screenState)
                put("command_topic", topics.screenSet)
                put("icon", "mdi:monitor")
            }
            component("screen_wake_on_touch", "switch", uid) {
                put("name", "Wake screen on touch")
                put("state_topic", topics.wakeOnTouchState)
                put("command_topic", topics.wakeOnTouchSet)
                put("entity_category", "config")
                put("icon", "mdi:gesture-tap")
            }
            component("screen_brightness", "number", uid) {
                put("name", "Screen brightness")
                put("state_topic", topics.brightnessState)
                put("command_topic", topics.brightnessSet)
                put("min", 1)
                put("max", 100)
                put("step", 1)
                put("mode", "slider")
                put("unit_of_measurement", "%")
                put("icon", "mdi:brightness-6")
            }
            component("beacon", "switch", uid) {
                put("name", "Bluetooth beacon")
                put("state_topic", topics.beaconState)
                put("value_template", "{{ value_json.on }}")
                put("command_topic", topics.beaconSet("on"))
                put("entity_category", "config")
                put("icon", "mdi:bluetooth-audio")
            }
            component("beacon_uuid", "text", uid) {
                put("name", "Beacon UUID")
                put("state_topic", topics.beaconState)
                put("value_template", "{{ value_json.uuid }}")
                put("command_topic", topics.beaconSet("uuid"))
                put("pattern", "^[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}$")
                put("entity_category", "config")
            }
            listOf("major" to "Beacon major", "minor" to "Beacon minor").forEach { (key, name) ->
                component("beacon_$key", "number", uid) {
                    put("name", name)
                    put("state_topic", topics.beaconState)
                    put("value_template", "{{ value_json.$key }}")
                    put("command_topic", topics.beaconSet(key))
                    put("min", 0)
                    put("max", 65535)
                    put("mode", "box")
                    put("entity_category", "config")
                }
            }
            component("restart", "button", uid) {
                put("name", "Restart")
                put("command_topic", topics.restart)
                put("payload_press", "PRESS")
                put("device_class", "restart")
                put("entity_category", "config")
            }
            diagnostic("ip", "IP address", uid, topics) { put("icon", "mdi:ip-network") }
            diagnostic("network", "Network", uid, topics) { put("icon", "mdi:lan") }
            diagnostic("wifi_rssi", "Wi-Fi signal", uid, topics) {
                put("device_class", "signal_strength")
                put("unit_of_measurement", "dBm")
                put("state_class", "measurement")
            }
            diagnostic("foreground_app", "Foreground app", uid, topics) { put("icon", "mdi:application") }
            diagnostic("last_boot", "Last boot", uid, topics) { put("device_class", "timestamp") }
        }
    }

    private fun JsonObjectBuilder.component(
        key: String,
        platform: String,
        uid: String,
        body: JsonObjectBuilder.() -> Unit,
    ) {
        putJsonObject(key) {
            put("platform", platform)
            put("unique_id", "${uid}_$key")
            body()
        }
    }

    private fun JsonObjectBuilder.diagnostic(
        key: String,
        name: String,
        uid: String,
        topics: Topics,
        body: JsonObjectBuilder.() -> Unit,
    ) = component(key, "sensor", uid) {
        put("name", name)
        put("state_topic", topics.deviceState)
        put("value_template", "{{ value_json.$key }}")
        put("entity_category", "diagnostic")
        body()
    }
}
