package com.chicot.turdalert.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.chicot.turdalert.api.SiteStatsResponse
import com.chicot.turdalert.location.Coordinates
import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint
import com.chicot.turdalert.util.distanceMiles
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.until

private fun formatDuration(startMillis: Long, nowMillis: Long): String {
    val elapsedMinutes = ((nowMillis - startMillis) / 60_000).coerceAtLeast(0)
    val hours = elapsedMinutes / 60
    val minutes = elapsedMinutes % 60
    return when {
        hours > 0 -> "Discharging for ${hours}h ${minutes}m"
        else -> "Discharging for ${minutes}m"
    }
}

private fun formatDistance(miles: Double): String = when {
    miles < 0.1 -> "< 0.1 mi"
    else -> "${kotlin.math.round(miles * 10) / 10} mi"
}

private fun formatStats(stats: SiteStatsResponse): String {
    val hours = kotlin.math.round(stats.totalDischargeHours * 10) / 10
    val events = stats.eventCount
    val pct = kotlin.math.round(stats.percentDischarging * 10) / 10
    return "${events} events · ${hours}h total · ${pct}% of time"
}

private fun formatLastDischarge(lastDischargeAt: String?): String {
    if (lastDischargeAt == null) return "No discharge recorded"
    val then = Instant.parse(lastDischargeAt)
    val now = Clock.System.now()
    val duration = then.until(now, kotlinx.datetime.DateTimeUnit.MINUTE, TimeZone.UTC)
    val hours = duration / 60
    val days = hours / 24
    return when {
        duration < 60 -> "Last discharge: ${duration}m ago"
        hours < 24 -> "Last discharge: ${hours}h ago"
        days == 1L -> "Last discharge: yesterday"
        else -> "Last discharge: ${days}d ago"
    }
}

@Composable
fun OverflowInfoCard(
    overflow: OverflowPoint,
    userLocation: Coordinates,
    currentTimeMillis: Long,
    siteStats: SiteStatsResponse? = null,
    onDirectionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val distance = distanceMiles(
        userLocation.latitude, userLocation.longitude,
        overflow.latitude, overflow.longitude
    )

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIndicator(status = overflow.status)
                Text(
                    text = overflow.siteName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "${overflow.watercourse} · ${overflow.company}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (overflow.status == DischargeStatus.DISCHARGING && overflow.statusStart != null) {
                Text(
                    text = formatDuration(overflow.statusStart!!, currentTimeMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF1744),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (siteStats != null && overflow.status != DischargeStatus.DISCHARGING) {
                Text(
                    text = formatLastDischarge(siteStats.lastDischargeAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (siteStats != null && siteStats.eventCount > 0) {
                Text(
                    text = formatStats(siteStats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDistance(distance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onDirectionsClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4285F4)
                    )
                ) {
                    Text("Directions")
                }
            }
        }
    }
}
