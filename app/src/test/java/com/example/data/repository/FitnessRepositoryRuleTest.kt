package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.BodyMeasurement
import com.example.data.model.CustomExercise
import com.example.data.model.CustomMeal
import com.example.data.model.CustomRoutine
import com.example.data.model.DailyLog
import com.example.data.model.PersonalGoal
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FitnessRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertAndGetUserProfile_ownerSucceeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = FitnessRepository(firestore, auth)

        val saveRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertUserProfile(UserProfile(userId = uid, name = "Alex", unit = "kg", goal = 4L, rest = 90L, theme = "dark"))
        }
        assertTrue(saveRes.isSuccess)

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getUserProfile().getOrThrow() }
        assertNotNull(fetched)
        assertEquals("Alex", fetched?.name)
    }

    @Test
    fun saveAndObserveWorkout_dailyLog_goal_measurement_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = FitnessRepository(firestore, auth)
        val workoutId = "w-${UUID.randomUUID().toString().take(8)}"

        val wRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveWorkout(
                WorkoutSession(
                    id = workoutId,
                    name = "Push Day",
                    dateKey = "2026-11-14",
                    startMs = 1700000000000L,
                    endMs = 1700001800000L,
                    durSec = 1800L,
                    volKg = 2400.0,
                    setsCount = 10L,
                    pushUpsCount = 40L,
                    exercises = listOf(mapOf("id" to "bench", "sets" to listOf(mapOf("w" to 60.0, "r" to 10L))))
                )
            )
        }
        assertTrue(wRes.isSuccess)

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeWorkouts(uid).first { list -> list.any { it.id == workoutId } }
        }
        assertTrue(emitted.any { it.id == workoutId })

        val logRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertDailyLog(
                DailyLog(
                    dateKey = "2026-11-14",
                    pushUps = 45L,
                    squats = 60L,
                    waterMl = 2250L,
                    steps = 9200L,
                    workoutMinutes = 30L,
                    caloriesBurned = 310L,
                    meals = listOf(mapOf("id" to "m1", "name" to "Chicken Bowl", "mealType" to "Lunch", "calories" to 620L, "proteinGrams" to 48L))
                )
            )
        }
        assertTrue(logRes.isSuccess)

        val goalRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertGoal(
                PersonalGoal(
                    title = "40 Push-Ups Daily",
                    metricType = "pushups",
                    targetValue = 40L,
                    unitLabel = "reps",
                    startDateKey = "2026-11-01",
                    endDateKey = "2026-11-30",
                    active = true,
                    completedDates = listOf("2026-11-14")
                )
            )
        }
        assertTrue(goalRes.isSuccess)

        val measRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertMeasurement(BodyMeasurement(dateKey = "2026-11-14", weightKg = 74.2, waistCm = 81.0))
        }
        assertTrue(measRes.isSuccess)

        val routineRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveRoutine(CustomRoutine(name = "Home Push", exerciseIds = listOf("pushup", "dips")))
        }
        assertTrue(routineRes.isSuccess)

        val exRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveCustomExercise(CustomExercise(name = "Archer Push-Up", muscleGroup = "Chest", equipment = "Bodyweight", trackingKind = "bw", cues = listOf("Shift weight")))
        }
        assertTrue(exRes.isSuccess)

        val mealRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveCustomMeal(CustomMeal(name = "Protein Oats", mealType = "Breakfast", calories = 450L, proteinGrams = 32L))
        }
        assertTrue(mealRes.isSuccess)
    }

    @Test
    fun observeWorkouts_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        signInTestUser(BOB_EMAIL)
        val bobRepo = FitnessRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeWorkouts(aliceUid).first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val firestoreException = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException for cross-user access, got $caught", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun observeWorkouts_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repo = FitnessRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeWorkouts(aliceUid).first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val firestoreException = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException for unauthenticated access, got $caught", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_forge@test.com"
        const val BOB_EMAIL = "bob_forge@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
