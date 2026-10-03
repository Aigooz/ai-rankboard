package com.ai.rankboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodsTest {
    @Test
    fun buildsMonthlyQuarterlyAndYearlyOptions() {
        val dates = listOf(
            "2026-10-01",
            "2026-09-12",
            "2026-09-22",
            "2025-12-01",
            "2025-01-01",
            "invalid",
            null,
        ).filterNotNull()

        val months = Periods.options(Periods.MONTH, dates)
        val quarters = Periods.options(Periods.QUARTER, dates)
        val years = Periods.options(Periods.YEAR, dates)

        assertEquals("2026-10" to 1, months.first().id to months.first().count)
        assertEquals("2026-09" to 2, months[1].id to months[1].count)
        assertEquals("2026-Q4" to 1, quarters.first().id to quarters.first().count)
        assertEquals("2026-Q3" to 2, quarters[1].id to quarters[1].count)
        assertEquals("2026" to 3, years.first().id to years.first().count)
    }

    @Test
    fun matchesOnlyModelsReleasedInsidePeriod() {
        assertTrue(Periods.matches(Periods.MONTH, "2026-09", "2026-09-22"))
        assertFalse(Periods.matches(Periods.MONTH, "2026-09", "2026-10-01"))
        assertTrue(Periods.matches(Periods.QUARTER, "2026-Q3", "2026-09-22"))
        assertFalse(Periods.matches(Periods.QUARTER, "2026-Q3", "2026-10-01"))
        assertTrue(Periods.matches(Periods.YEAR, "2026", "2026-01-01"))
        assertFalse(Periods.matches(Periods.YEAR, "2026", "2025-01-01"))
    }

    @Test
    fun createsReadableChineseLabels() {
        assertEquals("2026年10月", Periods.label(Periods.MONTH, "2026-10"))
        assertEquals("2026 年第 3 季度", Periods.label(Periods.QUARTER, "2026-Q3"))
        assertEquals("2026年", Periods.label(Periods.YEAR, "2026"))
    }
}
