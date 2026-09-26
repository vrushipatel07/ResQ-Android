package com.resq.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resq.data.model.EmergencyPacket
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyPacketDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(packet: EmergencyPacket): Long

    @Query("SELECT * FROM emergency_packets ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<EmergencyPacket>>

    @Query("SELECT * FROM emergency_packets WHERE messageId = :id LIMIT 1")
    suspend fun findById(id: String): EmergencyPacket?

    @Query("SELECT COUNT(*) FROM emergency_packets")
    fun observeCount(): Flow<Int>
}
