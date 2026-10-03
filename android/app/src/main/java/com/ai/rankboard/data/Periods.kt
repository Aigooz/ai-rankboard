package com.ai.rankboard.data

data class PeriodOption(
    val id: String,
    val label: String,
    val count: Int,
)

object Periods {
    const val MONTH = "period-month"
    const val QUARTER = "period-quarter"
    const val YEAR = "period-year"

    val supported = setOf(MONTH, QUARTER, YEAR)

    fun options(dimension: String, releaseDates: List<String>): List<PeriodOption> {
        val grouped = releaseDates
            .mapNotNull { periodId(dimension, it) }
            .groupingBy { it }
            .eachCount()

        return grouped
            .map { (id, count) -> PeriodOption(id, label(dimension, id), count) }
            .sortedByDescending { it.id }
    }

    fun periodId(dimension: String, releaseDate: String?): String? {
        val date = releaseDate?.trim().orEmpty()
        val match = Regex("^\\d{4}-\\d{2}(-\\d{2})?").find(date) ?: return null
        val normalized = match.value
        return when (dimension) {
            MONTH -> normalized.take(7)
            QUARTER -> normalized.take(4) + "-Q" + quarter(normalized)
            YEAR -> normalized.take(4)
            else -> null
        }
    }

    fun matches(dimension: String, periodId: String, releaseDate: String?): Boolean =
        periodId(dimension, releaseDate) == periodId

    fun label(dimension: String, periodId: String): String = when (dimension) {
        MONTH -> {
            val parts = periodId.split("-")
            if (parts.size == 2) "${parts[0]}年${parts[1].toIntOrNull() ?: parts[1]}月" else periodId
        }
        QUARTER -> {
            val parts = periodId.split("-Q")
            if (parts.size == 2) "${parts[0]} 年第 ${parts[1]} 季度" else periodId
        }
        YEAR -> if (periodId.matches(Regex("\\d{4}"))) "${periodId}年" else periodId
        else -> periodId
    }

    private fun quarter(date: String): Int = ((date.substring(5, 7).toIntOrNull() ?: 1) + 2) / 3
}
