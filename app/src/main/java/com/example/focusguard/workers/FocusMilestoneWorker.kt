package com.example.focusguard.workers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.focusguard.data.FocusRepository
import com.example.focusguard.receivers.MilestoneActionReceiver
import com.example.focusguard.util.NotificationHelper
import java.util.concurrent.TimeUnit

class FocusMilestoneWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val repository = FocusRepository(context)

        // 1. Check if Focus Mode is still active
        if (!repository.isFocusModeActive()) {
            return Result.success()
        }

        // 2. Check if Milestones are enabled
        if (!repository.areMilestonesEnabled()) {
            return Result.success() // Stop chain if disabled
        }

        // 3. Calculate elapsed time
        val metrics = repository.getFocusMetrics()
        val sessionDurationMin = metrics.first / 60000
        val sessionDurationHours = sessionDurationMin / 60

        // 4. Send Notification (if duration > 1 hour)
        if (sessionDurationHours >= 1) {
            val disableIntent = Intent(context, MilestoneActionReceiver::class.java).apply {
                action = MilestoneActionReceiver.ACTION_DISABLE_MILESTONES
            }
            val disablePendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                disableIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            NotificationHelper.showMilestoneNotification(
                context, 
                sessionDurationHours.toInt(), 
                disablePendingIntent
            )
        }

        // 5. Build and Enqueue Next Work (Cycle: 2 Hours)
        // We use OneTimeWorkRequest to chain safely rather than Periodic which has min 15m intervals
        // but here we want clean 2h intervals from *now*.
        val nextWork = OneTimeWorkRequestBuilder<FocusMilestoneWorker>()
            .setInitialDelay(2, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "focus_milestone_work",
            ExistingWorkPolicy.REPLACE,
            nextWork
        )

        return Result.success()
    }
}
