package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.PassType
import com.example.data.local.PaymentStatus
import com.example.data.local.VehicleStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun logVehicleEntryAndProcessExit_authenticatedOwner_succeedsAndSyncs() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = BillingRepository(firestore = firestore, parkingDao = null)

        val createdLog = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.logVehicleEntry(
                plateNumber = "TN39AB1234",
                photoPath = "/storage/emulated/0/entry_test.jpg",
                workerName = "Senthil",
                notes = "Black Splendor Plus"
            ).getOrThrow()
        }
        assertEquals("TN39AB1234", createdLog.plateNumber)
        assertEquals(VehicleStatus.INSIDE, createdLog.status)

        // Verify realtime stream emits the created entry
        val logs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCloudVehicleLogs().first { list -> list.any { it.id == createdLog.id } }
        }
        assertTrue(logs.any { it.id == createdLog.id })

        // Process exit with Pay Tomorrow (UNPAID_CREDIT)
        val exitTime = createdLog.entryTimestamp + (26 * 3600 * 1000L) // 26 hours -> 2 days charged
        val exitedLog = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.processVehicleExit(
                logId = createdLog.id,
                paymentChoice = PaymentStatus.UNPAID_CREDIT,
                exitTimestamp = exitTime
            ).getOrThrow()
        }
        assertEquals(VehicleStatus.EXITED, exitedLog.status)
        assertEquals(2, exitedLog.daysCharged)
        assertEquals(40.0, exitedLog.totalAmountDue, 0.01)
    }

    @Test
    fun registerPassAndRateConfig_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = BillingRepository(firestore = firestore, parkingDao = null)

        val createdPass = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerOrRenewPass(
                plateNumber = "TN42XY9876",
                passType = PassType.WEEKLY_PASS,
                paymentMode = PaymentStatus.PAID_CASH
            ).getOrThrow()
        }
        assertEquals("TN42XY9876", createdPass.plateNumber)

        val updatedConfig = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateRateConfig(
                dailyRate = 25.0,
                weeklyPassRate = 120.0,
                monthlyPassRate = 400.0,
                ownerPin = "4321",
                lowLightThreshold = 50.0
            ).getOrThrow()
        }
        assertEquals(25.0, updatedConfig.dailyRate, 0.01)
    }

    @Test
    fun getVehicleLog_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = BillingRepository(firestore = firestore, parkingDao = null)
        val createdLog = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.logVehicleEntry(
                plateNumber = "TN33CD5555",
                photoPath = "/storage/emulated/0/entry_33.jpg",
                workerName = "Kumar"
            ).getOrThrow()
        }

        // Switch to Bob and attempt to read Alice's vehicle log
        signInTestUser(BOB_EMAIL)
        val bobRepo = BillingRepository(firestore = firestore, parkingDao = null)
        var caughtException: Throwable? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getVehicleLogFromCloud(aliceUid, createdLog.id)
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull(caughtException)
        val firestoreEx = caughtException?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeCloudVehicleLogs_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()

        val repository = BillingRepository(firestore = firestore, parkingDao = null)
        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.getVehicleLogFromCloud(aliceUid, "nonexistent_log_id")
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull(caughtException)
        val firestoreEx = caughtException?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "owner_alice@motovault.test"
        const val BOB_EMAIL = "owner_bob@motovault.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
