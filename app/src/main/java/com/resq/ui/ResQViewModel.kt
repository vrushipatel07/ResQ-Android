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
import com.resq.ai.classifier.ClassificationResult
import com.resq.ai.classifier.EmergencyClassifier
import com.resq.ai.speech.SpeechInputManager
import com.resq.mesh.packet.MeshProtocol
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class LocationUiState(
    val loading: Boolean = false,
    val fix: LocationFix? = null,
    val error: String? = null
)

data class PacketEvent(val success: Boolean, val message: String)

data class AnalysisUiState(
    val processing: Boolean = false,
    val completedSteps: List<String> = emptyList(),
    val draft: EmergencyDraft? = null,
    val result: ClassificationResult? = null,
    val error: String? = null
)

class ResQViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ResQDatabase.getInstance(application)
    private val repository = EmergencyRepository(database.packetDao())
    private val locationProvider = LocationProvider(application)
    private val speechInput = SpeechInputManager(application)
    val speechState = speechInput.state
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
    val mapMarkers = database.supportDao().observeMarkers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _selectedPacketId = MutableStateFlow<String?>(null)
    val selectedPacketId: StateFlow<String?> = _selectedPacketId.asStateFlow()

    val packets = repository.observePackets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _location = MutableStateFlow(LocationUiState())
    val location: StateFlow<LocationUiState> = _location.asStateFlow()

    private val _events = MutableSharedFlow<PacketEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PacketEvent> = _events.asSharedFlow()
    private val _analysis = MutableStateFlow(AnalysisUiState())
    val analysis: StateFlow<AnalysisUiState> = _analysis.asStateFlow()

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

    fun startSpeechInput() = speechInput.start()
    fun stopSpeechInput() = speechInput.stop()

    fun seedOfflineMapMarkers() {
        val fix = _location.value.fix
        val baseLat = fix?.latitude ?: 12.97160
        val baseLng = fix?.longitude ?: 77.59460
        val now = System.currentTimeMillis()
        val samples = listOf(
            MapMarker("safe-zone-1", "SAFE", "Community Safe Zone", baseLat + .0042, baseLng + .0020, "Open shelter with drinking water", "OFFLINE GUIDE", now),
            MapMarker("medical-1", "MEDICAL", "Medical Point", baseLat - .0031, baseLng + .0045, "First aid and emergency medical support", "OFFLINE GUIDE", now + 1),
            MapMarker("hazard-1", "HAZARD", "Flooded Road", baseLat + .0012, baseLng - .0040, "Road is unsafe; use an alternate route", "LOCAL REPORT", now + 2),
            MapMarker("rescue-1", "RESCUE", "Rescue Point", baseLat - .0040, baseLng - .0025, "Rescue team assembly and pickup point", "RESQ", now + 3)
        )
        viewModelScope.launch {
            samples.forEach { marker -> database.supportDao().upsertMarker(marker) }
            _events.emit(PacketEvent(true, "Offline map markers saved"))
        }
    }

    fun analyzeDraft(draft: EmergencyDraft) {
        viewModelScope.launch {
            _analysis.value = AnalysisUiState(processing = true, draft = draft)
            val steps = listOf(
                "Understanding message",
                "Identifying emergency type",
                "Checking location",
                "Determining priority",
                "Preparing emergency packet"
            )
            val completed = mutableListOf<String>()
            val result = EmergencyClassifier.classify(draft.description, draft.type)
            for (step in steps) {
                delay(220)
                completed += step
                _analysis.value = _analysis.value.copy(completedSteps = completed.toList())
            }
            _analysis.value = _analysis.value.copy(processing = false, result = result)
        }
    }

    fun createAnalyzedPacket() {
        val state = _analysis.value
        val draft = state.draft
        val result = state.result
        if (draft == null || result == null) {
            _events.tryEmit(PacketEvent(false, "Analysis result is not ready"))
            return
        }
        createPacket(result.type, draft.description, result.priority)
    }

    fun createSos() = createPacket(EmergencyType.SOS, "Immediate SOS assistance requested", EmergencyPriority.CRITICAL)

    private fun createPacket(type: EmergencyType, text: String, priority: EmergencyPriority) {
        val fix = _location.value.fix
        if (fix == null) {
            _events.tryEmit(PacketEvent(false, "Get a GPS location before creating the packet"))
            return
        }
        viewModelScope.launch {
            repository.createPacket(deviceId, type, text, priority, fix).fold(
                onSuccess = { packet ->
                    database.supportDao().upsertMarker(
                        MapMarker(
                            id = "packet-${packet.messageId}",
                            markerType = if (packet.type == EmergencyType.MEDICAL) "MEDICAL" else "SOS",
                            title = "${packet.type.label} • ${packet.messageId}",
                            lat = packet.latitude,
                            lng = packet.longitude,
                            description = packet.text,
                            source = packet.senderId,
                            createdAt = packet.timestamp
                        )
                    )
                    _events.emit(PacketEvent(true, "${packet.messageId} saved locally and added to map"))
                },
                onFailure = { _events.emit(PacketEvent(false, it.message ?: "Packet could not be saved")) }
            )
        }
    }

    override fun onCleared() {
        bluetooth.close()
        wifi.close()
        speechInput.close()
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
