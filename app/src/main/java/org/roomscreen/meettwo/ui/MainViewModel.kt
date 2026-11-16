package org.roomscreen.meettwo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.roomscreen.meettwo.AppContainer
import org.roomscreen.meettwo.beacon.BeaconStatus
import org.roomscreen.meettwo.card.Card
import org.roomscreen.meettwo.device.DeviceStatus
import org.roomscreen.meettwo.light.LightState
import org.roomscreen.meettwo.mqtt.MqttStatus
import org.roomscreen.meettwo.settings.BeaconSettings
import org.roomscreen.meettwo.settings.MqttSettings
import org.roomscreen.meettwo.settings.ScreenSettings

data class HardwareUiState(
    val light: LightState = LightState(),
    val ledError: String? = null,
    val screenOn: Boolean = true,
    val screenBrightness: Int = 50,
    val screenError: String? = null,
    val lastCard: Card? = null,
    val cardError: String? = null,
)

data class ConnectivityUiState(
    val mqttStatus: MqttStatus = MqttStatus.Disabled,
    val mqtt: MqttSettings? = null,
    val beaconStatus: BeaconStatus = BeaconStatus.Off,
    val beacon: BeaconSettings? = null,
    val screen: ScreenSettings? = null,
    val device: DeviceStatus = DeviceStatus(),
    val deviceId: String? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(private val container: AppContainer) : ViewModel() {
    val hardware: StateFlow<HardwareUiState> = combine(
        combine(container.light.state, container.light.error, ::Pair),
        combine(container.screen.on, container.screen.brightness, container.screen.error, ::Triple),
        combine(container.cards.lastCard, container.cards.error, ::Pair),
    ) { (light, ledError), (screenOn, brightness, screenError), (card, cardError) ->
        HardwareUiState(light, ledError, screenOn, brightness, screenError, card, cardError)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HardwareUiState())

    val connectivity: StateFlow<ConnectivityUiState> = combine(
        combine(container.mqtt.status, container.settings.mqtt, ::Pair),
        combine(container.beacon.status, container.settings.beacon, ::Pair),
        container.settings.screen,
        container.diagnostics.status,
    ) { (mqttStatus, mqtt), (beaconStatus, beacon), screen, device ->
        ConnectivityUiState(
            mqttStatus = mqttStatus,
            mqtt = mqtt,
            beaconStatus = beaconStatus,
            beacon = beacon,
            screen = screen,
            device = device,
            deviceId = container.identity.takeIf { it.isCompleted }?.getCompleted()?.id,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectivityUiState())

    fun updateLight(transform: (LightState) -> LightState) = container.light.update(transform)

    fun setScreenOn(on: Boolean) {
        viewModelScope.launch { container.screen.setOn(on) }
    }

    fun setScreenBrightness(percent: Int) {
        viewModelScope.launch { container.screen.setBrightness(percent) }
    }

    fun saveMqtt(settings: MqttSettings) {
        viewModelScope.launch { container.settings.updateMqtt { settings } }
    }

    fun setWakeOnTouch(enabled: Boolean) {
        viewModelScope.launch { container.settings.setWakeOnTouch(enabled) }
    }

    fun saveBeacon(settings: BeaconSettings) {
        viewModelScope.launch { container.settings.updateBeacon { settings } }
    }
}
