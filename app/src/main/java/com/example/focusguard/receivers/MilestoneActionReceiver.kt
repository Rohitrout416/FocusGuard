package com.example.focusguard.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.work.WorkManager
import com.example.focusguard.data.FocusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MilestoneActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DISABLE_MILESTONES = "com.example.focusguard.ACTION_DISABLE_MILESTONES"
        const val NOTIFICATION_ID_MILESTONE = 999
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISABLE_MILESTONES) {
            val repository = FocusRepository(context)
            
            // Disable preference
            repository.setMilestonesEnabled(false)

            // Cancel any pending work
            WorkManager.getInstance(context).cancelUniqueWork("focus_milestone_work")

            // Dismiss the notification
            val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
            notificationManager.cancel(NOTIFICATION_ID_MILESTONE)

            // Feedback
            Toast.makeText(context, "Focus Milestones Disabled", Toast.LENGTH_SHORT).show()
        }
    }
}
