package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiCoachRepository
import com.example.data.model.BodyMeasurement
import com.example.data.model.CustomExercise
import com.example.data.model.CustomMeal
import com.example.data.model.CustomRoutine
import com.example.data.model.DailyLog
import com.example.data.model.PersonalGoal
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.data.model.todayDateKey
import com.example.data.repository.FitnessRepository
import com.example.health.HealthConnectAvailability
import com.example.health.HealthConnectManager
import com.example.notifications.ReminderChannels
import com.example.notifications.ReminderScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

sealed interface WebEvent {
    data class Toast(val message: String) : WebEvent
    data class AiResult(val kind: String, val payloadJson: String) : WebEvent
    data class SyncStatus(val status: String, val detail: String = "") : WebEvent
}

class ForgeViewModel(
    private val repository: FitnessRepository,
    private val aiCoach: AiCoachRepository,
    private val healthConnect: HealthConnectManager,
    private val reminderScheduler: ReminderScheduler,
    private val currentUserId: String,
    private val userEmail: String?,
    private val displayName: String?
) : ViewModel() {

    private val _events = MutableSharedFlow<WebEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<WebEvent> = _events.asSharedFlow()

    private val _healthStatus = MutableStateFlow(buildHealthStatusJson(false))
    val healthStatus: StateFlow<String> = _healthStatus.asStateFlow()

    private val profileFlow = repository.observeUserProfile(currentUserId)
        .catch { emit(null) }
    private val workoutsFlow = repository.observeWorkouts(currentUserId)
        .catch { emit(emptyList()) }
    private val dailyLogsFlow = repository.observeDailyLogs(currentUserId)
        .catch { emit(emptyList()) }
    private val goalsFlow = repository.observeGoals(currentUserId)
        .catch { emit(emptyList()) }
    private val measurementsFlow = repository.observeMeasurements(currentUserId)
        .catch { emit(emptyList()) }
    private val routinesFlow = repository.observeRoutines(currentUserId)
        .catch { emit(emptyList()) }
    private val customExercisesFlow = repository.observeCustomExercises(currentUserId)
        .catch { emit(emptyList()) }
    private val customMealsFlow = repository.observeCustomMeals(currentUserId)
        .catch { emit(emptyList()) }

    val stateJsonFlow: StateFlow<String> = combine(
        combine(profileFlow, workoutsFlow, dailyLogsFlow, goalsFlow) { p, w, d, g ->
            Quad(p, w, d, g)
        },
        combine(measurementsFlow, routinesFlow, customExercisesFlow, customMealsFlow) { m, r, ce, cm ->
            Quad(m, r, ce, cm)
        },
        _healthStatus
    ) { first, second, hcJson ->
        val profile = first.a ?: UserProfile(
            userId = currentUserId,
            name = displayName.orEmpty().split(" ").firstOrNull().orEmpty()
        )
        val root = JSONObject().apply {
            put("uid", currentUserId)
            put("email", userEmail.orEmpty())
            put("profile", profile.toJson())
            put("workouts", JSONArray(first.b.sortedByDescending { it.startMs }.map { it.toJson() }))
            put("dailyLogs", JSONArray(first.c.sortedBy { it.dateKey }.map { it.toJson() }))
            put("goals", JSONArray(first.d.sortedByDescending { it.startDateKey }.map { it.toJson() }))
            put("measurements", JSONArray(second.a.sortedBy { it.dateKey }.map { it.toJson() }))
            put("routines", JSONArray(second.b.map { it.toJson() }))
            put("customExercises", JSONArray(second.c.map { it.toJson() }))
            put("customMeals", JSONArray(second.d.map { it.toJson() }))
            put("healthConnect", JSONObject(hcJson))
            put("todayKey", todayDateKey())
        }
        root.toString()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ""
    )

    init {
        refreshHealthConnectAndSync()
    }

    fun refreshHealthConnectAndSync() {
        viewModelScope.launch {
            val hasPerm = healthConnect.hasAllPermissions()
            _healthStatus.value = buildHealthStatusJson(hasPerm)
            if (hasPerm) {
                syncStepsFromHealthConnect(silent = true)
            }
        }
    }

    fun syncStepsFromHealthConnect(silent: Boolean = false) {
        viewModelScope.launch {
            val stepsMap = healthConnect.readStepsByDate(7)
            if (stepsMap.isEmpty()) {
                if (!silent) _events.tryEmit(WebEvent.Toast("No recent Health Connect step data found"))
                return@launch
            }
            var updatedDays = 0
            for ((dateKey, hcSteps) in stepsMap) {
                if (hcSteps > 0L) {
                    val existing = repository.getDailyLog(dateKey).getOrNull() ?: DailyLog(dateKey = dateKey)
                    val mergedSteps = maxOf(existing.steps, hcSteps)
                    if (mergedSteps != existing.steps || hcSteps != existing.healthConnectSteps) {
                        repository.upsertDailyLog(
                            existing.copy(
                                steps = mergedSteps,
                                healthConnectSteps = hcSteps
                            )
                        )
                        updatedDays++
                    }
                }
            }
            if (!silent) {
                _events.tryEmit(
                    WebEvent.Toast(
                        if (updatedDays > 0) "Synced Health Connect steps for $updatedDays day(s)"
                        else "Health Connect steps already up to date"
                    )
                )
            }
        }
    }

    fun saveProfile(jsonStr: String) {
        viewModelScope.launch {
            runCatching {
                val j = JSONObject(jsonStr)
                val profile = UserProfile(
                    userId = currentUserId,
                    name = j.optString("name", ""),
                    unit = j.optString("unit", "kg"),
                    goal = j.optLong("goal", 4L),
                    rest = j.optLong("rest", 90L),
                    theme = j.optString("theme", "dark"),
                    dailyPushUpsTarget = j.optLong("dailyPushUpsTarget", 40L),
                    dailySquatsTarget = j.optLong("dailySquatsTarget", 50L),
                    dailyWaterMlTarget = j.optLong("dailyWaterMlTarget", 2500L),
                    glassSizeMl = j.optLong("glassSizeMl", 250L),
                    dailyCaloriesTarget = j.optLong("dailyCaloriesTarget", 2200L),
                    dailyProteinTarget = j.optLong("dailyProteinTarget", 140L),
                    dailyStepsTarget = j.optLong("dailyStepsTarget", 8000L),
                    dailyWorkoutMinutesTarget = j.optLong("dailyWorkoutMinutesTarget", 45L),
                    workoutReminderEnabled = j.optBoolean("workoutReminderEnabled", true),
                    workoutReminderHour = j.optLong("workoutReminderHour", 18L),
                    workoutReminderMinute = j.optLong("workoutReminderMinute", 0L),
                    waterReminderEnabled = j.optBoolean("waterReminderEnabled", true),
                    waterReminderIntervalHours = j.optLong("waterReminderIntervalHours", 2L),
                    healthConnectEnabled = j.optBoolean("healthConnectEnabled", false)
                )
                reminderScheduler.syncSchedules(
                    workoutEnabled = profile.workoutReminderEnabled,
                    workoutHour = profile.workoutReminderHour.toInt(),
                    workoutMinute = profile.workoutReminderMinute.toInt(),
                    waterEnabled = profile.waterReminderEnabled,
                    waterIntervalHours = profile.waterReminderIntervalHours.toInt()
                )
                repository.upsertUserProfile(profile).getOrThrow()
            }.onFailure { e ->
                Log.e(TAG, "saveProfile failed", e)
                _events.tryEmit(WebEvent.Toast(e.localizedMessage ?: "Failed to save profile"))
            }
        }
    }

    fun saveWorkout(jsonStr: String) {
        viewModelScope.launch {
            runCatching {
                val j = JSONObject(jsonStr)
                val exercises = jsonArrayToListOfMaps(j.optJSONArray("ex"))
                val prs = jsonArrayToListOfMaps(j.optJSONArray("prs"))
                val dateKey = j.optString("dateKey", todayDateKey())
                val startMs = j.optLong("start", System.currentTimeMillis())
                val endMs = j.optLong("end", startMs + 60_000L)
                val durSec = j.optLong("dur", 60L)
                val calories = j.optLong("caloriesBurned", maxOf(10L, (durSec / 60L) * 6L))

                var pushUps = j.optLong("pushUpsCount", 0L)
                var squats = j.optLong("squatsCount", 0L)
                if (pushUps == 0L && squats == 0L) {
                    exercises.forEach { ex ->
                        val exId = (ex["id"] as? String).orEmpty().lowercase()
                        val exName = (ex["name"] as? String).orEmpty().lowercase()
                        @Suppress("UNCHECKED_CAST")
                        val sets = (ex["sets"] as? List<Map<String, Any>>) ?: emptyList()
                        val totalReps = sets.sumOf { (it["r"] as? Number)?.toLong() ?: 0L }
                        if ("pushup" in exId || "push-up" in exName || "push up" in exName) pushUps += totalReps
                        if ("squat" in exId || "squat" in exName) squats += totalReps
                    }
                }

                val session = WorkoutSession(
                    id = j.optString("id", ""),
                    userId = currentUserId,
                    name = j.optString("name", "Workout"),
                    dateKey = dateKey,
                    startMs = startMs,
                    endMs = endMs,
                    durSec = durSec,
                    volKg = j.optDouble("vol", 0.0),
                    setsCount = j.optLong("sets", 0L),
                    pushUpsCount = pushUps,
                    squatsCount = squats,
                    caloriesBurned = calories,
                    exercises = exercises,
                    prs = prs,
                    notes = j.optString("notes", "")
                )
                repository.saveWorkout(session).getOrThrow()

                // Roll workout duration, push-ups, squats, and calories burned into that day's DailyLog
                val existingLog = repository.getDailyLog(dateKey).getOrNull() ?: DailyLog(dateKey = dateKey)
                val addedMinutes = maxOf(1L, durSec / 60L)
                repository.upsertDailyLog(
                    existingLog.copy(
                        pushUps = existingLog.pushUps + pushUps,
                        squats = existingLog.squats + squats,
                        workoutMinutes = existingLog.workoutMinutes + addedMinutes,
                        caloriesBurned = existingLog.caloriesBurned + calories
                    )
                )

                // Sync workout to Health Connect if permissions granted
                healthConnect.writeWorkoutToHealthConnect(
                    title = session.name,
                    startMs = session.startMs,
                    endMs = session.endMs,
                    caloriesBurned = session.caloriesBurned
                )
            }.onFailure { e ->
                Log.e(TAG, "saveWorkout failed", e)
                _events.tryEmit(WebEvent.Toast(e.localizedMessage ?: "Failed to save workout"))
            }
        }
    }

    fun deleteWorkout(workoutId: String) {
        viewModelScope.launch {
            repository.deleteWorkout(workoutId).onFailure { e ->
                _events.tryEmit(WebEvent.Toast(e.localizedMessage ?: "Failed to delete workout"))
            }
        }
    }

    fun saveDailyLog(jsonStr: String) {
        viewModelScope.launch {
            runCatching {
                val j = JSONObject(jsonStr)
                val dateKey = j.optString("dateKey", todayDateKey())
                val log = DailyLog(
                    dateKey = dateKey,
                    pushUps = j.optLong("pushUps", 0L),
                    squats = j.optLong("squats", 0L),
                    waterMl = j.optLong("waterMl", 0L),
                    steps = j.optLong("steps", 0L),
                    healthConnectSteps = j.optLong("healthConnectSteps", 0L),
                    workoutMinutes = j.optLong("workoutMinutes", 0L),
                    caloriesBurned = j.optLong("caloriesBurned", 0L),
                    otherActivities = jsonArrayToListOfMaps(j.optJSONArray("otherActivities")),
                    meals = jsonArrayToListOfMaps(j.optJSONArray("meals")),
                    notes = j.optString("notes", "")
                )
                repository.upsertDailyLog(log).getOrThrow()
            }.onFailure { e ->
                Log.e(TAG, "saveDailyLog failed", e)
                _events.tryEmit(WebEvent.Toast(e.localizedMessage ?: "Failed to save daily log"))
            }
        }
    }

    fun saveGoal(jsonStr: String) {
        viewModelScope.launch {
            runCatching {
                val j = JSONObject(jsonStr)
                val datesArr = j.optJSONArray("completedDates")
                val dates = mutableListOf<String>()
                if (datesArr != null) {
                    for (i in 0 until datesArr.length()) {
                        dates.add(datesArr.getString(i))
                    }
                }
                val goal = PersonalGoal(
                    id = j.optString("id", ""),
                    userId = currentUserId,
                    title = j.optString("title", "Daily Goal"),
                    metricType = j.optString("metricType", "pushups"),
                    targetValue = j.optLong("targetValue", 40L),
                    unitLabel = j.optString("unitLabel", "reps"),
                    startDateKey = j.optString("startDateKey", todayDateKey()),
                    endDateKey = j.optString("endDateKey", todayDateKey()),
                    active = j.optBoolean("active", true),
                    completedDates = dates
                )
                repository.upsertGoal(goal).getOrThrow()
            }.onFailure { e ->
                _events.tryEmit(WebEvent.Toast(e.localizedMessage ?: "Failed to save goal"))
            }
        }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch {
            repository.deleteGoal(goalId).onFailure { e ->
                _events.tryEmit(WebEvent.Toast(e.localizedMessage ?: "Failed to delete goal"))
            }
        }
    }

    fun saveMeasurement(jsonStr: String) {
        viewModelScope.launch {
            runCatching {
                val j = JSONObject(jsonStr)
                val dateKey = j.optString("d", todayDateKey())
                val measurement = BodyMeasurement(
                    dateKey = dateKey,
                    userId = currentUserId,
                    weightKg = j.optDouble("kg", 70.0),
                    bodyFatPct = if (j.has("bodyFatPct") && !j.isNull("bodyFatPct")) j.optDouble("bodyFatPct") else null,
                    waistCm = if (j.has("waistCm") && !j.isNull("waistCm")) j.optDouble("waistCm") else null,
                    chestCm = if (j.has("chestCm") && !j.isNull("chestCm")) j.optDouble("chestCm") else null,
                    armCm = if (j.has("armCm") && !j.isNull("armCm")) j.optDouble("armCm") else null,
                    note = j.optString("note", "")
                )
                repository.upsertMeasurement(measurement).getOrThrow()
            }.onFailure { e ->
                _events.tryEmit(WebEvent.Toast(e.localizedMessage ?: "Failed to save measurement"))
            }
        }
    }

    fun deleteMeasurement(dateKey: String) {
        viewModelScope.launch {
            repository.deleteMeasurement(dateKey).onFailure { e ->
                _events.tryEmit(WebEvent.Toast(e.localizedMessage ?: "Failed to remove measurement"))
            }
        }
    }

    fun saveRoutine(jsonStr: String) {
        viewModelScope.launch {
            runCatching {
                val j = JSONObject(jsonStr)
                val arr = j.optJSONArray("ex") ?: JSONArray()
                val exIds = (0 until arr.length()).map { arr.getString(it) }
                val routine = CustomRoutine(
                    id = j.optString("id", ""),
                    userId = currentUserId,
                    name = j.optString("name", "Routine"),
                    exerciseIds = exIds
                )
                repository.saveRoutine(routine).getOrThrow()
            }.onFailure { e ->
                _events.tryEmit(WebEvent.Toast(e.localizedMessage ?: "Failed to save routine"))
            }
        }
    }

    fun deleteRoutine(routineId: String) {
        viewModelScope.launch {
            repository.deleteRoutine(routineId).onFailure { e ->
                _events.tryEmit(WebEvent.Toast(e.localizedMessage ?: "Failed to delete routine"))
            }
        }
    }

    fun saveCustomExercise(jsonStr: String) {
        viewModelScope.launch {
            runCatching {
                val j = JSONObject(jsonStr)
                val cuesArr = j.optJSONArray("c") ?: JSONArray()
                val cues = (0 until cuesArr.length()).map { cuesArr.getString(it) }
                val ex = CustomExercise(
                    id = j.optString("id", ""),
                    userId = currentUserId,
                    name = j.optString("n", "Exercise"),
                    muscleGroup = j.optString("m", "Chest"),
                    equipment = j.optString("eq", "Bodyweight"),
                    trackingKind = j.optString("k", "bw"),
                    cues = cues
                )
                repository.saveCustomExercise(ex).getOrThrow()
            }.onFailure { e ->
                _events.tryEmit(WebEvent.Toast(e.localizedMessage ?: "Failed to save custom exercise"))
            }
        }
    }

    fun saveCustomMeal(jsonStr: String) {
        viewModelScope.launch {
            runCatching {
                val j = JSONObject(jsonStr)
                val meal = CustomMeal(
                    id = j.optString("id", ""),
                    userId = currentUserId,
                    name = j.optString("name", "Meal"),
                    mealType = j.optString("mealType", "Lunch"),
                    calories = j.optLong("calories", 0L),
                    proteinGrams = j.optLong("proteinGrams", 0L),
                    carbsGrams = j.optLong("carbsGrams", 0L),
                    fatGrams = j.optLong("fatGrams", 0L)
                )
                repository.saveCustomMeal(meal).getOrThrow()
            }.onFailure { e ->
                _events.tryEmit(WebEvent.Toast(e.localizedMessage ?: "Failed to save custom meal"))
            }
        }
    }

    fun eraseAllData() {
        viewModelScope.launch {
            repository.eraseAllUserData()
                .onSuccess { _events.tryEmit(WebEvent.Toast("All cloud and local data erased")) }
                .onFailure { e -> _events.tryEmit(WebEvent.Toast(e.localizedMessage ?: "Erase failed")) }
        }
    }

    fun askAiCoach(requestId: String, message: String, contextJson: String) {
        viewModelScope.launch {
            val res = aiCoach.askCoach(message, contextJson)
            val payload = JSONObject().apply {
                put("requestId", requestId)
                put("source", res.source)
                put("text", res.text)
            }.toString()
            _events.tryEmit(WebEvent.AiResult("chat", payload))
        }
    }

    fun generateAiWorkout(
        requestId: String,
        goalFocus: String,
        equipment: String,
        availableIdsJson: String,
        contextJson: String
    ) {
        viewModelScope.launch {
            val res = aiCoach.generateWorkoutPlan(goalFocus, equipment, availableIdsJson, contextJson)
            val payload = JSONObject().apply {
                put("requestId", requestId)
                put("source", res.source)
                put("plan", res.structuredJson ?: JSONObject())
            }.toString()
            _events.tryEmit(WebEvent.AiResult("workout", payload))
        }
    }

    fun suggestAiDiet(requestId: String, preference: String, contextJson: String) {
        viewModelScope.launch {
            val res = aiCoach.suggestDietPlan(preference, contextJson)
            val payload = JSONObject().apply {
                put("requestId", requestId)
                put("source", res.source)
                put("diet", res.structuredJson ?: JSONObject())
            }.toString()
            _events.tryEmit(WebEvent.AiResult("diet", payload))
        }
    }

    fun analyzeAiProgress(requestId: String, contextJson: String) {
        viewModelScope.launch {
            val res = aiCoach.analyzeProgress(contextJson)
            val payload = JSONObject().apply {
                put("requestId", requestId)
                put("source", res.source)
                put("analysis", res.structuredJson ?: JSONObject())
            }.toString()
            _events.tryEmit(WebEvent.AiResult("analysis", payload))
        }
    }

    fun sendTestReminderNotification() {
        reminderScheduler.sendTestNotification()
    }

    private fun buildHealthStatusJson(hasPermissions: Boolean): String {
        val avail = healthConnect.getAvailability()
        return JSONObject().apply {
            put("availability", avail.name)
            put("hasPermissions", hasPermissions)
            put("available", avail == HealthConnectAvailability.AVAILABLE)
        }.toString()
    }

    private fun jsonArrayToListOfMaps(arr: JSONArray?): List<Map<String, Any>> {
        if (arr == null) return emptyList()
        val list = mutableListOf<Map<String, Any>>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(jsonObjectToMap(obj))
        }
        return list
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.get(key)
            if (value == JSONObject.NULL) continue
            map[key] = when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                is Int -> value.toLong()
                else -> value
            }
        }
        return map
    }

    private fun jsonArrayToList(arr: JSONArray): List<Any> {
        val out = mutableListOf<Any>()
        for (i in 0 until arr.length()) {
            val v = arr.get(i)
            if (v == JSONObject.NULL) continue
            out.add(
                when (v) {
                    is JSONObject -> jsonObjectToMap(v)
                    is JSONArray -> jsonArrayToList(v)
                    is Int -> v.toLong()
                    else -> v
                }
            )
        }
        return out
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private companion object {
        const val TAG = "ForgeVM"
    }
}
