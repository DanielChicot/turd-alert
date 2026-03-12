package com.chicot.turdalert.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RefreshFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        containerColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
    ) {
        Text("\u21BB", fontSize = 22.sp, color = Color.DarkGray)
    }
}
