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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.focusguard.data.SenderCategory
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
                title = { Text("Manage Senders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp)
        ) {
            item {
                Text(
                    "Categorize your message sources:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            items(senders, key = { it.senderId }) { sender ->
                SenderConfigItem(
                    sender = sender,
                    onUpdate = { category ->
                        viewModel.categorizeSender(sender.senderId, category)
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun SenderConfigItem(
    sender: SenderScoreEntity,
    onUpdate: (SenderCategory) -> Unit
) {
    val parts = sender.senderId.split(":")
    val title = parts.getOrElse(1) { sender.senderId }
    val subtitle = parts.getOrNull(0) ?: ""

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = sender.category == SenderCategory.SPAM,
                onClick = { onUpdate(SenderCategory.SPAM) },
                label = { Text("Block") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer
                )
            )
            FilterChip(
                selected = sender.category == SenderCategory.PRIMARY || sender.category == SenderCategory.UNKNOWN,
                onClick = { onUpdate(SenderCategory.PRIMARY) },
                label = { Text("Primary") }
            )
            FilterChip(
                selected = sender.category == SenderCategory.VIP,
                onClick = { onUpdate(SenderCategory.VIP) },
                label = { Text("VIP") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
