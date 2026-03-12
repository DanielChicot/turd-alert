package com.chicot.turdalert.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint

@Composable
fun OverflowList(
    overflows: List<OverflowPoint>,
    currentTimeMillis: Long,
    modifier: Modifier = Modifier
) {
    val dischargingCount = overflows.count { it.status == DischargeStatus.DISCHARGING }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SummaryHeader(
                totalCount = overflows.size,
                dischargingCount = dischargingCount,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }

        items(overflows, key = { it.id }) { overflow ->
            OverflowCard(
                overflow = overflow,
                currentTimeMillis = currentTimeMillis
            )
        }
    }
}

@Composable
private fun SummaryHeader(
    totalCount: Int,
    dischargingCount: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "$totalCount overflow${if (totalCount != 1) "s" else ""} within 1 mile",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (dischargingCount > 0) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFFFF1744), fontWeight = FontWeight.Bold)) {
                        append("$dischargingCount currently discharging")
                    }
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
