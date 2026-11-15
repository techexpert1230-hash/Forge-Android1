package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderChannels {
    const val CHANNEL_WORKOUT = "forge_workout_reminders"
    const val CHANNEL_HYDRATION = "forge_hydration_reminders"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val workoutChannel = NotificationChannel(
                CHANNEL_WORKOUT,
                "Workout & Goal Check-Ins",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily workout and personal goal reminders from Forge."
            }
            val hydrationChannel = NotificationChannel(
                CHANNEL_HYDRATION,
                "Hydration Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Periodic water intake reminders to hit your daily liters target."
            }
            manager.createNotificationChannel(workoutChannel)
            manager.createNotificationChannel(hydrationChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun postNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String
    ) {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_forge_notif)
            .setColor(0xFFC7FF35.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}

class WorkoutReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ReminderChannels.postNotification(
            context = applicationContext,
            channelId = ReminderChannels.CHANNEL_WORKOUT,
            notificationId = 1001,
            title = "Forge Daily Check-In",
            body = "Time to train or log your push-ups, squats, and daily goals!"
        )
        return Result.success()
    }
}

class WaterReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour in 7..22) {
            ReminderChannels.postNotification(
                context = applicationContext,
                channelId = ReminderChannels.CHANNEL_HYDRATION,
                notificationId = 1002,
                title = "Hydration Check",
                body = "Grab a glass of water (250ml) and keep your daily hydration streak alive."
            )
        }
        return Result.success()
    }
}

class ReminderScheduler(private val context: Context) {

    fun syncSchedules(
        workoutEnabled: Boolean,
        workoutHour: Int,
        workoutMinute: Int,
        waterEnabled: Boolean,
        waterIntervalHours: Int
    ) {
        ReminderChannels.ensureChannels(context)
        val wm = WorkManager.getInstance(context)

        if (workoutEnabled) {
            val delayMin = computeDelayMinutesTo(workoutHour, workoutMinute)
            val request = PeriodicWorkRequestBuilder<WorkoutReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMin, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(
                WORK_WORKOUT,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            wm.cancelUniqueWork(WORK_WORKOUT)
        }

        if (waterEnabled) {
            val interval = waterIntervalHours.coerceIn(1, 12).toLong()
            val request = PeriodicWorkRequestBuilder<WaterReminderWorker>(interval, TimeUnit.HOURS)
                .setInitialDelay(interval, TimeUnit.HOURS)
                .build()
            wm.enqueueUniquePeriodicWork(
                WORK_WATER,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            wm.cancelUniqueWork(WORK_WATER)
        }
    }

    fun sendTestNotification() {
        ReminderChannels.postNotification(
            context = context,
            channelId = ReminderChannels.CHANNEL_WORKOUT,
            notificationId = 1099,
            title = "Forge Reminders Active",
            body = "Workout check-ins and hydration reminders are enabled on this device."
        )
    }

    private fun computeDelayMinutesTo(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
        }
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        val diffMs = target.timeInMillis - now.timeInMillis
        return (diffMs / 60_000L).coerceAtLeast(1L)
    }

    private companion object {
        const val WORK_WORKOUT = "forge_daily_workout_reminder"
        const val WORK_WATER = "forge_periodic_water_reminder"
    }
}
