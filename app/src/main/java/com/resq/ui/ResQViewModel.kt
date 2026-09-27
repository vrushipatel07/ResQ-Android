package com.resq.ui

import android.app.Application
import android.content.Context
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.resq.data.db.ResQDatabase
import com.resq.data.model.*
import com.resq.data.repository.DeviceIdRepository
import com.resq.data.repository.EmergencyRepository
import com.resq.location.LocationFix
import com.resq.location.LocationProvider
import com.resq.mesh.bluetooth.BluetoothMeshManager
import com.resq.mesh.bluetooth.PeerDevice
import com.resq.mesh.wifi.WifiDirectManager
import com.resq.mesh.wifi.WifiPeer
import com.resq.ai.decision.*
import com.resq.mesh.packet.MeshProtocol
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LocationUiState(
    val loading: Boolean = false,
    val fix: LocationFix? = null,
    val error: String? = null
)

data class PacketEvent(val success: Boolean, val message: String)

class ResQViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ResQDatabase.getInstance(application)
    private val repository = EmergencyRepository(database.packetDao())
    private val locationProvider = LocationProvider(application)
    val deviceId = DeviceIdRepository(application).getOrCreate()
    private val rolePreferences = application.getSharedPreferences("resq_role", 0)
    private val _rescueMode = MutableStateFlow(rolePreferences.getBoolean("rescue_mode", false))
    val rescueMode: StateFlow<Boolean> = _rescueMode.asStateFlow()
    private val bluetooth = BluetoothMeshManager(
        application,
        repository,
        database.supportDao(),
        deviceId,
        isRescueMode = { _rescueMode.value }
    )
    val meshState = bluetooth.state
    private val wifi = WifiDirectManager(
        application,
        repository,
        database.supportDao(),
        deviceId,
        isRescueMode = { _rescueMode.value }
    )
    val wifiState = wifi.state
    val forwardingLogs = database.supportDao().observeForwardingLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _selectedPacketId = MutableStateFlow<String?>(null)
    val selectedPacketId: StateFlow<String?> = _selectedPacketId.asStateFlow()

    val packets = repository.observePackets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _location = MutableStateFlow(LocationUiState())
    val location: StateFlow<LocationUiState> = _location.asStateFlow()

    private val _events = MutableSharedFlow<PacketEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PacketEvent> = _events.asSharedFlow()

    fun hasLocationPermission() = locationProvider.hasPermission()

    fun refreshBluetooth() = bluetooth.refresh()
    fun startBluetoothServer() = bluetooth.startServer()
    fun scanForPeers() = bluetooth.startDiscovery()
    fun discoverWifiPeers() = wifi.discoverPeers()
    fun startWifiHost() = wifi.startHost()
    fun selectPacket(messageId: String) { _selectedPacketId.value = messageId }
    fun sendSelectedPacket(peer: PeerDevice) {
        val packet = packets.value.firstOrNull { it.messageId == _selectedPacketId.value }
            ?: packets.value.firstOrNull()
        if (packet == null) _events.tryEmit(PacketEvent(false, "Create an SOS or report first"))
        else bluetooth.send(packet, peer)
    }
    fun sendSelectedPacketWifi(peer: WifiPeer) {
        val packet = packets.value.firstOrNull { it.messageId == _selectedPacketId.value }
            ?: packets.value.firstOrNull()
        if (packet == null) _events.tryEmit(PacketEvent(false, "Create an SOS or report first"))
        else wifi.connectAndSend(peer, packet)
    }

    fun batteryPercent(): Int =
        (getApplication<Application>().getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)

    fun currentDecision(): DecisionResult {
        val packet = packets.value.firstOrNull { it.messageId == _selectedPacketId.value } ?: packets.value.firstOrNull()
        if (packet == null) return DecisionResult(
            CommunicationMethod.STORE_RETRY,
            "Create an emergency packet before choosing a communication path.",
            listOf("No packet selected")
        )
        val logs = forwardingLogs.value.take(20)
        return CommunicationDecisionEngine.decide(
            DecisionInput(
                priority = packet.priority,
                packetSizeBytes = MeshProtocol.encodePacket(packet, deviceId).toByteArray().size,
                batteryPercent = batteryPercent(),
                bluetoothPeerAvailable = meshState.value.peers.any { it.paired },
                wifiPeerAvailable = wifiState.value.peers.isNotEmpty() || wifiState.value.connected,
                bluetoothRecentFailures = logs.count { it.method == "BLUETOOTH" && it.result == "FAILED" },
                wifiRecentFailures = logs.count { it.method == "WIFI_LOCAL" && it.result == "FAILED" }
            )
        )
    }
    fun setRescueMode(enabled: Boolean) {
        _rescueMode.value = enabled
        rolePreferences.edit().putBoolean("rescue_mode", enabled).apply()
        if (enabled) bluetooth.startServer()
    }

    fun refreshLocation() {
        viewModelScope.launch {
            _location.value = LocationUiState(loading = true)
            locationProvider.currentLocation().fold(
                onSuccess = { _location.value = LocationUiState(fix = it) },
                onFailure = { _location.value = LocationUiState(error = it.message ?: "Location unavailable") }
            )
        }
    }

    fun createSos() = createPacket(EmergencyType.SOS, "Immediate SOS assistance requested", EmergencyPriority.CRITICAL)

    fun createReport(draft: EmergencyDraft) {
        val priority = when (draft.type) {
            EmergencyType.FIRE, EmergencyType.FLOOD, EmergencyType.MEDICAL -> EmergencyPriority.URGENT
            else -> EmergencyPriority.NORMAL
        }
        createPacket(draft.type, draft.description, priority)
    }

    private fun createPacket(type: EmergencyType, text: String, priority: EmergencyPriority) {
        val fix = _location.value.fix
        if (fix == null) {
            _events.tryEmit(PacketEvent(false, "Get a GPS location before creating the packet"))
            return
        }
        viewModelScope.launch {
            repository.createPacket(deviceId, type, text, priority, fix).fold(
                onSuccess = { _events.emit(PacketEvent(true, "${it.messageId} saved locally")) },
                onFailure = { _events.emit(PacketEvent(false, it.message ?: "Packet could not be saved")) }
            )
        }
    }

    override fun onCleared() {
        bluetooth.close()
        wifi.close()
        super.onCleared()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.AndroidViewModelFactory(application) {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ResQViewModel(application) as T
            }
    }
}
