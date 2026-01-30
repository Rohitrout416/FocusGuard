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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Indeterminate ring for active "pulse" feel
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(140.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
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
                    style = MaterialTheme.typography.displaySmall, // Bigger font
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
