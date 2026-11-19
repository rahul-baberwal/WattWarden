package com.rahulbaberwal.wattwarden.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "charge_sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val startLevel: Int,
    val endLevel: Int,
    val peakWatts: Float = 0f,
    val avgWatts: Float = 0f,
    val maxTempC: Float = 0f,
    val startCounterUah: Long = 0,
    val endCounterUah: Long = 0,
    val charger: String,
    val label: String = "",
    val estimatedCapacityMah: Int? = null,
    /** Fraction of a full cycle this session represents (20% -> 70% = 0.5). */
    val cycleFraction: Float = 0f,
)

@Entity(tableName = "samples", indices = [Index("sessionId")])
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val time: Long,
    val level: Int,
    val currentMa: Float,
    val voltageMv: Int,
    val tempC: Float,
    val watts: Float,
)

@Entity(tableName = "drain_samples", indices = [Index("time")])
data class DrainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val level: Int,
    val screenOn: Boolean,
    val charging: Boolean,
)

@Dao
interface GuardianDao {
    @Insert
    suspend fun insertSession(s: SessionEntity): Long

    @Query(
        """UPDATE charge_sessions SET endTime=:endTime, endLevel=:endLevel, peakWatts=:peak,
           avgWatts=:avg, maxTempC=:maxTemp, endCounterUah=:endCounter,
           estimatedCapacityMah=:est, cycleFraction=:cycle WHERE id=:id"""
    )
    suspend fun updateSessionStats(
        id: Long, endTime: Long, endLevel: Int, peak: Float, avg: Float, maxTemp: Float,
        endCounter: Long, est: Int?, cycle: Float,
    )

    @Query("UPDATE charge_sessions SET label=:label WHERE id=:id")
    suspend fun setLabel(id: Long, label: String)

    @Query("DELETE FROM charge_sessions WHERE id=:id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM samples WHERE sessionId=:id")
    suspend fun deleteSamples(id: Long)

    @Query("DELETE FROM charge_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM samples")
    suspend fun clearSamples()

    @Insert
    suspend fun insertSample(s: SampleEntity)

    @Query("SELECT * FROM charge_sessions ORDER BY startTime DESC")
    fun sessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM samples WHERE sessionId=:id ORDER BY time")
    fun samples(id: Long): Flow<List<SampleEntity>>

    @Query(
        """SELECT * FROM samples WHERE sessionId =
           (SELECT id FROM charge_sessions ORDER BY startTime DESC LIMIT 1) ORDER BY time"""
    )
    fun latestSessionSamples(): Flow<List<SampleEntity>>

    @Query(
        """SELECT estimatedCapacityMah FROM charge_sessions
           WHERE estimatedCapacityMah IS NOT NULL ORDER BY startTime DESC LIMIT 5"""
    )
    suspend fun recentEstimates(): List<Int>

    @Query(
        """SELECT estimatedCapacityMah FROM charge_sessions
           WHERE estimatedCapacityMah IS NOT NULL ORDER BY startTime DESC LIMIT 5"""
    )
    fun recentEstimatesFlow(): Flow<List<Int>>

    @Insert
    suspend fun insertDrain(d: DrainEntity)

    @Query("SELECT * FROM drain_samples WHERE time >= :since ORDER BY time")
    suspend fun drainSince(since: Long): List<DrainEntity>

    @Query("DELETE FROM drain_samples WHERE time < :before")
    suspend fun pruneDrain(before: Long)

    @Query("DELETE FROM drain_samples")
    suspend fun clearDrain()
}

@Database(
    entities = [SessionEntity::class, SampleEntity::class, DrainEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class GuardianDatabase : RoomDatabase() {
    abstract fun dao(): GuardianDao

    companion object {
        fun build(context: Context): GuardianDatabase =
            Room.databaseBuilder(context, GuardianDatabase::class.java, "wattwarden.db").build()
    }
}
