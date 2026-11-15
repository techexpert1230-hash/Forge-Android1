package com.example.health

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

enum class HealthConnectAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE
}

class HealthConnectManager(private val context: Context) {

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
    )

    fun getAvailability(): HealthConnectAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return HealthConnectAvailability.UNAVAILABLE
        }
        return try {
            when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
                HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
                else -> HealthConnectAvailability.UNAVAILABLE
            }
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect availability check failed", e)
            HealthConnectAvailability.UNAVAILABLE
        }
    }

    private fun clientOrNull(): HealthConnectClient? {
        if (getAvailability() != HealthConnectAvailability.AVAILABLE) return null
        return runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    suspend fun hasAllPermissions(): Boolean {
        val client = clientOrNull() ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(requiredPermissions)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query Health Connect permissions", e)
            false
        }
    }

    suspend fun readStepsByDate(days: Int = 7): Map<String, Long> {
        val client = clientOrNull() ?: return emptyMap()
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.contains(HealthPermission.getReadPermission(StepsRecord::class))) {
                return emptyMap()
            }
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val result = mutableMapOf<String, Long>()
            for (offset in 0 until days) {
                val date = today.minusDays(offset.toLong())
                val start = date.atStartOfDay(zone).toInstant()
                val end = if (offset == 0) Instant.now() else date.plusDays(1).atStartOfDay(zone).toInstant()
                if (end.isAfter(start)) {
                    val agg = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(start, end)
                        )
                    )
                    val steps = agg[StepsRecord.COUNT_TOTAL] ?: 0L
                    result[date.toString()] = steps
                }
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading steps from Health Connect", e)
            emptyMap()
        }
    }

    suspend fun writeWorkoutToHealthConnect(
        title: String,
        startMs: Long,
        endMs: Long,
        caloriesBurned: Long
    ): Boolean {
        val client = clientOrNull() ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.contains(HealthPermission.getWritePermission(ExerciseSessionRecord::class))) {
                return false
            }
            val start = Instant.ofEpochMilli(startMs)
            val end = Instant.ofEpochMilli(maxOf(startMs + 60_000L, endMs))
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(start)

            val records = mutableListOf<androidx.health.connect.client.records.Record>()
            records.add(
                ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = zoneOffset,
                    endTime = end,
                    endZoneOffset = zoneOffset,
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                    title = title.take(80),
                    metadata = Metadata.manualEntry()
                )
            )
            if (caloriesBurned > 0 && granted.contains(HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class))) {
                records.add(
                    TotalCaloriesBurnedRecord(
                        startTime = start,
                        startZoneOffset = zoneOffset,
                        endTime = end,
                        endZoneOffset = zoneOffset,
                        energy = Energy.kilocalories(caloriesBurned.toDouble()),
                        metadata = Metadata.manualEntry()
                    )
                )
            }
            client.insertRecords(records)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed writing workout to Health Connect", e)
            false
        }
    }

    companion object {
        private const val TAG = "HealthConnectMgr"
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    }
}
