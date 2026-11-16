package org.roomscreen.meettwo.mqtt

/** MQTT topics of one tablet; [deviceId] is the topic-safe device ID (serial number). */
class Topics(val deviceId: String, val discoveryPrefix: String) {
    val base = "meettwo/$deviceId"
    val availability = "$base/status"
    val lightState = "$base/led/state"
    val lightSet = "$base/led/set"
    val card = "$base/card/state"
    val screenState = "$base/screen/state"
    val screenSet = "$base/screen/set"
    val wakeOnTouchState = "$base/screen_wake_on_touch/state"
    val wakeOnTouchSet = "$base/screen_wake_on_touch/set"
    val brightnessState = "$base/screen_brightness/state"
    val brightnessSet = "$base/screen_brightness/set"
    val beaconState = "$base/beacon/state"
    fun beaconSet(key: String) = "$base/beacon/$key/set"
    val restart = "$base/restart/press"
    val deviceState = "$base/device/state"

    val discovery = "$discoveryPrefix/device/meettwo_$deviceId/config"
    val homeAssistantStatus = "$discoveryPrefix/status"
}
