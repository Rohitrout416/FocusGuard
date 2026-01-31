package com.example.focusguard.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.focusguard.util.NotificationHelper

class FocusStatusService : Service() {

    companion object {
        private const val TAG = "FocusGuard"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        Log.d(TAG, "FocusStatusService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FocusStatusService onStartCommand")
        
        val notification = NotificationHelper.getFocusStatusNotification(this)
        
        // Android 14+ requires explicit foreground service type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.FOCUS_STATUS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.FOCUS_STATUS_NOTIFICATION_ID, notification)
        }
        
        Log.d(TAG, "FocusStatusService started foreground")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FocusStatusService destroyed")
    }
}
