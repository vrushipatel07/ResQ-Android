package com.resq.mesh.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.*
import android.os.Build
import androidx.core.content.ContextCompat
import com.resq.data.db.SupportDao
import com.resq.data.model.EmergencyPacket
import com.resq.data.model.ForwardingLog
import com.resq.data.repository.EmergencyRepository
import com.resq.mesh.packet.PacketJsonCodec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class PeerDevice(val name: String, val address: String, val paired: Boolean)

data class MeshUiState(
    val available: Boolean = true,
    val enabled: Boolean = false,
    val scanning: Boolean = false,
    val listening: Boolean = false,
    val peers: List<PeerDevice> = emptyList(),
    val status: String = "Bluetooth not started",
    val error: String? = null,
    val receivedPacketId: String? = null
)

class BluetoothMeshManager(
    context: Context,
    private val emergencyRepository: EmergencyRepository,
    private val supportDao: SupportDao,
    private val localDeviceId: String
) {
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(MeshUiState(available = adapter != null))
    val state: StateFlow<MeshUiState> = _state.asStateFlow()
    private var serverJob: Job? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> deviceFrom(intent)?.let(::addPeer)
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _state.value = _state.value.copy(scanning = false, status = "Scan finished")
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> refresh()
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    @SuppressLint("MissingPermission")
    fun refresh() {
        val bluetooth = adapter ?: run {
            _state.value = MeshUiState(available = false, status = "Bluetooth is not supported")
            return
        }
        val enabled = runCatching { bluetooth.isEnabled }.getOrDefault(false)
        val bonded = if (enabled) runCatching {
            bluetooth.bondedDevices.map { device ->
                PeerDevice(device.name ?: "Unknown device", device.address, true)
            }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList()) else emptyList()
        _state.value = _state.value.copy(
            enabled = enabled,
            peers = mergePeers(bonded, _state.value.peers),
            status = if (enabled) "Ready for Bluetooth mesh" else "Bluetooth is off",
            error = null
        )
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        val bluetooth = adapter ?: return
        if (!bluetooth.isEnabled) {
            _state.value = _state.value.copy(error = "Turn on Bluetooth first")
            return
        }
        refresh()
        bluetooth.cancelDiscovery()
        val started = bluetooth.startDiscovery()
        _state.value = _state.value.copy(
            scanning = started,
            status = if (started) "Scanning for nearby phones…" else "Bluetooth scan could not start",
            error = if (started) null else "Check Nearby devices permission"
        )
    }

    @SuppressLint("MissingPermission")
    fun startServer() {
        val bluetooth = adapter ?: return
        if (!bluetooth.isEnabled || serverJob?.isActive == true) return
        serverJob = scope.launch {
            try {
                serverSocket = bluetooth.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                _state.value = _state.value.copy(listening = true, status = "Listening for emergency packets", error = null)
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    launch { receive(socket) }
                }
            } catch (error: Exception) {
                if (isActive) _state.value = _state.value.copy(listening = false, error = error.message ?: "Bluetooth receiver stopped")
            } finally {
                _state.value = _state.value.copy(listening = false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun send(packet: EmergencyPacket, peer: PeerDevice) {
        val bluetooth = adapter ?: return
        scope.launch {
            _state.value = _state.value.copy(status = "Sending ${packet.messageId} to ${peer.name}…", error = null)
            bluetooth.cancelDiscovery()
            val result = runCatching {
                val device = bluetooth.getRemoteDevice(peer.address)
                device.createRfcommSocketToServiceRecord(SERVICE_UUID).use { socket ->
                    socket.connect()
                    socket.outputStream.bufferedWriter().use { writer ->
                        writer.write(PacketJsonCodec.encode(packet))
                        writer.newLine()
                        writer.flush()
                    }
                }
            }
            val now = System.currentTimeMillis()
            result.fold(
                onSuccess = {
                    emergencyRepository.markForwarded(packet.messageId)
                    supportDao.insertForwardingLog(
                        ForwardingLog(messageId = packet.messageId, fromDevice = localDeviceId, toDevice = peer.address, method = "BLUETOOTH", timestamp = now, result = "SENT")
                    )
                    _state.value = _state.value.copy(status = "${packet.messageId} sent to ${peer.name}", error = null)
                },
                onFailure = { error ->
                    supportDao.insertForwardingLog(
                        ForwardingLog(messageId = packet.messageId, fromDevice = localDeviceId, toDevice = peer.address, method = "BLUETOOTH", timestamp = now, result = "FAILED")
                    )
                    _state.value = _state.value.copy(error = error.message ?: "Bluetooth send failed", status = "Packet remains stored")
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun receive(socket: BluetoothSocket) {
        val remote = runCatching { socket.remoteDevice.name ?: socket.remoteDevice.address }.getOrDefault("Nearby device")
        val json = runCatching {
            socket.use { it.inputStream.bufferedReader().readLine() }
        }.getOrElse {
            _state.value = _state.value.copy(error = it.message ?: "Could not read incoming packet")
            return
        }
        if (json.isNullOrBlank() || json.length > MAX_PACKET_CHARS) {
            _state.value = _state.value.copy(error = "Malformed or oversized packet rejected")
            return
        }
        emergencyRepository.receiveSerializedPacket(json).fold(
            onSuccess = { packet ->
                supportDao.insertForwardingLog(
                    ForwardingLog(messageId = packet.messageId, fromDevice = packet.senderId, toDevice = localDeviceId, method = "BLUETOOTH", timestamp = System.currentTimeMillis(), result = "RECEIVED")
                )
                _state.value = _state.value.copy(status = "Received ${packet.messageId} from $remote", receivedPacketId = packet.messageId, error = null)
            },
            onFailure = { error ->
                _state.value = _state.value.copy(error = error.message ?: "Incoming packet rejected", status = "Packet not stored")
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun addPeer(device: BluetoothDevice) {
        val peer = PeerDevice(device.name ?: "Unknown device", device.address, device.bondState == BluetoothDevice.BOND_BONDED)
        _state.value = _state.value.copy(peers = mergePeers(listOf(peer), _state.value.peers))
    }

    private fun mergePeers(first: List<PeerDevice>, second: List<PeerDevice>): List<PeerDevice> =
        (first + second).associateBy { it.address }.values.sortedWith(compareByDescending<PeerDevice> { it.paired }.thenBy { it.name.lowercase() })

    @Suppress("DEPRECATION")
    private fun deviceFrom(intent: Intent): BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    @SuppressLint("MissingPermission")
    fun close() {
        runCatching { adapter?.cancelDiscovery() }
        runCatching { serverSocket?.close() }
        serverJob?.cancel()
        scope.cancel()
        if (receiverRegistered) runCatching { appContext.unregisterReceiver(receiver) }
        receiverRegistered = false
    }

    companion object {
        const val SERVICE_NAME = "ResQ Emergency Mesh"
        val SERVICE_UUID: UUID = UUID.fromString("4c0f2f96-907d-4a2b-a537-4dbd9bd5b278")
        private const val MAX_PACKET_CHARS = 8_192
    }
}
