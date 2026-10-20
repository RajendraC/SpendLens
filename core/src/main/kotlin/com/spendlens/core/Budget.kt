package com.spendlens.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What the user picked on the "Choose what to import" screen. Also used for every later sync. */
data class ImportFilters(
    val rangeMonths: Int = 6,
    val disabledSenders: Set<String> = setOf(Institutions.UNRECOGNISED),
    val includeOut: Boolean = true,
    val includeIn: Boolean = true,
    val minAmountPaise: Long = 0,
    val skipWords: List<String> = emptyList(),
) {
    fun senderEnabled(group: String) = group !in disabledSenders

    fun accepts(t: ParsedTransaction): Boolean {
        if (!senderEnabled(t.senderGroup)) return false
        if (t.direction == Direction.DEBIT && !includeOut) return false
        if (t.direction == Direction.CREDIT && !includeIn) return false
        if (t.direction == Direction.DEBIT && t.amountPaise < minAmountPaise) return false
        if (skipWords.isNotEmpty()) {
            val hay = (listOfNotNull(t.merchantRaw, t.vpa).joinToString(" ") + " " + t.body).lowercase()
            if (skipWords.any { w -> w.isNotBlank() && hay.contains(w.trim().lowercase()) }) return false
        }
        return true
    }

    fun startDate(today: LocalDate): LocalDate = today.minusMonths(rangeMonths.toLong())
}

enum class BudgetState { ON_TRACK, HEADS_UP, OVER }

data class BudgetStatus(
    val state: BudgetState,
    val spentPaise: Long,
    val limitPaise: Long,
    val fraction: Double,
    val projectedPaise: Long,
    val remainingPaise: Long,
    val safePerDayPaise: Long,
    val daysLeft: Int,
    val paceFraction: Double,
)

/** A budget month. It can start on a salary day (say the 25th) instead of the 1st. */
data class BudgetPeriod(val start: LocalDate, val endExclusive: LocalDate) {
    val days: Int get() = ChronoUnit.DAYS.between(start, endExclusive).toInt()
    fun dayNumber(today: LocalDate): Int = (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceIn(1, days)
    fun contains(d: LocalDate) = !d.isBefore(start) && d.isBefore(endExclusive)
    fun previous(startDay: Int) = BudgetMath.periodFor(start.minusDays(1), startDay)
}

object BudgetMath {

    fun periodFor(date: LocalDate, startDay: Int): BudgetPeriod {
        val sd = startDay.coerceIn(1, 28)
        val start = if (date.dayOfMonth >= sd) date.withDayOfMonth(sd) else date.minusMonths(1).withDayOfMonth(sd)
        return BudgetPeriod(start, start.plusMonths(1))
    }

    /**
     * Over: spent more than the limit. Heads-up: 75% used, or spending fast enough to finish over.
     * On track otherwise. Pace = how far through the month we are.
     */
    fun status(spentPaise: Long, limitPaise: Long, dayNumber: Int, daysInPeriod: Int): BudgetStatus {
        val elapsed = dayNumber.coerceIn(1, daysInPeriod)
        val daysLeft = (daysInPeriod - elapsed).coerceAtLeast(0)
        val fraction = if (limitPaise > 0) spentPaise.toDouble() / limitPaise else 0.0
        val projected = if (spentPaise <= 0) 0L else spentPaise * daysInPeriod / elapsed
        val remaining = limitPaise - spentPaise
        val state = when {
            limitPaise <= 0 -> BudgetState.ON_TRACK
            spentPaise > limitPaise -> BudgetState.OVER
            fraction >= 0.75 || projected > limitPaise -> BudgetState.HEADS_UP
            else -> BudgetState.ON_TRACK
        }
        return BudgetStatus(
            state = state,
            spentPaise = spentPaise,
            limitPaise = limitPaise,
            fraction = fraction,
            projectedPaise = projected,
            remainingPaise = remaining,
            safePerDayPaise = if (remaining > 0) remaining / maxOf(daysLeft, 1) else 0,
            daysLeft = daysLeft,
            paceFraction = elapsed.toDouble() / daysInPeriod,
        )
    }
}
