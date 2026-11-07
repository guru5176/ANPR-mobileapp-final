package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkingDao {
    // --- Vehicle Logs ---
    @Query("SELECT * FROM vehicle_logs ORDER BY entryTimestamp DESC")
    fun observeAllVehicleLogs(): Flow<List<VehicleLogEntity>>

    @Query("SELECT * FROM vehicle_logs WHERE status = 'INSIDE' ORDER BY entryTimestamp DESC")
    fun observeInsideVehicles(): Flow<List<VehicleLogEntity>>

    @Query("SELECT * FROM vehicle_logs WHERE plateNumber = :plate AND status = 'INSIDE' ORDER BY entryTimestamp DESC LIMIT 1")
    suspend fun getActiveInsideLogForPlate(plate: String): VehicleLogEntity?

    @Query("SELECT * FROM vehicle_logs WHERE id = :id LIMIT 1")
    suspend fun getVehicleLogById(id: String): VehicleLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVehicleLog(log: VehicleLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVehicleLogs(logs: List<VehicleLogEntity>)

    @Query("UPDATE vehicle_logs SET syncStatus = :syncStatus WHERE id = :logId")
    suspend fun updateVehicleLogSyncStatus(logId: String, syncStatus: String)

    // --- Passes ---
    @Query("SELECT * FROM passes ORDER BY expiryDate DESC")
    fun observeAllPasses(): Flow<List<PassEntity>>

    @Query("SELECT * FROM passes WHERE plateNumber = :plate LIMIT 1")
    suspend fun getPassForPlate(plate: String): PassEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPass(pass: PassEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPasses(passes: List<PassEntity>)

    // --- Credits ("Pay Tomorrow" / Udhaar) ---
    @Query("SELECT * FROM credits WHERE isSettled = 0 AND pendingAmount > 0 ORDER BY lastUnpaidTimestamp DESC")
    fun observeActiveCredits(): Flow<List<CreditEntity>>

    @Query("SELECT * FROM credits ORDER BY lastUnpaidTimestamp DESC")
    fun observeAllCredits(): Flow<List<CreditEntity>>

    @Query("SELECT * FROM credits WHERE plateNumber = :plate LIMIT 1")
    suspend fun getCreditForPlate(plate: String): CreditEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCredit(credit: CreditEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCredits(credits: List<CreditEntity>)

    // --- Rate Configuration ---
    @Query("SELECT * FROM rate_config WHERE id = 1 LIMIT 1")
    fun observeRateConfig(): Flow<RateConfig?>

    @Query("SELECT * FROM rate_config WHERE id = 1 LIMIT 1")
    suspend fun getRateConfig(): RateConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRateConfig(config: RateConfig)

    // --- Offline-First Sync Queue (Outbox) ---
    @Query("SELECT * FROM sync_queue ORDER BY enqueuedAt ASC")
    fun observePendingSyncQueue(): Flow<List<SyncQueueEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observePendingSyncCount(): Flow<Int>

    @Query("SELECT * FROM sync_queue ORDER BY enqueuedAt ASC")
    suspend fun getAllPendingSyncItems(): List<SyncQueueEntity>

    @Query("SELECT entityId FROM sync_queue WHERE entityType = :entityType")
    suspend fun getPendingEntityIdsByType(entityType: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueSyncItem(item: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE queueId = :queueId")
    suspend fun deleteSyncItemById(queueId: Long)

    @Query("DELETE FROM sync_queue WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteSyncItemForEntity(entityType: String, entityId: String)

    @Query("UPDATE sync_queue SET retryCount = :retryCount, lastError = :lastError WHERE queueId = :queueId")
    suspend fun updateSyncItemRetry(queueId: Long, retryCount: Int, lastError: String?)
}
