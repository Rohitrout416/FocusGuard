package com.example.focusguard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.focusguard.data.NotificationEntity
import com.example.focusguard.ui.ClassificationBanner
import com.example.focusguard.ui.MainViewModel
import com.example.focusguard.ui.SettingsScreen
import com.example.focusguard.ui.theme.FocusGuardTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusGuardTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel = viewModel()) {
    var currentScreen by remember { mutableStateOf("home") }

    if (currentScreen == "settings") {
        SettingsScreen(
            viewModel = viewModel,
            onBack = { currentScreen = "home" }
        )
    } else {
        MainScreen(
            viewModel = viewModel,
            onNavigateToSettings = { currentScreen = "settings" }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val isPermissionGranted = NotificationManagerCompat
        .getEnabledListenerPackages(context)
        .contains(context.packageName)

    val focusModeActive by viewModel.focusModeActive.collectAsState()
    val priorityNotifications by viewModel.priorityNotifications.collectAsState()
    val spamNotifications by viewModel.spamNotifications.collectAsState()
    
    // Banner Logic
    val uncategorizedSenders by viewModel.uncategorizedSenders.collectAsState()
    val dismissed by viewModel.dismissedSenders.collectAsState()
    
    // Find first sender that is NOT dismissed
    val senderToClassify = uncategorizedSenders.firstOrNull { !dismissed.contains(it.senderId) }

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FocusGuard") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Permission Card
            if (!isPermissionGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Permission Required", fontWeight = FontWeight.Bold)
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }) {
                            Text("Grant Access")
                        }
                    }
                }
            }

            // Classification Banner
            if (senderToClassify != null) {
                // Extract clean name
                val name = senderToClassify.senderId.substringAfter(":")
                ClassificationBanner(
                    senderName = name,
                    onCategorize = { category ->
                        viewModel.categorizeSender(senderToClassify.senderId, category)
                    },
                    onDismiss = {
                        viewModel.dismissBanner(senderToClassify.senderId)
                    }
                )
            }

            // Focus Mode Toggle
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Focus Mode", fontWeight = FontWeight.Bold)
                        Text(
                            if (focusModeActive) "Auto-blocking active" else "Notifications allowed",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = focusModeActive,
                        onCheckedChange = { viewModel.toggleFocusMode() }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Primary") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Spam") })
            }

            Spacer(Modifier.height(8.dp))

            // List
            val currentList = if (selectedTab == 0) priorityNotifications else spamNotifications
            
            if (currentList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (selectedTab == 0) "No primary messages" else "No spam messages",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(currentList, key = { it.id }) { notification ->
                        NotificationItem(
                            notification = notification,
                            onMarkPrimary = { viewModel.markAsPrimary(notification) },
                            onMarkSpam = { viewModel.markAsSpam(notification) },
                            onOpenApp = {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(notification.packageName)
                                if (launchIntent != null) context.startActivity(launchIntent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: NotificationEntity,
    onMarkPrimary: () -> Unit,
    onMarkSpam: () -> Unit,
    onOpenApp: () -> Unit
) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onOpenApp() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(notification.senderName, fontWeight = FontWeight.Medium)
                Text(
                    "${notification.packageName.substringAfterLast(".")} • ${dateFormat.format(Date(notification.timestamp))}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row {
                IconButton(onClick = onMarkPrimary) {
                    Icon(Icons.Default.Star, "Primary")
                }
                IconButton(onClick = onMarkSpam) {
                    Icon(Icons.Default.Delete, "Spam")
                }
            }
        }
    }
}
