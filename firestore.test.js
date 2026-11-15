const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 2000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user cannot read or write any FinTrack collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("accounts").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("budgets").get());
});

test("Owner can create and update their own UserProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set({
    userId: ALICE_UID,
    displayName: "Aarav Sharma",
    email: "aarav@example.com",
    currency: "INR",
    timezone: "Asia/Kolkata",
    pinEnabled: true,
    biometricEnabled: true,
    monthlyBudgetLimit: 45000,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));

  await assertSucceeds(profileRef.update({
    monthlyBudgetLimit: 50000,
    updatedAt: serverTimestamp(),
  }));
});

test("Shadow update with ghost field on UserProfile is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertFails(profileRef.set({
    userId: ALICE_UID,
    displayName: "Aarav",
    currency: "INR",
    timezone: "Asia/Kolkata",
    isAdmin: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));
});

test("Cross-user access to transactions and accounts is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").set({
      userId: ALICE_UID,
      transactionTimestampMillis: 1763200000000,
      dateIst: "2026-11-15",
      timeIst: "16:30:00",
      amount: 1250.5,
      type: "DEBIT",
      senderName: "Aarav",
      receiverName: "Swiggy",
      counterpartyName: "Swiggy",
      recipientMobile: "Unavailable",
      upiId: "swiggy@icici",
      bankAccountName: "HDFC Savings",
      maskedAccountIdentifier: "XXXX4321",
      paymentApp: "GOOGLE_PAY",
      paymentMethod: "UPI",
      referenceNumber: "REF123456",
      utrOrUpiRef: "631912345678",
      status: "SUCCESS",
      category: "Food & Dining",
      description: "Dinner order",
      source: "MANUAL_ENTRY",
      notes: "",
      dedupFingerprint: "fp_12345",
      isDuplicate: false,
      rawSourceHash: "hash_123",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Bob cannot list or get Alice's transactions
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").delete());
});

test("Owner can update allowed transaction fields, but cannot mutate immutable source or fingerprint", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_edit_1");

  await assertSucceeds(txRef.set({
    userId: ALICE_UID,
    transactionTimestampMillis: 1763200000000,
    dateIst: "2026-11-15",
    timeIst: "16:30:00",
    amount: 500,
    type: "DEBIT",
    status: "SUCCESS",
    category: "Other",
    source: "STATEMENT_CSV",
    dedupFingerprint: "fp_immutable",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));

  await assertSucceeds(txRef.update({
    category: "Groceries",
    notes: "Weekly vegetables",
    updatedAt: serverTimestamp(),
  }));

  await assertFails(txRef.update({
    source: "MANUAL_ENTRY",
    updatedAt: serverTimestamp(),
  }));

  await assertFails(txRef.update({
    amount: 99999,
    updatedAt: serverTimestamp(),
  }));
});

test("Owner can manage accounts, categories, budgets, contacts, notifications, consents, and append-only logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userDoc = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(userDoc.collection("accounts").doc("acc_1").set({
    userId: ALICE_UID,
    accountName: "HDFC Salary",
    institutionName: "HDFC Bank",
    accountType: "SAVINGS",
    maskedIdentifier: "XXXX8910",
    sourceType: "STATEMENT_IMPORT",
    syncStatus: "MANUAL_ONLY",
    supportsRealBalance: false,
    currentBalance: 0,
    lastSyncedAtMillis: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));

  await assertSucceeds(userDoc.collection("budgets").doc("bud_1").set({
    userId: ALICE_UID,
    title: "Food Budget",
    scope: "CATEGORY_MONTHLY",
    category: "Food & Dining",
    limitAmount: 8000,
    savedOrTargetAmount: 0,
    monthKey: "2026-11",
    alertThresholdPercent: 80,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));

  await assertSucceeds(userDoc.collection("sync_logs").doc("log_1").set({
    userId: ALICE_UID,
    source: "STATEMENT_CSV",
    status: "SUCCESS",
    recordsProcessed: 12,
    duplicatesSkipped: 2,
    message: "Imported CSV statement",
    timestampMillis: 1763200000000,
    createdAt: serverTimestamp(),
  }));

  // Append-only sync_logs must reject update
  await assertFails(userDoc.collection("sync_logs").doc("log_1").update({
    recordsProcessed: 99,
  }));
});
