package com.chicot.turdalert.map

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri

@SuppressLint("StaticFieldLeak")
object AndroidContextHolder {
    lateinit var appContext: Context
}

actual fun openDirections(latitude: Double, longitude: Double) {
    val uri = Uri.parse("geo:0,0?q=$latitude,$longitude")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    AndroidContextHolder.appContext.startActivity(intent)
}
