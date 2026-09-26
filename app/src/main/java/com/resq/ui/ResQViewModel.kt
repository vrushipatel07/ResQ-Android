package com.resq.ui

import android.app.Application
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
    private val bluetooth = BluetoothMeshManager(application, repository, database.supportDao(), deviceId)
    val meshState = bluetooth.state

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
    fun sendLatestPacket(peer: PeerDevice) {
        val packet = packets.value.firstOrNull()
        if (packet == null) _events.tryEmit(PacketEvent(false, "Create an SOS or report first"))
        else bluetooth.send(packet, peer)
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
