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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chicot.turdalert.api.WorstOffenderResult
import com.chicot.turdalert.api.createHybridOverflowRepository
import com.chicot.turdalert.api.createSiteHistoryClient
import com.chicot.turdalert.api.createWorstOffendersClient
import com.chicot.turdalert.location.LocationProvider
import com.chicot.turdalert.map.MapView
import com.chicot.turdalert.map.openDirections
import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.cameraBounds
import com.chicot.turdalert.ui.OverflowInfoCard
import com.chicot.turdalert.ui.SplashOverlay
import com.chicot.turdalert.ui.TopBar
import com.chicot.turdalert.ui.WorstOffendersSheet
import com.chicot.turdalert.viewmodel.OverflowViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@Composable
fun App(locationProvider: LocationProvider) {
    val backendUrl = "http://100.83.26.78:8080"
    val worstOffendersClient = remember { createWorstOffendersClient(backendUrl) }
    val viewModel = remember {
        val repository = createHybridOverflowRepository(backendUrl)
        val historyClient = createSiteHistoryClient(backendUrl)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        OverflowViewModel(repository, locationProvider, scope, historyClient)
    }

    val uiState by viewModel.state.collectAsState()
    val selectedOverflow by viewModel.selectedOverflow.collectAsState()
    val selectedSiteStats by viewModel.selectedSiteStats.collectAsState()
    val hasUserInteracted by viewModel.hasUserInteracted.collectAsState()

    val appScope = rememberCoroutineScope()
    var showWorstOffenders by remember { mutableStateOf(false) }
    var worstOffenders by remember { mutableStateOf<List<WorstOffenderResult>?>(null) }
    var nationalOffenders by remember { mutableStateOf<List<WorstOffenderResult>?>(null) }
    var navigateToBounds by remember { mutableStateOf<BoundingBox?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
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
                val bounds = navigateToBounds
                    ?: if (hasUserInteracted) null
                    else cameraBounds(state.overflows, state.location)

                if (navigateToBounds != null) {
                    LaunchedEffect(navigateToBounds) {
                        kotlinx.coroutines.delay(500)
                        navigateToBounds = null
                    }
                }

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

                    TopBar(
                        overflows = state.overflows,
                        onRefreshClick = { viewModel.refresh() },
                        onWorstOffendersClick = {
                            showWorstOffenders = true
                            worstOffenders = null
                            appScope.launch {
                                val location = locationProvider.currentLocation()
                                if (location != null) {
                                    worstOffenders = worstOffendersClient.worstOffenders(
                                        location.latitude, location.longitude
                                    )
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    selectedOverflow?.let { overflow ->
                        OverflowInfoCard(
                            overflow = overflow,
                            userLocation = state.location,
                            currentTimeMillis = Clock.System.now().toEpochMilliseconds(),
                            siteStats = selectedSiteStats,
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
            if (showWorstOffenders) {
                WorstOffendersSheet(
                    offenders = worstOffenders,
                    nationalOffenders = nationalOffenders,
                    onTabChanged = { isNational ->
                        if (isNational && nationalOffenders == null) {
                            appScope.launch {
                                nationalOffenders = worstOffendersClient.nationalWorstOffenders()
                            }
                        }
                    },
                    onSiteClick = { offender ->
                        showWorstOffenders = false
                        val padding = 0.005
                        navigateToBounds = BoundingBox(
                            minLat = offender.site.latitude - padding,
                            maxLat = offender.site.latitude + padding,
                            minLon = offender.site.longitude - padding,
                            maxLon = offender.site.longitude + padding
                        )
                    },
                    onClose = { showWorstOffenders = false }
                )
            }
            SplashOverlay()
        }
    }
}
