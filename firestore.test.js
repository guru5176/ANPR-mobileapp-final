const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_stand_1";
const BOB_UID = "bob_stand_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write vehicle_logs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("vehicle_logs").get()
  );
});

test("Authenticated owner can create and query valid vehicle_logs, passes, credits, and config", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("vehicle_logs")
      .doc("log_101")
      .set({
        userId: ALICE_UID,
        plateNumber: "TN39AB1234",
        entryTimestamp: 1730000000000,
        photoPath: "/data/user/0/entry_101.jpg",
        status: "INSIDE",
        paymentStatus: "PENDING",
        daysCharged: 1,
        baseFee: 20,
        previousCreditAdded: 0,
        totalAmountDue: 20,
        amountCollected: 0,
        workerName: "Ramesh (Shift A)",
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("passes")
      .doc("TN39AB1234")
      .set({
        userId: ALICE_UID,
        plateNumber: "TN39AB1234",
        passType: "WEEKLY_PASS",
        startDate: 1730000000000,
        expiryDate: 1730604800000,
        amountPaid: 100,
        paymentMode: "PAID_CASH",
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("credits")
      .doc("TN42XY9876")
      .set({
        userId: ALICE_UID,
        plateNumber: "TN42XY9876",
        pendingAmount: 40,
        lastUnpaidTimestamp: 1730000000000,
        notes: "Pay tomorrow regular commuter",
        isSettled: false,
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("config")
      .doc("rates")
      .set({
        userId: ALICE_UID,
        dailyRate: 20,
        weeklyPassRate: 100,
        monthlyPassRate: 350,
        ownerPin: "1234",
        lowLightThreshold: 45,
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("Cross-user access is strictly rejected", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("vehicle_logs")
      .doc("log_101")
      .set({
        userId: ALICE_UID,
        plateNumber: "TN39AB1234",
        entryTimestamp: 1730000000000,
        photoPath: "/data/user/0/entry_101.jpg",
        status: "INSIDE",
        paymentStatus: "PENDING",
        daysCharged: 1,
        baseFee: 20,
        previousCreditAdded: 0,
        totalAmountDue: 20,
        amountCollected: 0,
        workerName: "Ramesh",
        createdAt: now,
        updatedAt: now,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("vehicle_logs")
      .doc("log_101")
      .get()
  );
});

test("Shadow update with ghost field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("vehicle_logs")
      .doc("log_shadow")
      .set({
        userId: ALICE_UID,
        plateNumber: "TN39AB1234",
        entryTimestamp: 1730000000000,
        photoPath: "/data/user/0/entry_101.jpg",
        status: "INSIDE",
        paymentStatus: "PENDING",
        daysCharged: 1,
        baseFee: 20,
        previousCreditAdded: 0,
        totalAmountDue: 20,
        amountCollected: 0,
        workerName: "Ramesh",
        isSuperAdmin: true,
        createdAt: now,
        updatedAt: now,
      })
  );
});
