package com.chicot.turdalert

import androidx.compose.ui.window.ComposeUIViewController
import com.chicot.turdalert.location.IosLocationProvider

fun MainViewController() = ComposeUIViewController { App(IosLocationProvider()) }
