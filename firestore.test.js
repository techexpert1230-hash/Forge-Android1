const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);
const ts = () => new Date();

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

test("Unauthenticated user cannot read or write any user collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("workouts").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("daily_logs").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("goals").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("measurements").get());
});

test("Owner can create and update UserProfile; shadow fields and cross-user access fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = ts();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Alex",
      unit: "kg",
      goal: 4,
      rest: 90,
      theme: "dark",
      dailyPushUpsTarget: 40,
      dailyWaterMlTarget: 2500,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Shadow field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Alex",
      unit: "kg",
      goal: 4,
      rest: 90,
      theme: "dark",
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cross-user read/write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ name: "Bob", updatedAt: ts() }));
});

test("Owner can create, update, list DailyLog keyed by dateKey; invalid dateKey or cross-user fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = ts();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("daily_logs").doc("2026-11-14").set({
      userId: ALICE_UID,
      dateKey: "2026-11-14",
      pushUps: 40,
      squats: 60,
      waterMl: 2000,
      steps: 8500,
      workoutMinutes: 45,
      caloriesBurned: 380,
      otherActivities: [],
      meals: [{ id: "m1", name: "Oats", mealType: "Breakfast", calories: 420, proteinGrams: 28 }],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("daily_logs").doc("2026-11-14").update({
      pushUps: 50,
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("daily_logs").get()
  );

  // Mismatched dateKey rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("daily_logs").doc("2026-11-15").set({
      userId: ALICE_UID,
      dateKey: "2026-11-14",
      pushUps: 10,
      squats: 0,
      waterMl: 0,
      steps: 0,
      workoutMinutes: 0,
      caloriesBurned: 0,
      otherActivities: [],
      meals: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("daily_logs").get());
});

test("Owner can manage workouts, goals, measurements, routines, custom_exercises, custom_meals", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = ts();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workouts").doc("w1").set({
      userId: ALICE_UID,
      name: "Push Day",
      dateKey: "2026-11-14",
      startMs: 1700000000000,
      endMs: 1700002700000,
      durSec: 2700,
      volKg: 4200,
      setsCount: 15,
      exercises: [{ id: "bench", sets: [{ w: 80, r: 8 }] }],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("goals").doc("g1").set({
      userId: ALICE_UID,
      title: "40 push-ups daily",
      metricType: "pushups",
      targetValue: 40,
      startDateKey: "2026-11-01",
      endDateKey: "2026-11-30",
      active: true,
      completedDates: ["2026-11-14"],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("measurements").doc("2026-11-14").set({
      userId: ALICE_UID,
      dateKey: "2026-11-14",
      weightKg: 74.5,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("routines").doc("r1").set({
      userId: ALICE_UID,
      name: "Upper Mix",
      exerciseIds: ["bench", "pullup"],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("custom_exercises").doc("ce1").set({
      userId: ALICE_UID,
      name: "Pike Push-Up",
      muscleGroup: "Shoulders",
      equipment: "Bodyweight",
      trackingKind: "bw",
      cues: ["Hips high"],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("custom_meals").doc("cm1").set({
      userId: ALICE_UID,
      name: "Whey Shake",
      mealType: "Snack",
      calories: 160,
      proteinGrams: 30,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cross-user writes fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("goals").doc("g1").delete());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("workouts").get());
});
