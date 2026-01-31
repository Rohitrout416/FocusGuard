package com.example.focusguard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.focusguard.data.NotificationEntity
import com.example.focusguard.data.SenderCategory
import com.example.focusguard.ui.ClassificationBanner
import com.example.focusguard.ui.FocusTimeIndicator
import com.example.focusguard.ui.MainViewModel
import com.example.focusguard.ui.SettingsScreen
import com.example.focusguard.ui.theme.FocusGuardTheme
import com.example.focusguard.util.AppUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    
    private val focusRepository by lazy { com.example.focusguard.data.FocusRepository(this) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Create notification channels FIRST (before any service can start)
        com.example.focusguard.util.NotificationHelper.createNotificationChannel(this)
        
        // Request notification permission on Android 13+
        requestNotificationPermission()
        
        setContent {
            FocusGuardTheme {
                AppNavigation()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        android.util.Log.d("FocusGuard", "MainActivity.onResume called")
        // Ensure service is running if Focus Mode is ON
        ensureFocusServiceState()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int, 
        permissions: Array<String>, 
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        android.util.Log.d("FocusGuard", "onRequestPermissionsResult: requestCode=$requestCode")
        
        if (requestCode == 100 && grantResults.isNotEmpty() && 
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.util.Log.d("FocusGuard", "POST_NOTIFICATIONS permission granted, ensuring service state")
            // Permission was just granted - restart service if Focus Mode is ON
            ensureFocusServiceState()
        }
    }
    
    private fun ensureFocusServiceState() {
        val isFocusOn = focusRepository.isFocusModeActive()
        android.util.Log.d("FocusGuard", "ensureFocusServiceState: isFocusOn=$isFocusOn")
        
        if (isFocusOn) {
            // Focus Mode is ON, ensure service is running
            try {
                val serviceIntent = android.content.Intent(this, com.example.focusguard.service.FocusStatusService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                android.util.Log.d("FocusGuard", "MainActivity: Started/restarted FocusStatusService")
            } catch (e: Exception) {
                android.util.Log.e("FocusGuard", "MainActivity: Failed to ensure service: ${e.message}")
            }
        }
    }
    
    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.util.Log.d("FocusGuard", "Requesting POST_NOTIFICATIONS permission")
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            } else {
                android.util.Log.d("FocusGuard", "POST_NOTIFICATIONS already granted")
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isPermissionGranted by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = NotificationManagerCompat
                    .getEnabledListenerPackages(context)
                    .contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val focusModeActive by viewModel.focusModeActive.collectAsState()
    
    // Notification Streams
    val unknownNotifications by viewModel.unknownNotifications.collectAsState()
    val primaryNotifications by viewModel.primaryNotifications.collectAsState()
    val vipNotifications by viewModel.vipNotifications.collectAsState()
    val spamNotifications by viewModel.spamNotifications.collectAsState()
    
    // Banner Logic
    val uncategorizedSenders by viewModel.uncategorizedSenders.collectAsState()
    val dismissed by viewModel.dismissedSenders.collectAsState()
    
    val senderToClassify = uncategorizedSenders.firstOrNull { !dismissed.contains(it.senderId) }

    // Tabs: 0=Unknown, 1=Primary, 2=VIP, 3=Spam
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "FocusGuard",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp), // Check visual balance
                        letterSpacing = 1.sp // Subtle spacing for "Logo" feel
                    ) 
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings, 
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, // Clean surface background
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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

            // Classification Banner (Logic: Count >= 3)
            if (senderToClassify != null && senderToClassify.msgCount >= 3) {
                val packageName = senderToClassify.senderId.substringBefore(":")
                val senderName = senderToClassify.senderId.substringAfter(":")
                val appName = AppUtils.getAppName(context, packageName)

                ClassificationBanner(
                    senderName = senderName,
                    appName = appName,
                    msgCount = senderToClassify.msgCount,
                    onCategorize = { category ->
                        viewModel.categorizeSender(senderToClassify.senderId, category)
                    },
                    onDismiss = {
                        viewModel.dismissBanner(senderToClassify.senderId)
                    }
                )
            } else if (senderToClassify != null) {
                // Small subtle banner for new senders (Count < 3)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("New sender detected: Review in 'Unknown' tab when convenient.", style = MaterialTheme.typography.bodySmall)
                    }
                }
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
                    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
                    Switch(
                        checked = focusModeActive,
                        onCheckedChange = { 
                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            viewModel.toggleFocusMode() 
                        }
                    )
                    }
            }
            
            // Session Summary (Quiet Closure)
            val sessionSummary by viewModel.sessionSummary.collectAsState()
            
            if (sessionSummary != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(
                       containerColor = MaterialTheme.colorScheme.surface, // Calm, blends in
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Focus session complete", 
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = sessionSummary!!, 
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { viewModel.dismissSessionSummary() }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                contentDescription = "Dismiss"
                            )
                        }
                    }
                }
            }
            
            // Focus Metrics
            val focusMetrics by viewModel.focusMetrics.collectAsState()
            
            if (focusModeActive) {
                FocusTimeIndicator(
                    currentSessionMs = focusMetrics.first,
                    dailyTotalMs = focusMetrics.second
                )
            }

            Spacer(Modifier.height(16.dp))

            // 4 Tabs
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Unknown") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Primary") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("VIP") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Spam") })
            }

            Spacer(Modifier.height(8.dp))

            // List Selection
            val currentList = when (selectedTab) {
                0 -> unknownNotifications
                1 -> primaryNotifications
                2 -> vipNotifications
                3 -> spamNotifications
                else -> emptyList()
            }
            
            // Undo Category Mapping
            val currentCategory = when (selectedTab) {
                0 -> SenderCategory.UNKNOWN
                1 -> SenderCategory.PRIMARY
                2 -> SenderCategory.VIP
                3 -> SenderCategory.SPAM
                else -> SenderCategory.UNKNOWN
            }
            
            // Clear All Button
            if (currentList.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.clearAllNotifications() }) {
                        Text("Clear All")
                    }
                }
            }
            
            if (currentList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        when (selectedTab) {
                            0 -> "No new senders"
                            1 -> "No primary messages"
                            2 -> "No VIP messages"
                            3 -> "No spam messages"
                            else -> "Empty"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(currentList, key = { it.id }) { notification ->
                        NotificationItem(
                            notification = notification,
                            onMarkPrimary = {
                                val senderId = "${notification.packageName}:${notification.senderName}"
                                viewModel.categorizeSender(senderId, SenderCategory.PRIMARY)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Marked as Primary",
                                        actionLabel = "UNDO",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.categorizeSender(senderId, currentCategory)
                                    }
                                }
                            },
                            onMarkSpam = {
                                val senderId = "${notification.packageName}:${notification.senderName}"
                                viewModel.categorizeSender(senderId, SenderCategory.SPAM)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Marked as Spam",
                                        actionLabel = "UNDO",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.categorizeSender(senderId, currentCategory)
                                    }
                                }
                            },
                            onMarkVip = {
                                val senderId = "${notification.packageName}:${notification.senderName}"
                                viewModel.categorizeSender(senderId, SenderCategory.VIP)
                                
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Marked as VIP (Always Allowed)",
                                        actionLabel = "UNDO",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.categorizeSender(senderId, currentCategory)
                                    }
                                }
                            },
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
    onMarkVip: () -> Unit,
    onOpenApp: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val appName = AppUtils.getAppName(context, notification.packageName)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onOpenApp() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header: Sender & App Info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notification.senderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$appName • ${dateFormat.format(Date(notification.timestamp))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary Action
                AssistChip(
                    onClick = onMarkPrimary,
                    label = { Text("Primary") },
                    leadingIcon = {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        leadingIconContentColor = MaterialTheme.colorScheme.primary
                    )
                )

                // VIP Action
                AssistChip(
                    onClick = onMarkVip,
                    label = { Text("VIP") },
                    leadingIcon = {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        leadingIconContentColor = MaterialTheme.colorScheme.tertiary
                    )
                )

                // Spam Action
                AssistChip(
                    onClick = onMarkSpam,
                    label = { Text("Spam") },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        leadingIconContentColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        }
    }
}
