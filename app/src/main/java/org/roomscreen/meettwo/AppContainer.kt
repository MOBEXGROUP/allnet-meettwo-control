package org.roomscreen.meettwo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.roomscreen.meettwo.beacon.BeaconController
import org.roomscreen.meettwo.card.CardReader
import org.roomscreen.meettwo.device.DeviceDiagnostics
import org.roomscreen.meettwo.hardware.NativeLedDevice
import org.roomscreen.meettwo.light.LightController
import org.roomscreen.meettwo.light.LightStateStore
import org.roomscreen.meettwo.mqtt.MqttBridge
import org.roomscreen.meettwo.screen.ScreenController
import org.roomscreen.meettwo.settings.SettingsRepository
import org.roomscreen.meettwo.system.DeviceIdentity
import org.roomscreen.meettwo.system.RootShell
import org.roomscreen.meettwo.system.SelfProvisioning

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")
private val Context.lightStore: DataStore<Preferences> by preferencesDataStore("light")

/** Manual dependency injection: one instance of each component per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val root = RootShell()
    val settings = SettingsRepository(appContext.settingsStore)
    val light = LightController(NativeLedDevice(), LightStateStore(appContext.lightStore), appScope)
    val cards = CardReader(root)
    val screen = ScreenController(appContext, root)
    val beacon = BeaconController(appContext, root)
    val diagnostics = DeviceDiagnostics(appContext)
    val mqtt = MqttBridge(settings, light, cards, screen, beacon, diagnostics, root)
    private val provisioning = SelfProvisioning(appContext, root)

    /** Resolved once (needs root for the serial number). */
    val identity = appScope.async { DeviceIdentity.resolve(appContext, root) }

    init {
        appScope.launch { settings.ensureDefaults() }
    }

    /**
     * Everything that runs in the background: card reader, screen and network observers,
     * beacon and MQTT. Runs until the calling coroutine (the foreground service) is cancelled.
     */
    suspend fun runBackground() = coroutineScope {
        provisioning.run()
        launch { cards.run() }
        launch { screen.run(settings.screen) }
        launch { diagnostics.run() }
        launch { beacon.run(settings.beacon) }
        launch { mqtt.run(identity.await()) }
    }
}
