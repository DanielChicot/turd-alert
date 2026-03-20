package com.chicot.turdalert.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint

private val BarBackground = Color(0xE61E1E2E)
private val DischargeRed = Color(0xFFFF1744)

@Composable
fun TopBar(
    overflows: List<OverflowPoint>,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val discharging = overflows.count { it.status == DischargeStatus.DISCHARGING }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BarBackground)
            .padding(top = statusBarPadding.calculateTopPadding())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💩", fontSize = 20.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Turd Alert",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = summaryText(overflows.size, discharging),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        }

        Text(
            text = "\u21BB",
            fontSize = 22.sp,
            color = Color.White,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onRefreshClick)
                .padding(8.dp)
        )
    }
}

private fun summaryText(total: Int, discharging: Int): String =
    if (discharging > 0) "$total nearby · $discharging discharging"
    else "$total nearby"
