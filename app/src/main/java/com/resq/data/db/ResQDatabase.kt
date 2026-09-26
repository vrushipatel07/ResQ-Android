package com.resq.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.resq.data.model.DeviceState
import com.resq.data.model.EmergencyPacket
import com.resq.data.model.ForwardingLog
import com.resq.data.model.MapMarker

@Database(
    entities = [EmergencyPacket::class, ForwardingLog::class, MapMarker::class, DeviceState::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ResQDatabase : RoomDatabase() {
    abstract fun packetDao(): EmergencyPacketDao
    abstract fun supportDao(): SupportDao

    companion object {
        @Volatile private var instance: ResQDatabase? = null

        fun getInstance(context: Context): ResQDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ResQDatabase::class.java,
                "resq.db"
            ).build().also { instance = it }
        }
    }
}
