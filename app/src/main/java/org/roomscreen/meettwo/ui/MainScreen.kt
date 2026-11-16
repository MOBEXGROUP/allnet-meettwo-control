package org.roomscreen.meettwo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.roomscreen.meettwo.beacon.BeaconStatus
import org.roomscreen.meettwo.light.Effect
import org.roomscreen.meettwo.light.LightState
import org.roomscreen.meettwo.light.Rgb
import org.roomscreen.meettwo.mqtt.MqttStatus

private val PRESETS = listOf(
    Rgb(255, 0, 0), Rgb(0, 255, 0), Rgb(0, 0, 255), Rgb(255, 255, 0),
    Rgb(255, 0, 255), Rgb(0, 255, 255), Rgb(255, 128, 0), Rgb(255, 255, 255),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, openSettings: () -> Unit) {
    val hw by viewModel.hardware.collectAsStateWithLifecycle()
    val conn by viewModel.connectivity.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MeetTwo LED") },
                actions = { TextButton(onClick = openSettings) { Text("Settings") } },
            )
        },
    ) { padding ->
        TwoPane(
            Modifier.padding(padding),
            left = { LightSection(hw.light, hw.ledError, viewModel::updateLight) },
            right = {
                StatusSection(hw, conn)
                ScreenSection(hw, viewModel::setScreenOn, viewModel::setScreenBrightness)
            },
        )
    }
}

@Composable
private fun LightSection(light: LightState, error: String?, update: ((LightState) -> LightState) -> Unit) {
    Section("LED") {
        error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
        SwitchRow("On", light.on) { on -> update { it.copy(on = on) } }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PRESETS.forEach { rgb ->
                Box(
                    Modifier.size(40.dp)
                        .background(Color(rgb.r, rgb.g, rgb.b), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { update { it.copy(on = true, color = rgb, effect = Effect.NONE) } },
                )
            }
        }
        LabeledSlider("Red", light.color.r, 0..255) { v -> update { it.copy(color = it.color.copy(r = v)) } }
        LabeledSlider("Green", light.color.g, 0..255) { v -> update { it.copy(color = it.color.copy(g = v)) } }
        LabeledSlider("Blue", light.color.b, 0..255) { v -> update { it.copy(color = it.color.copy(b = v)) } }
        LabeledSlider("Brightness", light.brightness, 1..255) { v -> update { it.copy(brightness = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Effect.entries.forEach { effect ->
                FilterChip(
                    selected = light.effect == effect,
                    onClick = { update { it.copy(effect = effect, on = true) } },
                    label = { Text(effect.label) },
                )
            }
        }
    }
}

@Composable
private fun StatusSection(hw: HardwareUiState, conn: ConnectivityUiState) {
    Section("Status") {
        when (val s = conn.mqttStatus) {
            MqttStatus.Connected -> StatusLine("MQTT", "connected", true)
            MqttStatus.Connecting -> StatusLine("MQTT", "connecting…", false)
            MqttStatus.Disabled -> StatusLine("MQTT", "disabled", false)
            is MqttStatus.Failed -> StatusLine("MQTT", "not connected (${s.reason})", false)
        }
        StatusLine("Card reader", hw.cardError?.let { "error ($it)" } ?: "ready", hw.cardError == null)
        Column(Modifier.padding(start = 120.dp)) {
            val card = hw.lastCard
            Text(if (card == null) "No card yet" else "${card.id} at ${TIME_FORMAT.format(card.time)}")
        }
        when (val b = conn.beaconStatus) {
            BeaconStatus.Advertising -> StatusLine("Beacon", "advertising", true)
            BeaconStatus.Starting -> StatusLine("Beacon", "starting…", false)
            BeaconStatus.Off -> StatusLine("Beacon", "off", false)
            is BeaconStatus.Failed -> StatusLine("Beacon", "error (${b.reason})", false)
        }
        StatusLine("Network", "${conn.device.ip ?: "no address"} (${conn.device.network})", conn.device.ip != null)
    }
}

@Composable
private fun ScreenSection(hw: HardwareUiState, setOn: (Boolean) -> Unit, setBrightness: (Int) -> Unit) {
    Section("Screen") {
        hw.screenError?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
        LabeledSlider("Brightness", hw.screenBrightness, 1..100, setBrightness)
        Button(onClick = { setOn(false) }) { Text("Switch screen off") }
    }
}
