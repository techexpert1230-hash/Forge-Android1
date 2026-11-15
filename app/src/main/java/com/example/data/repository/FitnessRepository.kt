package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.BodyMeasurement
import com.example.data.model.CustomExercise
import com.example.data.model.CustomMeal
import com.example.data.model.CustomRoutine
import com.example.data.model.DailyLog
import com.example.data.model.PersonalGoal
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FitnessRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        createConfiguredFirestore(context.applicationContext),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    fun observeUserProfile(uid: String): Flow<UserProfile?> =
        userDoc(uid).snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { e -> handleStreamError(e, OperationType.GET, "users/$uid", uid) }

    fun observeWorkouts(uid: String): Flow<List<WorkoutSession>> =
        observeCollection(uid, "workouts", WorkoutSession::class.java)

    fun observeDailyLogs(uid: String): Flow<List<DailyLog>> =
        observeCollection(uid, "daily_logs", DailyLog::class.java)

    fun observeGoals(uid: String): Flow<List<PersonalGoal>> =
        observeCollection(uid, "goals", PersonalGoal::class.java)

    fun observeMeasurements(uid: String): Flow<List<BodyMeasurement>> =
        observeCollection(uid, "measurements", BodyMeasurement::class.java)

    fun observeRoutines(uid: String): Flow<List<CustomRoutine>> =
        observeCollection(uid, "routines", CustomRoutine::class.java)

    fun observeCustomExercises(uid: String): Flow<List<CustomExercise>> =
        observeCollection(uid, "custom_exercises", CustomExercise::class.java)

    fun observeCustomMeals(uid: String): Flow<List<CustomMeal>> =
        observeCollection(uid, "custom_meals", CustomMeal::class.java)

    private fun <T> observeCollection(uid: String, subcollection: String, clazz: Class<T>): Flow<List<T>> = flow {
        val path = "users/$uid/$subcollection"
        emitAll(
            userDoc(uid).collection(subcollection)
                .snapshots()
                .map { it.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e -> handleStreamError(e, OperationType.LIST, path, uid) }
        )
    }

    private fun handleStreamError(error: Throwable, op: OperationType, path: String, uid: String): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path, auth)
        }
        throw error
    }

    suspend fun upsertUserProfile(profile: UserProfile): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = userDoc(uid)
        try {
            val existingCreatedAt = runCatching {
                ref.get().await().getTimestamp("createdAt")
            }.getOrNull()
            ref.set(profile.toWriteMap(uid, existingCreatedAt)).await()
            Unit
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path, auth)
            throw e
        }
    }

    suspend fun getUserProfile(): Result<UserProfile?> = runCatching {
        val uid = requireUserId()
        val ref = userDoc(uid)
        try {
            val snap = ref.get().await()
            if (snap.exists()) {
                snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, ref.path, auth)
            throw e
        }
    }

    suspend fun saveWorkout(workout: WorkoutSession): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = workout.id.ifBlank { "w-${UUID.randomUUID().toString().take(12)}" }
        val ref = userDoc(uid).collection("workouts").document(docId)
        try {
            val existingCreatedAt = if (workout.createdAt != null) {
                workout.createdAt
            } else {
                runCatching { ref.get().await().getTimestamp("createdAt") }.getOrNull()
            }
            ref.set(workout.toWriteMap(uid, existingCreatedAt)).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path, auth)
            throw e
        }
    }

    suspend fun deleteWorkout(workoutId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = userDoc(uid).collection("workouts").document(workoutId)
        try {
            ref.delete().await()
            Unit
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path, auth)
            throw e
        }
    }

    suspend fun upsertDailyLog(log: DailyLog): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(log.dateKey.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) { "Invalid dateKey" }
        val ref = userDoc(uid).collection("daily_logs").document(log.dateKey)
        try {
            val existingCreatedAt = log.createdAt ?: runCatching {
                ref.get().await().getTimestamp("createdAt")
            }.getOrNull()
            ref.set(log.toWriteMap(uid, existingCreatedAt)).await()
            Unit
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path, auth)
            throw e
        }
    }

    suspend fun getDailyLog(dateKey: String): Result<DailyLog?> = runCatching {
        val uid = requireUserId()
        val ref = userDoc(uid).collection("daily_logs").document(dateKey)
        try {
            val snap = ref.get().await()
            if (snap.exists()) {
                snap.toObject(DailyLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, ref.path, auth)
            throw e
        }
    }

    suspend fun upsertGoal(goal: PersonalGoal): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = goal.id.ifBlank { "g-${UUID.randomUUID().toString().take(12)}" }
        val ref = userDoc(uid).collection("goals").document(docId)
        try {
            val existingCreatedAt = goal.createdAt ?: runCatching {
                ref.get().await().getTimestamp("createdAt")
            }.getOrNull()
            ref.set(goal.toWriteMap(uid, existingCreatedAt)).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path, auth)
            throw e
        }
    }

    suspend fun deleteGoal(goalId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = userDoc(uid).collection("goals").document(goalId)
        try {
            ref.delete().await()
            Unit
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path, auth)
            throw e
        }
    }

    suspend fun upsertMeasurement(measurement: BodyMeasurement): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(measurement.dateKey.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) { "Invalid dateKey" }
        val ref = userDoc(uid).collection("measurements").document(measurement.dateKey)
        try {
            val existingCreatedAt = measurement.createdAt ?: runCatching {
                ref.get().await().getTimestamp("createdAt")
            }.getOrNull()
            ref.set(measurement.toWriteMap(uid, existingCreatedAt)).await()
            Unit
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path, auth)
            throw e
        }
    }

    suspend fun deleteMeasurement(dateKey: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = userDoc(uid).collection("measurements").document(dateKey)
        try {
            ref.delete().await()
            Unit
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path, auth)
            throw e
        }
    }

    suspend fun saveRoutine(routine: CustomRoutine): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = routine.id.ifBlank { "r-${UUID.randomUUID().toString().take(12)}" }
        val ref = userDoc(uid).collection("routines").document(docId)
        try {
            val existingCreatedAt = routine.createdAt ?: runCatching {
                ref.get().await().getTimestamp("createdAt")
            }.getOrNull()
            ref.set(routine.toWriteMap(uid, existingCreatedAt)).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path, auth)
            throw e
        }
    }

    suspend fun deleteRoutine(routineId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = userDoc(uid).collection("routines").document(routineId)
        try {
            ref.delete().await()
            Unit
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path, auth)
            throw e
        }
    }

    suspend fun saveCustomExercise(exercise: CustomExercise): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = exercise.id.ifBlank { "ce-${UUID.randomUUID().toString().take(12)}" }
        val ref = userDoc(uid).collection("custom_exercises").document(docId)
        try {
            val existingCreatedAt = exercise.createdAt ?: runCatching {
                ref.get().await().getTimestamp("createdAt")
            }.getOrNull()
            ref.set(exercise.toWriteMap(uid, existingCreatedAt)).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path, auth)
            throw e
        }
    }

    suspend fun deleteCustomExercise(exerciseId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = userDoc(uid).collection("custom_exercises").document(exerciseId)
        try {
            ref.delete().await()
            Unit
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path, auth)
            throw e
        }
    }

    suspend fun saveCustomMeal(meal: CustomMeal): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = meal.id.ifBlank { "cm-${UUID.randomUUID().toString().take(12)}" }
        val ref = userDoc(uid).collection("custom_meals").document(docId)
        try {
            val existingCreatedAt = meal.createdAt ?: runCatching {
                ref.get().await().getTimestamp("createdAt")
            }.getOrNull()
            ref.set(meal.toWriteMap(uid, existingCreatedAt)).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path, auth)
            throw e
        }
    }

    suspend fun deleteCustomMeal(mealId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = userDoc(uid).collection("custom_meals").document(mealId)
        try {
            ref.delete().await()
            Unit
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path, auth)
            throw e
        }
    }

    suspend fun eraseAllUserData(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val subcollections = listOf(
            "workouts", "daily_logs", "goals", "measurements",
            "routines", "custom_exercises", "custom_meals"
        )
        try {
            for (sub in subcollections) {
                val snaps = userDoc(uid).collection(sub).get().await()
                for (doc in snaps.documents) {
                    doc.reference.delete().await()
                }
            }
            userDoc(uid).delete().await()
            Unit
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid", auth)
            throw e
        }
    }

    companion object {
        fun createConfiguredFirestore(context: Context): FirebaseFirestore {
            val databaseId = context.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            runCatching {
                db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
            }
            return db
        }
    }
}
