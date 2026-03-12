package com.chicot.turdalert.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chicot.turdalert.location.Coordinates
import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.OverflowPoint

@Composable
expect fun MapView(
    overflows: List<OverflowPoint>,
    userLocation: Coordinates,
    bounds: BoundingBox?,
    onMarkerClick: (OverflowPoint) -> Unit,
    onMapClick: () -> Unit,
    onViewportChanged: (BoundingBox) -> Unit,
    modifier: Modifier = Modifier
)
