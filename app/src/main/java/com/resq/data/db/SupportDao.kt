package com.resq.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.resq.data.model.DeviceState
import com.resq.data.model.ForwardingLog
import com.resq.data.model.MapMarker

@Dao
interface SupportDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertForwardingLog(log: ForwardingLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMarker(marker: MapMarker)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeviceState(state: DeviceState)
}
