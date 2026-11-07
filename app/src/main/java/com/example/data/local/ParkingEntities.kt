package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlin.math.ceil
import kotlin.math.max

/**
 * Indian License Plate Regex Validator:
 * Matches standard RTO formats like TN39AB1234, TN42Z987, KA01MJ2024, DL8CAF1234.
 */
val INDIAN_PLATE_REGEX = Regex("^[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{1,4}$")

object VehicleStatus {
    const val INSIDE = "INSIDE"
    const val EXITED = "EXITED"
}

object PaymentStatus {
    const val PENDING = "PENDING"
    const val PAID_CASH = "PAID_CASH"
    const val PAID_UPI = "PAID_UPI"
    const val UNPAID_CREDIT = "UNPAID_CREDIT"
    const val PASS_COVERED = "PASS_COVERED"
}

object PassType {
    const val WEEKLY_PASS = "WEEKLY_PASS"
    const val MONTHLY_PASS = "MONTHLY_PASS"
}

object SyncStatus {
    const val SYNCED = "SYNCED"
    const val PENDING_SYNC = "PENDING_SYNC"
    const val SYNC_FAILED = "SYNC_FAILED"
}

object SyncEntityType {
    const val VEHICLE_LOG = "VEHICLE_LOG"
    const val PASS = "PASS"
    const val CREDIT = "CREDIT"
    const val RATE_CONFIG = "RATE_CONFIG"
}

object SyncOperationType {
    const val CREATE_VEHICLE_LOG = "CREATE_VEHICLE_LOG"
    const val UPDATE_VEHICLE_LOG = "UPDATE_VEHICLE_LOG"
    const val UPSERT_PASS = "UPSERT_PASS"
    const val UPSERT_CREDIT = "UPSERT_CREDIT"
    const val UPSERT_RATE_CONFIG = "UPSERT_RATE_CONFIG"
}

/**
 * 1. VehicleLogEntity: Offline-first Room entity + Firestore document representation.
 * Tracks every vehicle entry, mandatory burned-in timestamp photo, exit duration, settlement mode,
 * and local-to-cloud sync status.
 */
@Entity(
    tableName = "vehicle_logs",
    indices = [Index(value = ["plateNumber"]), Index(value = ["status"]), Index(value = ["syncStatus"])]
)
@IgnoreExtraProperties
data class VehicleLogEntity(
    @PrimaryKey
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val plateNumber: String = "",
    val entryTimestamp: Long = 0L,
    val exitTimestamp: Long? = null,
    val photoPath: String = "",
    val status: String = VehicleStatus.INSIDE,
    val paymentStatus: String = PaymentStatus.PENDING,
    val daysCharged: Int = 1,
    val baseFee: Double = 20.0,
    val previousCreditAdded: Double = 0.0,
    val totalAmountDue: Double = 20.0,
    val amountCollected: Double = 0.0,
    val workerName: String = "Worker",
    val notes: String? = null,
    val syncStatus: String = SyncStatus.SYNCED
) {
    fun toFirestoreMap(isCreate: Boolean = false): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "userId" to userId,
            "plateNumber" to plateNumber,
            "entryTimestamp" to entryTimestamp,
            "exitTimestamp" to exitTimestamp,
            "photoPath" to photoPath,
            "status" to status,
            "paymentStatus" to paymentStatus,
            "daysCharged" to daysCharged,
            "baseFee" to baseFee,
            "previousCreditAdded" to previousCreditAdded,
            "totalAmountDue" to totalAmountDue,
            "amountCollected" to amountCollected,
            "workerName" to workerName,
            "notes" to notes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isCreate) {
            map["createdAt"] = FieldValue.serverTimestamp()
        }
        return map.filterValues { it != null }.mapValues { it.value!! }
    }
}

/**
 * 2. PassEntity: Weekly and Monthly Pass registry per license plate.
 */
@Entity(tableName = "passes")
@IgnoreExtraProperties
data class PassEntity(
    @PrimaryKey
    @DocumentId
    val plateNumber: String = "",
    val userId: String = "",
    val passType: String = PassType.WEEKLY_PASS,
    val startDate: Long = 0L,
    val expiryDate: Long = 0L,
    val amountPaid: Double = 100.0,
    val paymentMode: String = PaymentStatus.PAID_CASH,
    val createdAtEpoch: Long = System.currentTimeMillis()
) {
    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return expiryDate > nowMillis
    }

    fun daysRemaining(nowMillis: Long = System.currentTimeMillis()): Int {
        if (expiryDate <= nowMillis) return 0
        return max(1, ceil((expiryDate - nowMillis) / 86_400_000.0).toInt())
    }

    fun toFirestoreMap(isCreate: Boolean = false): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "userId" to userId,
            "plateNumber" to plateNumber,
            "passType" to passType,
            "startDate" to startDate,
            "expiryDate" to expiryDate,
            "amountPaid" to amountPaid,
            "paymentMode" to paymentMode,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isCreate) {
            map["createdAt"] = FieldValue.serverTimestamp()
        }
        return map
    }
}

/**
 * 3. CreditEntity: Tracks "Pay Tomorrow" (Udhaar) unpaid credit linked to a vehicle plate.
 */
@Entity(tableName = "credits")
@IgnoreExtraProperties
data class CreditEntity(
    @PrimaryKey
    @DocumentId
    val plateNumber: String = "",
    val userId: String = "",
    val pendingAmount: Double = 0.0,
    val lastUnpaidTimestamp: Long = 0L,
    val notes: String = "",
    val isSettled: Boolean = false
) {
    fun toFirestoreMap(isCreate: Boolean = false): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "userId" to userId,
            "plateNumber" to plateNumber,
            "pendingAmount" to pendingAmount,
            "lastUnpaidTimestamp" to lastUnpaidTimestamp,
            "notes" to notes,
            "isSettled" to isSettled,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isCreate) {
            map["createdAt"] = FieldValue.serverTimestamp()
        }
        return map
    }
}

/**
 * 4. RateConfig: Admin/Owner controlled tariff rates, PIN, and low-light threshold.
 */
@Entity(tableName = "rate_config")
@IgnoreExtraProperties
data class RateConfig(
    @PrimaryKey
    val id: Int = 1,
    val userId: String = "",
    val dailyRate: Double = 20.0,
    val weeklyPassRate: Double = 100.0,
    val monthlyPassRate: Double = 350.0,
    val ownerPin: String = "1234",
    val lowLightThreshold: Double = 45.0
) {
    fun toFirestoreMap(isCreate: Boolean = false): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "userId" to userId,
            "dailyRate" to dailyRate,
            "weeklyPassRate" to weeklyPassRate,
            "monthlyPassRate" to monthlyPassRate,
            "ownerPin" to ownerPin,
            "lowLightThreshold" to lowLightThreshold,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isCreate) {
            map["createdAt"] = FieldValue.serverTimestamp()
        }
        return map
    }
}

/**
 * 5. SyncQueueEntity: Durable Room outbox queue for offline-first synchronization.
 * Stores pending mutations made when cellular connectivity is poor or unavailable
 * and flushes them in FIFO order once network connectivity is restored.
 */
@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["entityType", "entityId"], unique = true)]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val queueId: Long = 0L,
    val userId: String = "",
    val entityType: String = SyncEntityType.VEHICLE_LOG,
    val entityId: String = "",
    val operationType: String = SyncOperationType.CREATE_VEHICLE_LOG,
    val enqueuedAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
)

/**
 * Result of flushing the Room offline sync queue to Firestore.
 */
data class SyncFlushResult(
    val syncedCount: Int = 0,
    val remainingCount: Int = 0,
    val failedCount: Int = 0
)

/**
 * Domain summary returned whenever a license plate is scanned or typed.
 */
data class PlateInspectionResult(
    val plateNumber: String,
    val isValidFormat: Boolean,
    val activeInsideLog: VehicleLogEntity? = null,
    val passEntity: PassEntity? = null,
    val isPassActive: Boolean = false,
    val passDaysLeft: Int = 0,
    val unpaidCredit: CreditEntity? = null,
    val exitBillPreview: ExitBillCalculation? = null
)

/**
 * Non-negotiable fee calculation for vehicle exit.
 */
data class ExitBillCalculation(
    val logId: String,
    val plateNumber: String,
    val entryTimestamp: Long,
    val exitTimestamp: Long,
    val photoPath: String,
    val elapsedMillis: Long,
    val daysCharged: Int,
    val dailyRateUsed: Double,
    val isCoveredByPass: Boolean,
    val passDaysRemaining: Int,
    val baseParkingFee: Double,
    val previousPendingCredit: Double,
    val previousCreditDate: Long?,
    val totalAmountDue: Double
)

/**
 * Real-time Owner Dashboard Shift Audit metrics.
 */
data class ShiftAuditSummary(
    val totalBikesInside: Int = 0,
    val totalExitsToday: Int = 0,
    val cashFromExitsToday: Double = 0.0,
    val cashFromPassesToday: Double = 0.0,
    val expectedCashInDrawer: Double = 0.0,
    val upiFromExitsToday: Double = 0.0,
    val upiFromPassesToday: Double = 0.0,
    val totalUpiToday: Double = 0.0,
    val totalPendingCreditAmount: Double = 0.0,
    val activeCreditAccountsCount: Int = 0,
    val activePassesCount: Int = 0
)
