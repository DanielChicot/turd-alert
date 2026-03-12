package com.chicot.turdalert.api

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

internal fun osgbToWgs84(easting: Double, northing: Double): Pair<Double, Double> {
    val (osgbLat, osgbLon) = osgbToLatLon(easting, northing)
    return helmertTransform(osgbLat, osgbLon)
}

private fun toRadians(degrees: Double): Double = degrees * PI / 180.0

private fun toDegrees(radians: Double): Double = radians * 180.0 / PI

private fun osgbToLatLon(easting: Double, northing: Double): Pair<Double, Double> {
    val a = 6377563.396
    val b = 6356256.909
    val f0 = 0.9996012717
    val lat0 = toRadians(49.0)
    val lon0 = toRadians(-2.0)
    val n0 = -100000.0
    val e0 = 400000.0

    val e2 = 1 - (b * b) / (a * a)
    val n = (a - b) / (a + b)
    val n2 = n * n
    val n3 = n * n * n

    var lat = lat0
    var m = 0.0

    do {
        lat = (northing - n0 - m) / (a * f0) + lat
        val latMinusLat0 = lat - lat0
        val latPlusLat0 = lat + lat0

        m = b * f0 * (
            (1 + n + (5.0 / 4) * n2 + (5.0 / 4) * n3) * latMinusLat0 -
            (3 * n + 3 * n2 + (21.0 / 8) * n3) * sin(latMinusLat0) * cos(latPlusLat0) +
            ((15.0 / 8) * n2 + (15.0 / 8) * n3) * sin(2 * latMinusLat0) * cos(2 * latPlusLat0) -
            (35.0 / 24) * n3 * sin(3 * latMinusLat0) * cos(3 * latPlusLat0)
        )
    } while ((northing - n0 - m) >= 0.00001)

    val sinLat = sin(lat)
    val cosLat = cos(lat)
    val tanLat = tan(lat)
    val tan2 = tanLat * tanLat
    val tan4 = tan2 * tan2
    val tan6 = tan4 * tan2

    val nu = a * f0 / sqrt(1 - e2 * sinLat * sinLat)
    val rho = a * f0 * (1 - e2) / (1 - e2 * sinLat * sinLat).pow(1.5)
    val eta2 = nu / rho - 1

    val de = easting - e0
    val de2 = de * de
    val de3 = de2 * de
    val de4 = de2 * de2
    val de5 = de3 * de2
    val de6 = de3 * de3
    val de7 = de4 * de3

    val vii = tanLat / (2 * rho * nu)
    val viii = tanLat / (24 * rho * nu * nu * nu) * (5 + 3 * tan2 + eta2 - 9 * tan2 * eta2)
    val ix = tanLat / (720 * rho * nu.pow(5)) * (61 + 90 * tan2 + 45 * tan4)
    val x = 1 / (cosLat * nu)
    val xi = 1 / (6 * cosLat * nu * nu * nu) * (nu / rho + 2 * tan2)
    val xii = 1 / (120 * cosLat * nu.pow(5)) * (5 + 28 * tan2 + 24 * tan4)
    val xiia = 1 / (5040 * cosLat * nu.pow(7)) * (61 + 662 * tan2 + 1320 * tan4 + 720 * tan6)

    val resultLat = lat - vii * de2 + viii * de4 - ix * de6
    val resultLon = lon0 + x * de - xi * de3 + xii * de5 - xiia * de7

    return Pair(toDegrees(resultLat), toDegrees(resultLon))
}

private fun helmertTransform(osgbLat: Double, osgbLon: Double): Pair<Double, Double> {
    val latRad = toRadians(osgbLat)
    val lonRad = toRadians(osgbLon)

    val a = 6377563.396
    val b = 6356256.909
    val e2 = 1 - (b * b) / (a * a)

    val sinLat = sin(latRad)
    val cosLat = cos(latRad)
    val sinLon = sin(lonRad)
    val cosLon = cos(lonRad)

    val nu = a / sqrt(1 - e2 * sinLat * sinLat)
    val x1 = nu * cosLat * cosLon
    val y1 = nu * cosLat * sinLon
    val z1 = (1 - e2) * nu * sinLat

    val tx = 446.448
    val ty = -125.157
    val tz = 542.060
    val s = -20.4894e-6
    val rx = toRadians(0.1502 / 3600.0)
    val ry = toRadians(0.2470 / 3600.0)
    val rz = toRadians(0.8421 / 3600.0)

    val x2 = tx + (1 + s) * x1 + (-rz) * y1 + ry * z1
    val y2 = ty + rz * x1 + (1 + s) * y1 + (-rx) * z1
    val z2 = tz + (-ry) * x1 + rx * y1 + (1 + s) * z1

    val aWgs = 6378137.0
    val bWgs = 6356752.3142
    val e2Wgs = 1 - (bWgs * bWgs) / (aWgs * aWgs)

    val p = sqrt(x2 * x2 + y2 * y2)
    var wgsLat = atan2(z2, p * (1 - e2Wgs))

    repeat(10) {
        val nuWgs = aWgs / sqrt(1 - e2Wgs * sin(wgsLat) * sin(wgsLat))
        wgsLat = atan2(z2 + e2Wgs * nuWgs * sin(wgsLat), p)
    }

    val wgsLon = atan2(y2, x2)

    return Pair(toDegrees(wgsLat), toDegrees(wgsLon))
}
