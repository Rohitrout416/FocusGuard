package com.example.focusguard.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.focusguard.data.SenderScoreEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val senders by viewModel.allSenders.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings - Message Sources") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "Customize message behavior below:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (senders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No message sources found yet.")
                }
            } else {
                LazyColumn {
                    items(senders, key = { it.senderId }) { sender ->
                        SenderConfigItem(
                            sender = sender,
                            onUpdate = { isPrimary, isVip ->
                                viewModel.updateSenderConfig(sender.senderId, isPrimary, isVip)
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun SenderConfigItem(
    sender: SenderScoreEntity,
    onUpdate: (isPrimary: Boolean, isVip: Boolean) -> Unit
) {
    // senderId format: "package:Title"
    val parts = sender.senderId.split(":")
    val title = parts.getOrElse(1) { sender.senderId }
    val subtitle = parts.getOrNull(0) ?: ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Header
        Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.height(8.dp))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Primary Toggle
            Column(modifier = Modifier.weight(1f)) {
                Text("Primary Section", fontWeight = FontWeight.Medium)
                Text(
                    if (sender.isPrimary) "Shows in Priority" else "Shows in Spam",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = sender.isPrimary,
                onCheckedChange = { onUpdate(it, sender.isVip) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // VIP Toggle
            Column(modifier = Modifier.weight(1f)) {
                Text("Focus Bypass (VIP)", fontWeight = FontWeight.Medium)
                Text(
                    if (sender.isVip) "Can notify during Focus Mode" else "Blocked during Focus Mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sender.isVip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = sender.isVip,
                onCheckedChange = { onUpdate(sender.isPrimary, it) }
            )
        }
    }
}
