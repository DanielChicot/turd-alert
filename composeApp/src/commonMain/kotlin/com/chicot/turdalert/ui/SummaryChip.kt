package com.chicot.turdalert.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint

@Composable
fun SummaryChip(
    overflows: List<OverflowPoint>,
    modifier: Modifier = Modifier
) {
    val discharging = overflows.count { it.status == DischargeStatus.DISCHARGING }

    val text = buildAnnotatedString {
        append("${overflows.size} nearby")
        if (discharging > 0) {
            append(" · ")
            withStyle(SpanStyle(color = Color(0xFFFF1744))) {
                append("$discharging discharging")
            }
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xE61E1E2E))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp
        )
    }
}
