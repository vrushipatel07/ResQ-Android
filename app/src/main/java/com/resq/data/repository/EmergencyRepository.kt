package com.resq.data.repository

import com.resq.data.db.EmergencyPacketDao
import com.resq.data.model.*
import com.resq.location.LocationFix
import com.resq.mesh.packet.PacketJsonCodec
import com.resq.mesh.packet.PacketValidator
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class EmergencyRepository(private val dao: EmergencyPacketDao) {
    fun observePackets(): Flow<List<EmergencyPacket>> = dao.observeAll()

    suspend fun createPacket(
        senderId: String,
        type: EmergencyType,
        text: String,
        priority: EmergencyPriority,
        location: LocationFix
    ): Result<EmergencyPacket> {
        val now = System.currentTimeMillis()
        val packet = EmergencyPacket(
            messageId = "EMG-${now.toString().takeLast(6)}-${UUID.randomUUID().toString().take(4).uppercase()}",
            priority = priority,
            type = type,
            text = text.trim(),
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = now,
            senderId = senderId,
            status = PacketStatus.STORED,
            hopCount = 0,
            lastForwardedAt = null
        )
        return PacketValidator.validate(packet).fold(
            onSuccess = {
                val inserted = dao.insert(it)
                if (inserted == -1L) Result.failure(IllegalStateException("Duplicate message ID rejected"))
                else Result.success(it)
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun receiveSerializedPacket(json: String): Result<EmergencyPacket> =
        PacketJsonCodec.decode(json).fold(
            onSuccess = { packet ->
                if (dao.insert(packet) == -1L) Result.failure(IllegalStateException("Duplicate message ID rejected"))
                else Result.success(packet)
            },
            onFailure = { Result.failure(it) }
        )
}
