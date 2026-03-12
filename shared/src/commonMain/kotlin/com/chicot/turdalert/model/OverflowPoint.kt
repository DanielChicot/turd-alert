package com.chicot.turdalert.model

import kotlinx.serialization.Serializable

@Serializable
data class OverflowPoint(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val status: DischargeStatus,
    val watercourse: String,
    val siteName: String,
    val statusStart: Long? = null,
    val company: String
)

enum class DischargeStatus {
    DISCHARGING,
    NOT_DISCHARGING,
    OFFLINE
}
