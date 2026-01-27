package com.example.focusguard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.focusguard.data.FocusRepository
import com.example.focusguard.data.NotificationEntity
import com.example.focusguard.ui.theme.FocusGuardTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = FocusRepository(applicationContext)
        
        setContent {
            FocusGuardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        repository = repository,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(repository: FocusRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // State
    var isFocusMode by remember { mutableStateOf(repository.isFocusModeActive()) }
    val notifications by repository.getAllHeldNotifications().collectAsState(initial = emptyList())
    
    // Permission Check
    val isPermissionGranted = NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // 1. HEADER & PERMISSION
        Text(
            text = "FocusGuard",
            style = MaterialTheme.typography.headlineMedium
        )
        
        if (!isPermissionGranted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Permission Required!")
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) {
                        Text("Enable Notification Access")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. FOCUS MODE TOGGLE
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Focus Mode",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isFocusMode) "ON - Notifications are blocked" else "OFF - Notifications passing through",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = isFocusMode,
                    onCheckedChange = { active ->
                        isFocusMode = active
                        repository.setFocusModeActive(active)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. HELD NOTIFICATIONS LIST
        Text(
            text = "Blocked Notifications (${notifications.size})",
            style = MaterialTheme.typography.titleMedium
        )
        
        if (notifications.isNotEmpty()) {
            Button(
                onClick = { coroutineScope.launch { repository.clearNotifications() } },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Clear All")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 8.dp)
        ) {
            items(notifications) { notification ->
                NotificationItem(notification)
            }
        }
    }
}

@Composable
fun NotificationItem(notification: NotificationEntity) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.senderName, // Sender Name (Title)
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(notification.timestamp)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = notification.packageName, // App Name
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            // NOTICE: No message content is displayed here because we didn't store it!
            Text(
                text = "Message Content Hidden (Privacy)",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}
