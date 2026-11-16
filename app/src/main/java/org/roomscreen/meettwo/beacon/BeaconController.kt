package org.roomscreen.meettwo.beacon

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import org.roomscreen.meettwo.settings.BeaconSettings
import org.roomscreen.meettwo.system.RootShell
import java.nio.ByteBuffer

sealed interface BeaconStatus {
    data object Off : BeaconStatus
    data object Starting : BeaconStatus
    data object Advertising : BeaconStatus
    data class Failed(val reason: String) : BeaconStatus
}

/** Sends an Apple iBeacon over Bluetooth LE while enabled in the settings. */
class BeaconController(private val context: Context, private val root: RootShell) {
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _status = MutableStateFlow<BeaconStatus>(BeaconStatus.Off)
    val status: StateFlow<BeaconStatus> = _status.asStateFlow()

    suspend fun run(settings: Flow<BeaconSettings>) {
        settings.collectLatest { s ->
            if (!s.enabled) {
                _status.value = BeaconStatus.Off
                return@collectLatest
            }
            _status.value = BeaconStatus.Starting
            val failure = prepare()
            if (failure != null) {
                _status.value = BeaconStatus.Failed(failure)
                return@collectLatest
            }
            @Suppress("MissingPermission") // checked in prepare()
            advertise(s)
        }
    }

    /** Returns an error message, or `null` when Bluetooth is ready to advertise. */
    private suspend fun prepare(): String? {
        val adapter = adapter ?: return "No Bluetooth adapter"
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            return "Permission BLUETOOTH_ADVERTISE missing"
        }
        if (!adapter.isEnabled) {
            root.exec("cmd bluetooth_manager enable")
                .onFailure { return "Bluetooth is off and could not be switched on: ${it.message}" }
            withTimeoutOrNull(BLUETOOTH_ON_TIMEOUT_MS) {
                while (!adapter.isEnabled) delay(250)
            } ?: return "Bluetooth did not switch on"
        }
        return if (adapter.bluetoothLeAdvertiser == null) "Bluetooth LE advertising not supported" else null
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private suspend fun advertise(s: BeaconSettings) {
        val advertiser = adapter?.bluetoothLeAdvertiser ?: return
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                _status.value = BeaconStatus.Advertising
            }

            override fun onStartFailure(errorCode: Int) {
                Log.w(TAG, "Advertising failed: $errorCode")
                _status.value = BeaconStatus.Failed("Advertising failed (error $errorCode)")
            }
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .addManufacturerData(APPLE_COMPANY_ID, iBeaconPayload(s))
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()
        advertiser.startAdvertising(settings, data, callback)
        try {
            awaitCancellation()
        } finally {
            runCatching { advertiser.stopAdvertising(callback) }
            _status.value = BeaconStatus.Off
        }
    }

    companion object {
        private const val TAG = "BeaconController"
        private const val APPLE_COMPANY_ID = 0x004C
        private const val BLUETOOTH_ON_TIMEOUT_MS = 15_000L
        /** Calibrated RSSI at 1 m, as advertised in the beacon (typical value). */
        private const val MEASURED_POWER: Byte = -59

        /** iBeacon manufacturer data: type 0x02, length 0x15, UUID, major, minor, measured power. */
        fun iBeaconPayload(s: BeaconSettings): ByteArray {
            val uuid = s.uuid.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            require(uuid.size == 16) { "Beacon UUID must have 32 hex digits" }
            return ByteBuffer.allocate(23)
                .put(0x02.toByte()).put(0x15.toByte())
                .put(uuid)
                .putShort(s.major.toShort())
                .putShort(s.minor.toShort())
                .put(MEASURED_POWER)
                .array()
        }
    }
}
