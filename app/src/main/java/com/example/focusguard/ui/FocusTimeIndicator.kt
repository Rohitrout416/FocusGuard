package com.example.focusguard.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FocusTimeIndicator(
    currentSessionMs: Long,
    dailyTotalMs: Long
) {
    // Show even if < 1m to give immediate feedback
    
    val sessionMin = currentSessionMs / 60000
    val sessionHours = sessionMin / 60
    val displayMin = sessionMin % 60
    
    val formattedTime = if (sessionHours > 0) {
        "%d:%02d".format(sessionHours, displayMin)
    } else {
        "${sessionMin}m"
    }
    
    // Progress towards 2-hour milestone (0.0 to 1.0)
    // 2 hours = 120 minutes = 7,200,000 ms
    val milestoneIntervalMs = 7200000f
    val rawProgress = (currentSessionMs % milestoneIntervalMs) / milestoneIntervalMs
    
    // Smooth animation
    val progress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
        label = "progress"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Background Track (faint ring)
            androidx.compose.material3.CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(160.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 6.dp, // Slightly thicker track
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            
            // Determinate Progress Ring (fills up)
            androidx.compose.material3.CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(160.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp,
                trackColor = Color.Transparent, // Track is handled by the background ring above
            )
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "IN FOCUS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alpha(0.8f)
                )
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
