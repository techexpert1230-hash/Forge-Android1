package com.example.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

data class AiCoachResponse(
    val text: String,
    val source: String, // "gemini" or "offline"
    val structuredJson: JSONObject? = null
)

class AiCoachRepository {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are Forge Coach, an encouraging, concise, evidence-based strength, cardio, hydration, and nutrition coach. " +
                        "Always personalize advice to the athlete's real logged stats provided in context. Keep responses clear, practical, and free of fluff."
                )
            }
        )
    }

    private val workoutPlanModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Workout or routine name"),
                "focus" to Schema.string(description = "Target muscles or fitness goal"),
                "estimatedMinutes" to Schema.integer(description = "Estimated duration in minutes"),
                "rationale" to Schema.string(description = "Short coaching note"),
                "exerciseIds" to Schema.array(
                    items = Schema.string(),
                    description = "List of matching exercise IDs from the provided availableIds list"
                ),
                "prescription" to Schema.array(
                    items = Schema.string(),
                    description = "Sets and reps guidance per exercise"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val dietPlanModel by lazy {
        val mealSchema = Schema.obj(
            properties = mapOf(
                "name" to Schema.string(description = "Meal name"),
                "mealType" to Schema.enumeration(listOf("Breakfast", "Lunch", "Dinner", "Snack")),
                "calories" to Schema.integer(description = "Calories kcal"),
                "proteinGrams" to Schema.integer(description = "Protein in grams"),
                "carbsGrams" to Schema.integer(description = "Carbs in grams"),
                "fatGrams" to Schema.integer(description = "Fat in grams"),
                "notes" to Schema.string(description = "Short prep or macro tip")
            )
        )
        val schema = Schema.obj(
            properties = mapOf(
                "summary" to Schema.string(description = "Short nutrition coaching overview"),
                "meals" to Schema.array(items = mealSchema, description = "3 to 4 practical meal suggestions")
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val analysisModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "headline" to Schema.string(description = "Concise progress headline"),
                "summary" to Schema.string(description = "2-3 sentence progress synthesis"),
                "strengths" to Schema.array(items = Schema.string(), description = "2-4 observed wins"),
                "gaps" to Schema.array(items = Schema.string(), description = "2-3 areas to improve"),
                "nextWeekTargets" to Schema.array(items = Schema.string(), description = "3 actionable next-week steps")
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    suspend fun askCoach(userMessage: String, contextJson: String): AiCoachResponse {
        val ctx = safeParseJson(contextJson)
        return try {
            val prompt = """
                Athlete Snapshot (real saved data):
                $contextJson

                Athlete Question:
                $userMessage
            """.trimIndent()
            val response = withTimeout(TIMEOUT_MS) {
                chatModel.generateContent(prompt)
            }
            val text = response.text?.trim()
            if (!text.isNullOrBlank()) {
                AiCoachResponse(text = text, source = "gemini")
            } else {
                offlineChatFallback(userMessage, ctx)
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                Log.w(TAG, "Gemini chat timed out, falling back to offline coach", e)
                offlineChatFallback(userMessage, ctx)
            } else {
                throw e
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini chat failed (${e.localizedMessage}), using offline coach fallback", e)
            offlineChatFallback(userMessage, ctx)
        }
    }

    suspend fun generateWorkoutPlan(
        goalFocus: String,
        equipment: String,
        availableExerciseIdsJson: String,
        contextJson: String
    ): AiCoachResponse {
        val ctx = safeParseJson(contextJson)
        return try {
            val prompt = """
                Create a personalized workout routine for this athlete.
                Focus: $goalFocus
                Equipment preference: $equipment
                Available exercise IDs (pick 4 to 6 strictly from this list): $availableExerciseIdsJson
                Athlete stats context: $contextJson
            """.trimIndent()
            val response = withTimeout(TIMEOUT_MS) {
                workoutPlanModel.generateContent(prompt)
            }
            val raw = response.text?.trim()
            if (!raw.isNullOrBlank()) {
                val parsed = JSONObject(raw)
                AiCoachResponse(text = parsed.optString("rationale"), source = "gemini", structuredJson = parsed)
            } else {
                offlineWorkoutFallback(goalFocus, equipment, availableExerciseIdsJson)
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                offlineWorkoutFallback(goalFocus, equipment, availableExerciseIdsJson)
            } else {
                throw e
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini workout plan failed, using offline fallback", e)
            offlineWorkoutFallback(goalFocus, equipment, availableExerciseIdsJson)
        }
    }

    suspend fun suggestDietPlan(
        preference: String,
        contextJson: String
    ): AiCoachResponse {
        val ctx = safeParseJson(contextJson)
        return try {
            val prompt = """
                Suggest practical, budget-friendly meals for this athlete based on their remaining daily calories & protein targets.
                Dietary preference: $preference
                Athlete stats context: $contextJson
            """.trimIndent()
            val response = withTimeout(TIMEOUT_MS) {
                dietPlanModel.generateContent(prompt)
            }
            val raw = response.text?.trim()
            if (!raw.isNullOrBlank()) {
                val parsed = JSONObject(raw)
                AiCoachResponse(text = parsed.optString("summary"), source = "gemini", structuredJson = parsed)
            } else {
                offlineDietFallback(preference, ctx)
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                offlineDietFallback(preference, ctx)
            } else {
                throw e
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini diet plan failed, using offline fallback", e)
            offlineDietFallback(preference, ctx)
        }
    }

    suspend fun analyzeProgress(contextJson: String): AiCoachResponse {
        val ctx = safeParseJson(contextJson)
        return try {
            val prompt = """
                Analyze this athlete's real fitness history (daily, weekly, monthly, all-time workouts, push-ups, squats, hydration, nutrition, steps, weight, and goal completion) and produce concrete insights.
                Data: $contextJson
            """.trimIndent()
            val response = withTimeout(TIMEOUT_MS) {
                analysisModel.generateContent(prompt)
            }
            val raw = response.text?.trim()
            if (!raw.isNullOrBlank()) {
                val parsed = JSONObject(raw)
                AiCoachResponse(text = parsed.optString("summary"), source = "gemini", structuredJson = parsed)
            } else {
                offlineAnalysisFallback(ctx)
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                offlineAnalysisFallback(ctx)
            } else {
                throw e
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini analysis failed, using offline fallback", e)
            offlineAnalysisFallback(ctx)
        }
    }

    private fun offlineChatFallback(message: String, ctx: JSONObject): AiCoachResponse {
        val lower = message.lowercase()
        val pushUpsToday = ctx.optInt("todayPushUps", 0)
        val pushUpsGoal = ctx.optInt("dailyPushUpsTarget", 40)
        val waterMl = ctx.optInt("todayWaterMl", 0)
        val waterGoal = ctx.optInt("dailyWaterMlTarget", 2500)
        val proteinToday = ctx.optInt("todayProtein", 0)
        val proteinGoal = ctx.optInt("dailyProteinTarget", 140)
        val workoutsWeek = ctx.optInt("weeklyWorkouts", 0)
        val weeklyGoal = ctx.optInt("weeklyWorkoutGoal", 4)

        val reply = when {
            "water" in lower || "hydrat" in lower -> {
                val remMl = (waterGoal - waterMl).coerceAtLeast(0)
                if (remMl == 0) {
                    "Offline Coach: You've hit your ${waterGoal}ml hydration target today! Keep sipping steadily around training."
                } else {
                    "Offline Coach: You're at ${waterMl}ml of ${waterGoal}ml today. Drink ${(remMl + 249) / 250} more glass(es) (~${remMl}ml) spaced out before bedtime."
                }
            }
            "protein" in lower || "diet" in lower || "meal" in lower || "eat" in lower -> {
                val remP = (proteinGoal - proteinToday).coerceAtLeast(0)
                "Offline Coach: Today you've logged ${proteinToday}g of your ${proteinGoal}g protein target. " +
                    if (remP > 0) "Add a ${minOf(40, remP)}g protein anchor like Greek yogurt, eggs, chicken breast, lentils, or a whey shake." else "Great work hitting your protein goal!"
            }
            "push" in lower || "squat" in lower || "workout" in lower || "plan" in lower -> {
                val remPush = (pushUpsGoal - pushUpsToday).coerceAtLeast(0)
                "Offline Coach: You've completed $workoutsWeek/$weeklyGoal workouts this week and $pushUpsToday/$pushUpsGoal push-ups today. " +
                    if (remPush > 0) "Knock out 2–3 submaximal sets to finish your remaining $remPush push-ups with crisp form." else "Push-up target crushed! Focus on compound lifts or recovery mobility next."
            }
            else -> {
                "Offline Coach: This week you have $workoutsWeek/$weeklyGoal workouts, $pushUpsToday/$pushUpsGoal push-ups today, " +
                    "${waterMl}ml/${waterGoal}ml water, and ${proteinToday}g/${proteinGoal}g protein. Stay consistent: prioritize your next scheduled session, hit your hydration glass target, and add protein to your next meal."
            }
        }
        return AiCoachResponse(text = reply, source = "offline")
    }

    private fun offlineWorkoutFallback(
        goalFocus: String,
        equipment: String,
        availableExerciseIdsJson: String
    ): AiCoachResponse {
        val lower = goalFocus.lowercase()
        val ids = when {
            "bodyweight" in equipment.lowercase() || "home" in lower ->
                listOf("pushup", "dips", "pullup", "hlr", "plank")
            "leg" in lower || "lower" in lower ->
                listOf("squat", "rdl", "leg-press", "leg-curl", "calf")
            "pull" in lower || "back" in lower ->
                listOf("deadlift", "pullup", "bb-row", "face-pull", "bb-curl")
            "cardio" in lower || "fat" in lower || "endurance" in lower ->
                listOf("run", "rope", "pushup", "plank", "twist")
            else ->
                listOf("bench", "incline-db", "ohp", "lat-raise", "pushdown")
        }
        val available = runCatching {
            val arr = JSONArray(availableExerciseIdsJson)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())

        val filtered = if (available.isNotEmpty()) ids.filter { it in available } else ids
        val json = JSONObject().apply {
            put("title", "Forge ${goalFocus.ifBlank { "Strength" }} Plan")
            put("focus", goalFocus.ifBlank { "Balanced Strength & Hypertrophy" })
            put("estimatedMinutes", 45)
            put("rationale", "Offline Coach built this balanced progressive-overload session tailored to your $equipment setup.")
            put("exerciseIds", JSONArray(filtered.ifEmpty { ids }))
            put("prescription", JSONArray(listOf(
                "3–4 working sets × 8–12 controlled reps",
                "Rest 90 seconds between compound sets",
                "Add 1 rep or 2.5kg when all sets feel smooth"
            )))
        }
        return AiCoachResponse(text = json.getString("rationale"), source = "offline", structuredJson = json)
    }

    private fun offlineDietFallback(preference: String, ctx: JSONObject): AiCoachResponse {
        val calGoal = ctx.optInt("dailyCaloriesTarget", 2200)
        val calToday = ctx.optInt("todayCalories", 0)
        val proGoal = ctx.optInt("dailyProteinTarget", 140)
        val proToday = ctx.optInt("todayProtein", 0)
        val remCal = (calGoal - calToday).coerceAtLeast(350)
        val remPro = (proGoal - proToday).coerceAtLeast(25)

        val isVeg = "veg" in preference.lowercase()
        val meals = JSONArray().apply {
            put(JSONObject().apply {
                put("name", if (isVeg) "Greek Yogurt Berry & Chia Bowl" else "Protein Oats with Egg Whites")
                put("mealType", "Breakfast")
                put("calories", minOf(480, remCal / 2 + 120))
                put("proteinGrams", 34)
                put("carbsGrams", 48)
                put("fatGrams", 12)
                put("notes", "Fast morning protein + slow-release carbs.")
            })
            put(JSONObject().apply {
                put("name", if (isVeg) "Paneer & Quinoa Power Bowl" else "Grilled Chicken Rice & Greens")
                put("mealType", "Lunch")
                put("calories", minOf(620, remCal / 2 + 180))
                put("proteinGrams", 46)
                put("carbsGrams", 58)
                put("fatGrams", 16)
                put("notes", "Balanced post-training recovery meal.")
            })
            put(JSONObject().apply {
                put("name", if (isVeg) "Roasted Chickpea & Whey Shake" else "Tuna Avocado Lettuce Wraps")
                put("mealType", "Snack")
                put("calories", 260)
                put("proteinGrams", 30)
                put("carbsGrams", 18)
                put("fatGrams", 7)
                put("notes", "Closes your remaining protein gap effortlessly.")
            })
        }
        val json = JSONObject().apply {
            put("summary", "Offline Coach: Remaining today ~${remCal} kcal and ~${remPro}g protein ($preference). Tap any suggestion to log it instantly.")
            put("meals", meals)
        }
        return AiCoachResponse(text = json.getString("summary"), source = "offline", structuredJson = json)
    }

    private fun offlineAnalysisFallback(ctx: JSONObject): AiCoachResponse {
        val weeklyWorkouts = ctx.optInt("weeklyWorkouts", 0)
        val weeklyGoal = ctx.optInt("weeklyWorkoutGoal", 4).coerceAtLeast(1)
        val allTimeWorkouts = ctx.optInt("allTimeWorkouts", 0)
        val totalPushUps = ctx.optInt("allTimePushUps", 0)
        val totalWaterLiters = ctx.optDouble("allTimeWaterLiters", 0.0)
        val goalPct = ctx.optInt("weeklyGoalCompletionPct", 0)

        val strengths = JSONArray().apply {
            put("$allTimeWorkouts lifetime workouts and $totalPushUps total push-ups logged.")
            put("Hydration history totals ${String.format("%.1f", totalWaterLiters)} L tracked.")
            if (weeklyWorkouts >= weeklyGoal) put("Weekly training target achieved ($weeklyWorkouts/$weeklyGoal)!")
        }
        val gaps = JSONArray().apply {
            if (weeklyWorkouts < weeklyGoal) put("You have ${weeklyGoal - weeklyWorkouts} workout(s) left to hit this week's target.")
            if (goalPct < 70) put("Daily target completion is at $goalPct% — try hitting water and push-up minimums earlier in the day.")
        }
        val nextSteps = JSONArray().apply {
            put("Schedule your next workout session right after waking or right after work.")
            put("Pre-fill a 500ml bottle to knock out 2 glasses of water before noon.")
            put("Aim for progressive overload on your primary compound lift next session.")
        }
        val json = JSONObject().apply {
            put("headline", "Offline Performance Snapshot")
            put("summary", "You have logged $weeklyWorkouts of $weeklyGoal workouts this week ($goalPct% daily goal completion) and $allTimeWorkouts total workouts overall.")
            put("strengths", strengths)
            put("gaps", gaps)
            put("nextWeekTargets", nextSteps)
        }
        return AiCoachResponse(text = json.getString("summary"), source = "offline", structuredJson = json)
    }

    private fun safeParseJson(raw: String): JSONObject =
        runCatching { JSONObject(raw) }.getOrDefault(JSONObject())

    private companion object {
        const val TAG = "AiCoachRepo"
        const val TIMEOUT_MS = 45_000L
    }
}
