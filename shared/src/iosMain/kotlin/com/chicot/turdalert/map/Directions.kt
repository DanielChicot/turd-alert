package com.chicot.turdalert.map

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openDirections(latitude: Double, longitude: Double) {
    val url = NSURL(string = "http://maps.apple.com/?daddr=$latitude,$longitude")
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
}
