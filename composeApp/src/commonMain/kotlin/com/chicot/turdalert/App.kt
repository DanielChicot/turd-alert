package com.chicot.turdalert

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chicot.turdalert.api.createHybridOverflowRepository
import com.chicot.turdalert.location.LocationProvider
import com.chicot.turdalert.map.MapView
import com.chicot.turdalert.map.openDirections
import com.chicot.turdalert.model.cameraBounds
import com.chicot.turdalert.ui.OverflowInfoCard
import com.chicot.turdalert.ui.RefreshFAB
import com.chicot.turdalert.ui.SummaryChip
import com.chicot.turdalert.viewmodel.OverflowViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.Clock

@Composable
fun App(locationProvider: LocationProvider) {
    val viewModel = remember {
        val repository = createHybridOverflowRepository("http://100.83.26.78:8080")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        OverflowViewModel(repository, locationProvider, scope)
    }

    val uiState by viewModel.state.collectAsState()
    val selectedOverflow by viewModel.selectedOverflow.collectAsState()
    val hasUserInteracted by viewModel.hasUserInteracted.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    MaterialTheme {
        when (val state = uiState) {
            is OverflowViewModel.UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is OverflowViewModel.UiState.Loaded -> {
                val bounds = if (hasUserInteracted) null
                    else cameraBounds(state.overflows, state.location)

                Box(modifier = Modifier.fillMaxSize()) {
                    MapView(
                        overflows = state.overflows,
                        userLocation = state.location,
                        bounds = bounds,
                        onMarkerClick = { viewModel.selectOverflow(it) },
                        onMapClick = { viewModel.clearSelection() },
                        onViewportChanged = { viewModel.onViewportChanged(it) },
                        modifier = Modifier.fillMaxSize()
                    )

                    RefreshFAB(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    )

                    SummaryChip(
                        overflows = state.overflows,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )

                    selectedOverflow?.let { overflow ->
                        OverflowInfoCard(
                            overflow = overflow,
                            userLocation = state.location,
                            currentTimeMillis = Clock.System.now().toEpochMilliseconds(),
                            onDirectionsClick = {
                                openDirections(overflow.latitude, overflow.longitude)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 64.dp)
                        )
                    }
                }
            }

            is OverflowViewModel.UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            is OverflowViewModel.UiState.NoLocation -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Unable to determine your location",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please enable location services and try again",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
