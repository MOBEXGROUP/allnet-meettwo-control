package org.roomscreen.meettwo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.roomscreen.meettwo.BuildConfig
import org.roomscreen.meettwo.settings.BeaconSettings
import org.roomscreen.meettwo.settings.MqttSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, back: () -> Unit) {
    val conn by viewModel.connectivity.collectAsStateWithLifecycle()
    BackHandler(onBack = back)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = back) { Text("Back") } },
            )
        },
    ) { padding ->
        // Fits without scrolling; scrolls only while the soft keyboard covers a field.
        TwoPane(
            Modifier.padding(padding).imePadding(),
            scrollable = true,
            left = {
                conn.mqtt?.let { MqttSection(it, viewModel::saveMqtt) }
                conn.screen?.let { ScreenSection(it.wakeOnTouch, viewModel::setWakeOnTouch) }
            },
            right = {
                conn.beacon?.let { BeaconSection(it, viewModel::saveBeacon) }
                DeviceSection(conn)
            },
        )
    }
}

@Composable
private fun MqttSection(saved: MqttSettings, save: (MqttSettings) -> Unit) {
    var enabled by rememberSaveable(saved) { mutableStateOf(saved.enabled) }
    var host by rememberSaveable(saved) { mutableStateOf(saved.host) }
    var port by rememberSaveable(saved) { mutableStateOf(saved.port.toString()) }
    var user by rememberSaveable(saved) { mutableStateOf(saved.username) }
    var password by rememberSaveable(saved) { mutableStateOf(saved.password) }
    var prefix by rememberSaveable(saved) { mutableStateOf(saved.discoveryPrefix) }

    Section("Home Assistant (MQTT)") {
        SwitchRow("Enabled", enabled) { enabled = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(host, { host = it }, Modifier.weight(3f), label = { Text("Broker host") }, singleLine = true)
            OutlinedTextField(
                port, { port = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Port") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(user, { user = it }, Modifier.weight(1f), label = { Text("User") }, singleLine = true)
            OutlinedTextField(
                password, { password = it }, Modifier.weight(1f), label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        }
        OutlinedTextField(
            prefix, { prefix = it }, Modifier.fillMaxWidth(), label = { Text("Discovery prefix") }, singleLine = true,
        )
        Button(onClick = {
            save(
                MqttSettings(
                    enabled = enabled,
                    host = host,
                    port = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: MqttSettings.DEFAULT_PORT,
                    username = user,
                    password = password,
                    discoveryPrefix = prefix,
                ),
            )
        }) { Text("Save") }
    }
}

@Composable
private fun ScreenSection(wakeOnTouch: Boolean, setWakeOnTouch: (Boolean) -> Unit) {
    Section("Screen") {
        SwitchRow("Wake screen on touch", wakeOnTouch, setWakeOnTouch)
        if (!wakeOnTouch) {
            Text(
                "While the screen is off, touch and the power button do nothing; Home Assistant switches it on.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BeaconSection(saved: BeaconSettings, save: (BeaconSettings) -> Unit) {
    var enabled by rememberSaveable(saved) { mutableStateOf(saved.enabled) }
    var uuid by rememberSaveable(saved) { mutableStateOf(BeaconSettings.uuidWithDashes(saved.uuid)) }
    var major by rememberSaveable(saved) { mutableStateOf(saved.major.toString()) }
    var minor by rememberSaveable(saved) { mutableStateOf(saved.minor.toString()) }
    val normalized = BeaconSettings.normalizeUuid(uuid)

    Section("Bluetooth beacon (iBeacon)") {
        SwitchRow("Enabled", enabled) { enabled = it }
        OutlinedTextField(
            uuid, { uuid = it }, Modifier.fillMaxWidth(), label = { Text("UUID") }, singleLine = true,
            isError = normalized == null,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                major, { major = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Major") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                minor, { minor = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Minor") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Button(
            enabled = normalized != null,
            onClick = {
                save(
                    BeaconSettings(
                        enabled = enabled,
                        uuid = normalized ?: saved.uuid,
                        major = major.toIntOrNull()?.coerceIn(0, 65535) ?: saved.major,
                        minor = minor.toIntOrNull()?.coerceIn(0, 65535) ?: saved.minor,
                    ),
                )
            },
        ) { Text("Save") }
    }
}

@Composable
private fun DeviceSection(conn: ConnectivityUiState) {
    val d = conn.device
    Section("Device") {
        Text("App version ${BuildConfig.VERSION_NAME} · ID ${conn.deviceId ?: "…"}")
        Text("IP address ${d.ip ?: "–"} (${d.network})" + (d.wifiRssi?.let { ", Wi-Fi $it dBm" } ?: ""))
        Text("Foreground app ${d.foregroundApp ?: "–"}")
        Text("Last boot ${TIME_FORMAT.format(d.lastBoot)}")
    }
}
