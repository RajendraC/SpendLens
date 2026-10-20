package com.spendlens.core

/**
 * Money is always held as whole paise in a Long. Never use floating point for amounts.
 */
object Money {

    /** Parses "1,23,456.78" or "2180.0" into paise. Returns null for anything that is not a plain amount. */
    fun parseRupeesToPaise(text: String): Long? {
        val clean = text.replace(",", "").trim()
        if (clean.isEmpty() || clean.startsWith(".")) return null
        val parts = clean.split('.')
        if (parts.size > 2) return null
        if (!parts[0].all { it.isDigit() }) return null
        val rupees = parts[0].toLongOrNull() ?: return null
        val paise = if (parts.size == 2) {
            val frac = parts[1]
            if (frac.isEmpty() || !frac.all { it.isDigit() }) return null
            frac.padEnd(2, '0').take(2).toLong()
        } else 0L
        return rupees * 100 + paise
    }

    /** Indian digit grouping: 12345678 paise -> "₹1,23,457" (or "₹1,23,456.78" with paise). */
    fun formatInr(paise: Long, showPaise: Boolean = false): String {
        val negative = paise < 0
        val abs = kotlin.math.abs(paise)
        val sign = if (negative) "-" else ""
        return if (showPaise) {
            val rupees = abs / 100
            val p = abs % 100
            "$sign₹${group(rupees)}.${p.toString().padStart(2, '0')}"
        } else {
            val rupees = (abs + 50) / 100
            "$sign₹${group(rupees)}"
        }
    }

    /** Short form for tight spaces: ₹950, ₹12.4k, ₹1.2L. */
    fun formatShort(paise: Long): String {
        val rupees = paise / 100.0
        val a = kotlin.math.abs(rupees)
        val sign = if (rupees < 0) "-" else ""
        return when {
            a >= 1_00_000 -> sign + "₹" + oneDecimal(a / 1_00_000) + "L"
            a >= 1_000 -> sign + "₹" + oneDecimal(a / 1_000) + "k"
            else -> formatInr(paise)
        }
    }

    private fun oneDecimal(v: Double): String {
        val tenths = kotlin.math.round(v * 10).toLong()
        val whole = tenths / 10
        val frac = tenths % 10
        return if (frac == 0L) whole.toString() else "$whole.$frac"
    }

    private fun group(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last3
    }
}
