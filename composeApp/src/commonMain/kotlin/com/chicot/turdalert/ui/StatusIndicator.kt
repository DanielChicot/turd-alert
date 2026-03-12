package com.chicot.turdalert.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chicot.turdalert.model.DischargeStatus

private val dischargingRed = Color(0xFFFF1744)
private val recentDischargeYellow = Color(0xFFFFD600)
private val notDischargingGreen = Color(0xFF00C853)
private val offlineGrey = Color(0xFF9E9E9E)

fun DischargeStatus.indicatorColor(): Color = when (this) {
    DischargeStatus.DISCHARGING -> dischargingRed
    DischargeStatus.RECENT_DISCHARGE -> recentDischargeYellow
    DischargeStatus.NOT_DISCHARGING -> notDischargingGreen
    DischargeStatus.OFFLINE -> offlineGrey
}

@Composable
fun StatusIndicator(status: DischargeStatus, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(status.indicatorColor())
    )
}
