package com.spendlens.core

enum class RuleMatch { MERCHANT, VPA, KEYWORD }

/** A user's "always use Food for Swiggy". User rules always beat the built-in defaults. */
data class CategoryRule(val match: RuleMatch, val pattern: String, val categoryKey: String)

class Categorizer(private val userRules: List<CategoryRule> = emptyList()) {

    fun categorize(tx: ParsedTransaction, merchantKey: String, known: KnownMerchant?): String =
        categorize(merchantKey, tx.merchantRaw, tx.vpa, tx.direction, tx.kind, known, tx.body)

    fun categorize(
        merchantKey: String,
        merchantName: String?,
        vpa: String?,
        direction: Direction,
        kind: TxKind,
        known: KnownMerchant?,
        body: String = "",
    ): String {
        userRules.firstOrNull { r ->
            when (r.match) {
                RuleMatch.MERCHANT -> r.pattern.equals(merchantKey, ignoreCase = true)
                RuleMatch.VPA -> vpa != null && r.pattern.equals(vpa, ignoreCase = true)
                RuleMatch.KEYWORD -> listOfNotNull(merchantName, vpa).joinToString(" ").contains(r.pattern, ignoreCase = true)
            }
        }?.let { return it.categoryKey }

        when (kind) {
            TxKind.CASH_WITHDRAWAL -> return Cat.CASH
            TxKind.CARD_BILL_PAYMENT -> return Cat.TRANSFERS
            TxKind.SALARY -> return Cat.INCOME
            TxKind.INVESTMENT -> return Cat.INVESTMENTS
            TxKind.REFUND -> return known?.category ?: keywordCategory(merchantName, vpa, body) ?: Cat.OTHERS
            TxKind.NORMAL -> Unit
        }
        known?.let { return it.category }
        keywordCategory(merchantName, vpa, body)?.let { return it }
        return if (direction == Direction.CREDIT) Cat.INCOME else Cat.OTHERS
    }

    private fun keywordCategory(merchantName: String?, vpa: String?, body: String): String? {
        val hay = listOfNotNull(merchantName, vpa).joinToString(" ").lowercase()
        if (hay.isNotBlank()) KEYWORDS.firstOrNull { (re, _) -> re.containsMatchIn(hay) }?.let { return it.second }
        val b = body.lowercase()
        return BODY_KEYWORDS.firstOrNull { (re, _) -> re.containsMatchIn(b) }?.second
    }

    companion object {
        private fun k(pattern: String, cat: String) = Regex(pattern) to cat

        private val KEYWORDS = listOf(
            k("""petrol|fuel|hpcl|bpcl|iocl|indian ?oil|bharat petroleum|hindustan petroleum|\bshell\b|filling station""", Cat.FUEL),
            k("""electricity|\bpower\b|bescom|tneb|tangedco|msedcl|bses|tata power|water board|\bgas\b|bbps|billdesk|broadband|fibernet|act fibernet""", Cat.BILLS),
            k("""recharge|prepaid|postpaid|vodafone|\bvi\b|dth|tata play|sun direct""", Cat.RECHARGE),
            k("""pharma|medical|hospital|clinic|medplus|diagnostic|\blabs?\b|dental|chemist""", Cat.HEALTH),
            k("""restaurant|\bcafe\b|coffee|bakery|bhavan|biryani|pizza|burger|dhaba|sweets|kitchen|\bfoods?\b|canteen|chai|tea stall|hotel""", Cat.FOOD),
            k("""\bmart\b|grocery|groceries|supermarket|kirana|\bfresh\b|dmart|more retail|spencer|provision""", Cat.GROCERIES),
            k("""metro|railway|\bbus\b|\bcab\b|taxi|\bauto\b|fastag|parking|\btoll\b""", Cat.TRANSPORT),
            k("""airline|indigo|air india|vistara|akasa|spicejet|\boyo\b|resort|travels""", Cat.TRAVEL),
            k("""school|college|university|tuition|academy|course|udemy|coursera""", Cat.EDUCATION),
            k("""\brent\b|landlord|housing society|maintenance""", Cat.RENT),
            k("""\bemi\b|\bloan\b|bajaj fin|hdb financial|home credit""", Cat.EMI),
            k("""\bsip\b|mutual fund|zerodha|groww|upstox|\bnps\b|\bppf\b|indian clearing""", Cat.INVESTMENTS),
            k("""cinema|\bpvr\b|inox|movies|theatre|gaming""", Cat.ENTERTAINMENT),
            k("""store|retail|fashion|lifestyle|decathlon|croma|reliance digital|trends|shoppers""", Cat.SHOPPING),
        )
        private val BODY_KEYWORDS = listOf(
            k("""\bemi\b""", Cat.EMI),
            k("""\bsip\b|mutual fund""", Cat.INVESTMENTS),
            k("""\brent\b""", Cat.RENT),
        )
    }
}

/**
 * Drops the second copy of the same payment: banks and UPI apps often both send an SMS.
 * Two messages are the same payment when their references match, or (without references) when
 * amount and direction match within five minutes and the account numbers don't contradict.
 */
class Deduper(existing: Collection<DedupeEntry> = emptyList()) {

    data class DedupeEntry(
        val amountPaise: Long,
        val direction: Direction,
        val timestamp: Long,
        val last4: String?,
        val reference: String?,
    )

    private val seen = ArrayList(existing)

    fun isDuplicate(e: DedupeEntry): Boolean = seen.any { s -> same(s, e) }

    /** Returns true if [e] is new (and remembers it), false if it duplicates something seen. */
    fun offer(e: DedupeEntry): Boolean {
        if (isDuplicate(e)) return false
        seen.add(e)
        return true
    }

    private fun same(a: DedupeEntry, b: DedupeEntry): Boolean {
        if (a.reference != null && b.reference != null) return a.reference == b.reference
        if (a.amountPaise != b.amountPaise || a.direction != b.direction) return false
        if (kotlin.math.abs(a.timestamp - b.timestamp) > WINDOW_MS) return false
        return a.last4 == null || b.last4 == null || a.last4 == b.last4
    }

    companion object {
        const val WINDOW_MS = 5 * 60_000L
        fun entryOf(t: ParsedTransaction) = DedupeEntry(t.amountPaise, t.direction, t.timestamp, t.accountLast4, t.reference)
    }
}
