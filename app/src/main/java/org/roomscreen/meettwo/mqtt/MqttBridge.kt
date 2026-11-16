package org.roomscreen.meettwo.mqtt

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.roomscreen.meettwo.BuildConfig
import org.roomscreen.meettwo.beacon.BeaconController
import org.roomscreen.meettwo.card.CardReader
import org.roomscreen.meettwo.device.DeviceDiagnostics
import org.roomscreen.meettwo.light.LightController
import org.roomscreen.meettwo.screen.ScreenController
import org.roomscreen.meettwo.settings.BeaconSettings
import org.roomscreen.meettwo.settings.MqttSettings
import org.roomscreen.meettwo.settings.SettingsRepository
import org.roomscreen.meettwo.system.DeviceIdentity
import org.roomscreen.meettwo.system.RootShell
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Connects to the MQTT broker (MQTT 3.1.1), announces the tablet to Home Assistant via
 * discovery and maps entity states and commands to the controllers. Reconnects with
 * back-off; a settings change reconnects immediately.
 */
class MqttBridge(
    private val settings: SettingsRepository,
    private val light: LightController,
    private val cards: CardReader,
    private val screen: ScreenController,
    private val beacon: BeaconController,
    private val diagnostics: DeviceDiagnostics,
    private val root: RootShell,
) {
    private val _status = MutableStateFlow<MqttStatus>(MqttStatus.Disabled)
    val status: StateFlow<MqttStatus> = _status.asStateFlow()

    suspend fun run(identity: DeviceIdentity) {
        settings.mqtt.collectLatest { s ->
            if (!s.isUsable) {
                _status.value = MqttStatus.Disabled
                return@collectLatest
            }
            var backoffMs = MIN_BACKOFF_MS
            while (true) {
                _status.value = MqttStatus.Connecting
                try {
                    session(s, identity) { backoffMs = MIN_BACKOFF_MS }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "MQTT session ended", e)
                    _status.value = MqttStatus.Failed(e.message ?: e.javaClass.simpleName)
                }
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    private suspend fun session(s: MqttSettings, identity: DeviceIdentity, onConnected: () -> Unit): Nothing = coroutineScope {
        val topics = Topics(identity.id, s.discoveryPrefix)
        val disconnected = CompletableDeferred<Throwable>()
        val client = MqttClient.builder()
            .useMqttVersion3()
            .identifier("meettwo-${identity.id}")
            .serverHost(s.host)
            .serverPort(s.port)
            .addDisconnectedListener { disconnected.complete(it.cause) }
            .buildAsync()
        try {
            client.connectWith()
                .keepAlive(KEEP_ALIVE_S)
                .cleanSession(true)
                .apply {
                    if (s.username.isNotEmpty()) {
                        simpleAuth().username(s.username).password(s.password.toByteArray()).applySimpleAuth()
                    }
                }
                .willPublish()
                .topic(topics.availability).payload(OFFLINE).qos(MqttQos.AT_LEAST_ONCE).retain(true)
                .applyWillPublish()
                .send()
                .await()
            onConnected()
            _status.value = MqttStatus.Connected
            Log.i(TAG, "Connected to ${s.host}:${s.port}")

            val sources = stateSources(topics)
            suspend fun announce() {
                client.publish(topics.discovery, HaDiscovery.payload(topics, identity, BuildConfig.VERSION_NAME).toString())
                client.publish(topics.availability, ONLINE_TEXT)
                // Current values; flows without a value yet (no card read) are skipped.
                sources.forEach { (topic, flow) ->
                    withTimeoutOrNull(500) { flow.first() }?.let { client.publish(topic, it) }
                }
            }
            subscribe(client, topics, this) { announce() }
            announce()
            sources.forEach { (topic, flow) -> launch { flow.drop(1).collect { client.publish(topic, it) } } }

            val cause = disconnected.await()
            throw IOException("Disconnected: ${cause.message ?: cause.javaClass.simpleName}", cause)
        } finally {
            withContext(NonCancellable) {
                if (client.state.isConnected) {
                    withTimeoutOrNull(2_000) {
                        runCatching {
                            client.publish(topics.availability, OFFLINE.decodeToString())
                            client.disconnect().await()
                        }
                    }
                }
            }
        }
    }

    private suspend fun subscribe(
        client: Mqtt3AsyncClient,
        topics: Topics,
        scope: CoroutineScope,
        announce: suspend () -> Unit,
    ) {
        val handlers: Map<String, suspend (String) -> Unit> = mapOf(
            topics.lightSet to { payload ->
                val command = Json.parseToJsonElement(payload).jsonObject
                light.update { LightJson.applyCommand(it, command) }
            },
            topics.screenSet to { payload -> screen.setOn(payload.equals("ON", ignoreCase = true)) },
            topics.wakeOnTouchSet to { payload -> settings.setWakeOnTouch(payload.equals("ON", ignoreCase = true)) },
            topics.brightnessSet to { payload -> payload.toFloatOrNull()?.let { screen.setBrightness(it.toInt()) } },
            topics.beaconSet("on") to { payload ->
                settings.updateBeacon { it.copy(enabled = payload.equals("ON", ignoreCase = true)) }
            },
            topics.beaconSet("uuid") to { payload ->
                BeaconSettings.normalizeUuid(payload)?.let { uuid -> settings.updateBeacon { it.copy(uuid = uuid) } }
            },
            topics.beaconSet("major") to { payload ->
                payload.toFloatOrNull()?.let { v -> settings.updateBeacon { it.copy(major = v.toInt()) } }
            },
            topics.beaconSet("minor") to { payload ->
                payload.toFloatOrNull()?.let { v -> settings.updateBeacon { it.copy(minor = v.toInt()) } }
            },
            topics.restart to { _ -> root.exec("svc power reboot || reboot") },
            topics.homeAssistantStatus to { payload ->
                // Home Assistant restarted: announce the device and resend the states.
                if (payload == "online") announce()
            },
        )
        handlers.forEach { (topic, handle) ->
            client.subscribeWith()
                .topicFilter(topic)
                .qos(MqttQos.AT_MOST_ONCE)
                .callback { publish: Mqtt3Publish ->
                    val payload = publish.payloadAsBytes.decodeToString().trim()
                    scope.launch {
                        runCatching { handle(payload) }
                            .onFailure { Log.w(TAG, "Command on $topic failed: $payload", it) }
                    }
                }
                .send()
                .await()
        }
    }

    /** Retained state topics and the payloads to publish on them. */
    private fun stateSources(topics: Topics): List<Pair<String, Flow<String>>> = listOf(
        topics.lightState to light.state.map { LightJson.state(it).toString() },
        topics.card to cards.lastCard.filterNotNull().map { card ->
            buildJsonObject {
                put("id", card.id)
                put("time", card.time.toOffsetString())
            }.toString()
        },
        topics.screenState to screen.on.map { if (it) "ON" else "OFF" },
        topics.brightnessState to screen.brightness.map { it.toString() },
        topics.wakeOnTouchState to settings.screen.map { if (it.wakeOnTouch) "ON" else "OFF" },
        topics.beaconState to settings.beacon.map { b ->
            buildJsonObject {
                put("on", if (b.enabled) "ON" else "OFF")
                put("uuid", BeaconSettings.uuidWithDashes(b.uuid))
                put("major", b.major)
                put("minor", b.minor)
            }.toString()
        },
        topics.deviceState to diagnostics.status.map { d ->
            buildJsonObject {
                put("ip", d.ip)
                put("network", d.network)
                put("wifi_rssi", d.wifiRssi)
                put("foreground_app", d.foregroundApp)
                put("last_boot", d.lastBoot.toOffsetString())
            }.toString()
        },
    ).map { (topic, flow) -> topic to flow.distinctUntilChanged() }

    private fun Instant.toOffsetString() = atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()

    private suspend fun Mqtt3AsyncClient.publish(topic: String, payload: String) {
        publishWith()
            .topic(topic)
            .payload(payload.toByteArray())
            .qos(MqttQos.AT_MOST_ONCE)
            .retain(true)
            .send()
            .orTimeout(10, TimeUnit.SECONDS)
            .await()
    }

    private companion object {
        const val TAG = "MqttBridge"
        const val KEEP_ALIVE_S = 30
        const val MIN_BACKOFF_MS = 5_000L
        const val MAX_BACKOFF_MS = 60_000L
        const val ONLINE_TEXT = "online"
        val OFFLINE = "offline".toByteArray()
    }
}
