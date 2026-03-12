package com.chicot.turdalert.domain

import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint

private const val ONE_HOUR_MS = 3_600_000L

fun List<OverflowPoint>.withRecentDischargeStatus(
    nowMillis: Long
): List<OverflowPoint> = map { point ->
    if (point.status == DischargeStatus.NOT_DISCHARGING &&
        point.statusStart != null &&
        (nowMillis - point.statusStart) <= ONE_HOUR_MS
    ) {
        point.copy(status = DischargeStatus.RECENT_DISCHARGE)
    } else {
        point
    }
}
