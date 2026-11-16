package org.roomscreen.meettwo.mqtt

sealed interface MqttStatus {
    data object Disabled : MqttStatus
    data object Connecting : MqttStatus
    data object Connected : MqttStatus
    data class Failed(val reason: String) : MqttStatus
}
