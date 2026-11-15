package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val unit: String = "kg",
    val goal: Long = 4L,
    val rest: Long = 90L,
    val theme: String = "dark",
    val dailyPushUpsTarget: Long = 40L,
    val dailySquatsTarget: Long = 50L,
    val dailyWaterMlTarget: Long = 2500L,
    val glassSizeMl: Long = 250L,
    val dailyCaloriesTarget: Long = 2200L,
    val dailyProteinTarget: Long = 140L,
    val dailyStepsTarget: Long = 8000L,
    val dailyWorkoutMinutesTarget: Long = 45L,
    val workoutReminderEnabled: Boolean = true,
    val workoutReminderHour: Long = 18L,
    val workoutReminderMinute: Long = 0L,
    val waterReminderEnabled: Boolean = true,
    val waterReminderIntervalHours: Long = 2L,
    val healthConnectEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toWriteMap(uid: String, existingCreatedAt: Timestamp?): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.take(40),
        "unit" to if (unit == "lb") "lb" else "kg",
        "goal" to goal.coerceIn(1L, 7L),
        "rest" to rest.coerceIn(0L, 600L),
        "theme" to if (theme == "light") "light" else "dark",
        "dailyPushUpsTarget" to dailyPushUpsTarget.coerceIn(0L, 10000L),
        "dailySquatsTarget" to dailySquatsTarget.coerceIn(0L, 10000L),
        "dailyWaterMlTarget" to dailyWaterMlTarget.coerceIn(0L, 20000L),
        "glassSizeMl" to glassSizeMl.coerceIn(50L, 1000L),
        "dailyCaloriesTarget" to dailyCaloriesTarget.coerceIn(0L, 15000L),
        "dailyProteinTarget" to dailyProteinTarget.coerceIn(0L, 1000L),
        "dailyStepsTarget" to dailyStepsTarget.coerceIn(0L, 100000L),
        "dailyWorkoutMinutesTarget" to dailyWorkoutMinutesTarget.coerceIn(0L, 600L),
        "workoutReminderEnabled" to workoutReminderEnabled,
        "workoutReminderHour" to workoutReminderHour.coerceIn(0L, 23L),
        "workoutReminderMinute" to workoutReminderMinute.coerceIn(0L, 59L),
        "waterReminderEnabled" to waterReminderEnabled,
        "waterReminderIntervalHours" to waterReminderIntervalHours.coerceIn(1L, 12L),
        "healthConnectEnabled" to healthConnectEnabled,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("unit", unit)
        put("goal", goal)
        put("rest", rest)
        put("theme", theme)
        put("dailyPushUpsTarget", dailyPushUpsTarget)
        put("dailySquatsTarget", dailySquatsTarget)
        put("dailyWaterMlTarget", dailyWaterMlTarget)
        put("glassSizeMl", glassSizeMl)
        put("dailyCaloriesTarget", dailyCaloriesTarget)
        put("dailyProteinTarget", dailyProteinTarget)
        put("dailyStepsTarget", dailyStepsTarget)
        put("dailyWorkoutMinutesTarget", dailyWorkoutMinutesTarget)
        put("workoutReminderEnabled", workoutReminderEnabled)
        put("workoutReminderHour", workoutReminderHour)
        put("workoutReminderMinute", workoutReminderMinute)
        put("waterReminderEnabled", waterReminderEnabled)
        put("waterReminderIntervalHours", waterReminderIntervalHours)
        put("healthConnectEnabled", healthConnectEnabled)
    }
}

@IgnoreExtraProperties
data class WorkoutSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "Workout",
    val dateKey: String = todayDateKey(),
    val startMs: Long = System.currentTimeMillis(),
    val endMs: Long = System.currentTimeMillis(),
    val durSec: Long = 60L,
    val volKg: Double = 0.0,
    val setsCount: Long = 0L,
    val pushUpsCount: Long = 0L,
    val squatsCount: Long = 0L,
    val caloriesBurned: Long = 0L,
    val exercises: List<Map<String, Any>> = emptyList(),
    val prs: List<Map<String, Any>> = emptyList(),
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toWriteMap(uid: String, existingCreatedAt: Timestamp?): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.ifBlank { "Workout" }.take(80),
        "dateKey" to dateKey,
        "startMs" to startMs,
        "endMs" to maxOf(startMs, endMs),
        "durSec" to durSec.coerceIn(0L, 86400L),
        "volKg" to volKg.coerceIn(0.0, 10_000_000.0),
        "setsCount" to setsCount.coerceIn(0L, 500L),
        "pushUpsCount" to pushUpsCount.coerceIn(0L, 50000L),
        "squatsCount" to squatsCount.coerceIn(0L, 50000L),
        "caloriesBurned" to caloriesBurned.coerceIn(0L, 20000L),
        "exercises" to exercises.take(30),
        "prs" to prs.take(30),
        "notes" to notes.take(500),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("dateKey", dateKey)
        put("start", startMs)
        put("end", endMs)
        put("dur", durSec)
        put("vol", volKg)
        put("sets", setsCount)
        put("pushUpsCount", pushUpsCount)
        put("squatsCount", squatsCount)
        put("caloriesBurned", caloriesBurned)
        put("ex", JSONArray(exercises.map { mapToJsonObject(it) }))
        put("prs", JSONArray(prs.map { mapToJsonObject(it) }))
        put("notes", notes)
    }
}

@IgnoreExtraProperties
data class DailyLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = todayDateKey(),
    val pushUps: Long = 0L,
    val squats: Long = 0L,
    val waterMl: Long = 0L,
    val steps: Long = 0L,
    val healthConnectSteps: Long = 0L,
    val workoutMinutes: Long = 0L,
    val caloriesBurned: Long = 0L,
    val otherActivities: List<Map<String, Any>> = emptyList(),
    val meals: List<Map<String, Any>> = emptyList(),
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toWriteMap(uid: String, existingCreatedAt: Timestamp?): Map<String, Any> = mapOf(
        "userId" to uid,
        "dateKey" to dateKey,
        "pushUps" to pushUps.coerceIn(0L, 100000L),
        "squats" to squats.coerceIn(0L, 100000L),
        "waterMl" to waterMl.coerceIn(0L, 50000L),
        "steps" to steps.coerceIn(0L, 250000L),
        "healthConnectSteps" to healthConnectSteps.coerceIn(0L, 250000L),
        "workoutMinutes" to workoutMinutes.coerceIn(0L, 1440L),
        "caloriesBurned" to caloriesBurned.coerceIn(0L, 20000L),
        "otherActivities" to otherActivities.take(50),
        "meals" to meals.take(50),
        "notes" to notes.take(500),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("dateKey", dateKey)
        put("pushUps", pushUps)
        put("squats", squats)
        put("waterMl", waterMl)
        put("steps", steps)
        put("healthConnectSteps", healthConnectSteps)
        put("workoutMinutes", workoutMinutes)
        put("caloriesBurned", caloriesBurned)
        put("otherActivities", JSONArray(otherActivities.map { mapToJsonObject(it) }))
        put("meals", JSONArray(meals.map { mapToJsonObject(it) }))
        put("notes", notes)
    }
}

@IgnoreExtraProperties
data class PersonalGoal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val metricType: String = "pushups",
    val targetValue: Long = 40L,
    val unitLabel: String = "reps",
    val startDateKey: String = todayDateKey(),
    val endDateKey: String = todayDateKey(),
    val active: Boolean = true,
    val completedDates: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toWriteMap(uid: String, existingCreatedAt: Timestamp?): Map<String, Any> = mapOf(
        "userId" to uid,
        "title" to title.ifBlank { "Daily Goal" }.take(100),
        "metricType" to metricType,
        "targetValue" to targetValue.coerceIn(1L, 1000000L),
        "unitLabel" to unitLabel.take(24),
        "startDateKey" to startDateKey,
        "endDateKey" to endDateKey,
        "active" to active,
        "completedDates" to completedDates.distinct().take(366),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("metricType", metricType)
        put("targetValue", targetValue)
        put("unitLabel", unitLabel)
        put("startDateKey", startDateKey)
        put("endDateKey", endDateKey)
        put("active", active)
        put("completedDates", JSONArray(completedDates))
    }
}

@IgnoreExtraProperties
data class BodyMeasurement(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = todayDateKey(),
    val weightKg: Double = 70.0,
    val bodyFatPct: Double? = null,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val armCm: Double? = null,
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toWriteMap(uid: String, existingCreatedAt: Timestamp?): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "userId" to uid,
            "dateKey" to dateKey,
            "weightKg" to weightKg.coerceIn(10.0, 700.0),
            "note" to note.take(300),
            "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        bodyFatPct?.let { map["bodyFatPct"] = it.coerceIn(1.0, 80.0) }
        waistCm?.let { map["waistCm"] = it.coerceIn(20.0, 300.0) }
        chestCm?.let { map["chestCm"] = it.coerceIn(20.0, 300.0) }
        armCm?.let { map["armCm"] = it.coerceIn(10.0, 150.0) }
        return map
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("d", dateKey)
        put("kg", weightKg)
        bodyFatPct?.let { put("bodyFatPct", it) }
        waistCm?.let { put("waistCm", it) }
        chestCm?.let { put("chestCm", it) }
        armCm?.let { put("armCm", it) }
        put("note", note)
    }
}

@IgnoreExtraProperties
data class CustomRoutine(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val exerciseIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toWriteMap(uid: String, existingCreatedAt: Timestamp?): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.ifBlank { "Routine" }.take(60),
        "exerciseIds" to exerciseIds.take(30),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("ex", JSONArray(exerciseIds))
    }
}

@IgnoreExtraProperties
data class CustomExercise(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val muscleGroup: String = "Chest",
    val equipment: String = "Bodyweight",
    val trackingKind: String = "bw",
    val cues: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toWriteMap(uid: String, existingCreatedAt: Timestamp?): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.ifBlank { "Custom Exercise" }.take(80),
        "muscleGroup" to muscleGroup.take(30),
        "equipment" to equipment.take(40),
        "trackingKind" to if (trackingKind in listOf("weight", "bw", "hold", "cardio")) trackingKind else "bw",
        "cues" to cues.take(10),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("n", name)
        put("m", muscleGroup)
        put("eq", equipment)
        put("k", trackingKind)
        put("c", JSONArray(cues))
        put("custom", true)
    }
}

@IgnoreExtraProperties
data class CustomMeal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val mealType: String = "Lunch",
    val calories: Long = 0L,
    val proteinGrams: Long = 0L,
    val carbsGrams: Long = 0L,
    val fatGrams: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toWriteMap(uid: String, existingCreatedAt: Timestamp?): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.ifBlank { "Meal" }.take(80),
        "mealType" to if (mealType in listOf("Breakfast", "Lunch", "Dinner", "Snack")) mealType else "Lunch",
        "calories" to calories.coerceIn(0L, 10000L),
        "proteinGrams" to proteinGrams.coerceIn(0L, 1000L),
        "carbsGrams" to carbsGrams.coerceIn(0L, 1000L),
        "fatGrams" to fatGrams.coerceIn(0L, 1000L),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("mealType", mealType)
        put("calories", calories)
        put("proteinGrams", proteinGrams)
        put("carbsGrams", carbsGrams)
        put("fatGrams", fatGrams)
    }
}

fun todayDateKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
    val obj = JSONObject()
    map.forEach { (k, v) ->
        when (v) {
            null -> obj.put(k, JSONObject.NULL)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                obj.put(k, mapToJsonObject(v as Map<String, Any?>))
            }
            is List<*> -> obj.put(k, listToJsonArray(v))
            else -> obj.put(k, v)
        }
    }
    return obj
}

fun listToJsonArray(list: List<*>): JSONArray {
    val arr = JSONArray()
    list.forEach { item ->
        when (item) {
            null -> arr.put(JSONObject.NULL)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                arr.put(mapToJsonObject(item as Map<String, Any?>))
            }
            is List<*> -> arr.put(listToJsonArray(item))
            else -> arr.put(item)
        }
    }
    return arr
}
