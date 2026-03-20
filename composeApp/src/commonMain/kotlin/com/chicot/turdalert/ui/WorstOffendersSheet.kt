package com.chicot.turdalert.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chicot.turdalert.api.WorstOffenderResult

private val SheetBackground = Color(0xFFF5F5F5)
private val HeaderBackground = Color(0xFF1E1E2E)

private val TabActive = Color.White
private val TabInactive = Color.White.copy(alpha = 0.5f)
private val TabUnderline = Color.White

@Composable
fun WorstOffendersSheet(
    offenders: List<WorstOffenderResult>?,
    nationalOffenders: List<WorstOffenderResult>?,
    onTabChanged: (Boolean) -> Unit,
    onSiteClick: (WorstOffenderResult) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    var showNational by remember { mutableStateOf(false) }
    val displayedOffenders = if (showNational) nationalOffenders else offenders

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SheetBackground)
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HeaderBackground)
                    .padding(top = statusBarPadding.calculateTopPadding())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showNational) "💩 Wall of Shame" else "💩 Worst Offenders",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "✕",
                        color = Color.White,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clickable(onClick = onClose)
                            .padding(8.dp)
                    )
                }
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TabLabel("Near Me", !showNational) {
                        showNational = false
                        onTabChanged(false)
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    TabLabel("National", showNational) {
                        showNational = true
                        onTabChanged(true)
                    }
                }
            }

            if (displayedOffenders == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (displayedOffenders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showNational) "No discharge events recorded yet"
                            else "No discharge events recorded nearby",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(displayedOffenders) { index, offender ->
                        OffenderCard(
                            rank = index + 1,
                            offender = offender,
                            onClick = { onSiteClick(offender) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OffenderCard(
    rank: Int,
    offender: WorstOffenderResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$rank",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = rankColor(rank),
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = offender.site.siteName ?: offender.site.id,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${offender.site.watercourse ?: "Unknown"} · ${offender.site.company}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatOffenderStats(offender.stats),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF1744)
                )
            }
        }
    }
}

private fun rankColor(rank: Int): Color = when (rank) {
    1 -> Color(0xFFFF1744)
    2 -> Color(0xFFFF5722)
    3 -> Color(0xFFFF9800)
    else -> Color.Gray
}

@Composable
private fun TabLabel(text: String, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            color = if (active) TabActive else TabInactive,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
        if (active) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TabUnderline)
                    .fillMaxWidth(0.5f)
                    .padding(vertical = 1.dp)
            )
        }
    }
}

private fun formatOffenderStats(stats: com.chicot.turdalert.api.SiteStatsResponse): String {
    val hours = kotlin.math.round(stats.totalDischargeHours * 10) / 10
    val pct = kotlin.math.round(stats.percentDischarging * 10) / 10
    return "${stats.eventCount} events · ${hours}h · ${pct}% of time"
}
