package com.chicot.turdalert.backend.poller

import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class PollerTest {

    private val samplePoint = OverflowPoint(
        id = "SITE-001",
        latitude = 51.5,
        longitude = -0.1,
        status = DischargeStatus.DISCHARGING,
        watercourse = "River Thames",
        siteName = "Test Overflow",
        statusStart = 1700000000000L,
        company = "Thames Water"
    )

    @Test
    fun `toSiteRow maps OverflowPoint correctly`() {
        val siteRow = samplePoint.toSiteRow()
        assertEquals("Thames Water", siteRow.company)
        assertEquals("SITE-001", siteRow.siteId)
        assertEquals("Test Overflow", siteRow.siteName)
        assertEquals("River Thames", siteRow.watercourse)
        assertEquals(51.5, siteRow.latitude)
        assertEquals(-0.1, siteRow.longitude)
    }

    @Test
    fun `toReadingRow maps status correctly`() {
        val polledAt = OffsetDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC)
        val readingRow = samplePoint.toReadingRow(polledAt)
        assertEquals("Thames Water", readingRow.company)
        assertEquals("SITE-001", readingRow.siteId)
        assertEquals(polledAt, readingRow.polledAt)
        assertEquals(1.toShort(), readingRow.status)
    }

    @Test
    fun `toDbStatus maps all statuses`() {
        data class StatusMapping(val input: DischargeStatus, val expected: Short)
        listOf(
            StatusMapping(DischargeStatus.DISCHARGING, 1),
            StatusMapping(DischargeStatus.NOT_DISCHARGING, 0),
            StatusMapping(DischargeStatus.RECENT_DISCHARGE, 0),
            StatusMapping(DischargeStatus.OFFLINE, -1)
        ).forEach { (input, expected) ->
            assertEquals(expected, input.toDbStatus(), "Expected $input to map to $expected")
        }
    }
}
