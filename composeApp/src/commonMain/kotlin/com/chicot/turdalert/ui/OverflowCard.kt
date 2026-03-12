package com.chicot.turdalert.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint

private val dischargingTint = Color(0x1AFF1744)

private fun formatDuration(startMillis: Long, nowMillis: Long): String {
    val elapsedMinutes = ((nowMillis - startMillis) / 60_000).coerceAtLeast(0)
    val hours = elapsedMinutes / 60
    val minutes = elapsedMinutes % 60
    return when {
        hours > 0 -> "Discharging for ${hours}h ${minutes}m"
        else -> "Discharging for ${minutes}m"
    }
}

@Composable
fun OverflowCard(
    overflow: OverflowPoint,
    currentTimeMillis: Long,
    modifier: Modifier = Modifier
) {
    val containerColor = when (overflow.status) {
        DischargeStatus.DISCHARGING -> dischargingTint
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            StatusIndicator(
                status = overflow.status,
                modifier = Modifier.padding(top = 4.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = overflow.siteName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = overflow.watercourse,
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = overflow.company,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (overflow.status == DischargeStatus.DISCHARGING && overflow.statusStart != null) {
                    Text(
                        text = formatDuration(overflow.statusStart!!, currentTimeMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF1744),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
