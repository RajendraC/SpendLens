package com.spendlens.core

/** One message as read from the SMS inbox. Only id, sender, text and time are ever read. */
data class SmsMessage(val id: Long, val address: String, val body: String, val timestamp: Long)

enum class Direction { DEBIT, CREDIT }

/** Hints the parser picks up from the text; the categorizer uses them. */
enum class TxKind { NORMAL, CASH_WITHDRAWAL, CARD_BILL_PAYMENT, SALARY, REFUND, INVESTMENT }

enum class RejectReason {
    PERSONAL_SENDER, PROMOTIONAL, GOVERNMENT, OTP, FUTURE_OR_REMINDER, REQUEST, FAILED, NO_AMOUNT, NOT_A_TRANSACTION
}

data class ParsedTransaction(
    val smsId: Long,
    val sender: String,
    val senderGroup: String,          // institution id, or Institutions.UNRECOGNISED
    val institutionName: String?,
    val timestamp: Long,
    val amountPaise: Long,
    val direction: Direction,
    val accountLast4: String?,
    val isCard: Boolean,
    val merchantRaw: String?,
    val vpa: String?,
    val reference: String?,
    val balancePaise: Long?,
    val kind: TxKind,
    val confidence: Double,
    val body: String,
) {
    /** Low-confidence parses wait in Needs review instead of going straight to the dashboard. */
    val needsReview: Boolean get() = confidence < 0.75
}

sealed class ParseResult {
    data class Accepted(val tx: ParsedTransaction) : ParseResult()
    data class Rejected(val reason: RejectReason) : ParseResult()
}

/**
 * Turns a raw SMS into a transaction, or explains why it was skipped.
 * Pure Kotlin with no Android imports, so every format is pinned down by fast JVM unit tests.
 */
object SmsParser {

    private const val NUM = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val CURRENCY_AMOUNT = Regex("""(?i)(?:rs\.?|inr|₹)\s*$NUM""")
    private val BARE_AMOUNT = Regex("""(?i)\b(?:debited|credited|deducted)\s+(?:by|for|with)?\s*(?:rs\.?|inr|₹)?\s*$NUM""")
    private val BALANCE_LEAD = Regex("""(?i)(?:avl\.?\s*bal(?:ance)?|available\s+bal(?:ance)?|bal(?:ance)?\s*[:\-]|avl\.?\s*l(?:i)?m(?:i)?t|available\s+limit|avbl\s*bal)\W{0,6}$""")
    private val BALANCE = Regex("""(?i)(?:avl\.?\s*bal(?:ance)?|available\s+bal(?:ance)?|avl\.?\s*l(?:i)?m(?:i)?t|available\s+limit|avbl\s*bal|\bbal)\W{0,6}(?:is\s+)?(?:rs\.?|inr|₹)?\s*$NUM""")

    private val OTP = Regex("""(?i)\b(otp|one[\s-]time\s+password|verification\s+code|security\s+code)\b""")
    private val FUTURE = Regex("""(?i)\bwill\s+be\s+(debited|deducted|charged|credited|auto[\s-]?debited|reversed|refunded)\b|\bscheduled\s+(?:to\s+be\s+)?debit""")
    private val REQUEST = Regex("""(?i)\b(requested\s+money|has\s+requested|collect\s+request|is\s+requesting|payment\s+request|request\s+of\s+(?:rs|inr|₹))""")
    private val FAILED = Regex("""(?i)\b(failed|declined|unsuccessful|could\s+not\s+be\s+processed|was\s+not\s+successful)\b""")
    private val REFUND_WORDS = Regex("""(?i)\b(refund(?:ed)?|reversed|reversal|credited\s+back)\b""")
    private val PROMO = Regex("""(?i)\b(pre[\s-]?approved|apply\s+now|offer|eligible\s+for|get\s+up\s*to|limited\s+period|click\s+here\s+to\s+avail)\b""")

    // Past-tense verbs only: a message has to describe money that already moved.
    private val DEBIT_VERB = Regex("""(?i)\b(debited|spent|paid|sent|withdrawn|deducted|purchase\s+of|transferred\s+to|trf\s+to|charged)\b""")
    private val CREDIT_VERB = Regex("""(?i)\b(credited|received|deposited|refund(?:ed)?|reversed|cashback\s+of)\b""")

    private val ACCOUNT = Regex("""(?i)\b(?:a/c|acct|account|ac)\b\.?\s*(?:no\.?|number)?\s*[:\-]?\s*(?:ending\s+(?:with\s+|in\s+)?)?[x*#]*\s*([0-9]{3,6})""")
    private val ACCOUNT_TIGHT = Regex("""(?i)\b(?:a/c|acct|account|ac)([x*]+[0-9]{3,6})""")
    private val CARD = Regex("""(?i)\bcard\b\s*(?:no\.?|number)?\s*(?:ending\s+(?:with\s+|in\s+)?)?[x*#]*\s*([0-9]{4})\b""")
    private val VPA = Regex("""\b([A-Za-z0-9][A-Za-z0-9._\-]{1,}@[A-Za-z]{2,})\b""")
    private val REFERENCE = Regex("""(?i)(?:upi\s*ref(?:erence)?(?:\s*no)?|ref(?:erence)?\s*(?:no|number|id)?|refno|rrn|utr|txn\s*id|upi)\s*[:.#\-]?\s*([0-9]{6,})""")
    private val UPI_PATH = Regex("""(?i)\bupi/p2[am]/([0-9]{6,})/([^/\n]+)""")

    private val ATM = Regex("""(?i)\b(atm|cash\s+withdrawal|withdrawn\s+at|cash\s+wdl)\b""")
    private val CARD_BILL = Regex("""(?i)(credit\s*card\s*(?:bill|payment|dues)|card\s*bill|cc\s*(?:bill|payment)|received\s+towards\s+your\s+.{0,30}card|payment\s+.{0,40}received\s+(?:on|towards|for)\s+.{0,30}card|cardbill)""")
    private val SALARY = Regex("""(?i)\b(salary|sal\s+cr|payroll)\b""")
    private val INVEST = Regex("""(?i)\b(sip|mutual\s+fund|nach|ach\s+dr|systematic\s+investment)\b""")

    fun parse(sms: SmsMessage): ParseResult {
        val header = SenderParser.parse(sms.address) ?: return reject(RejectReason.PERSONAL_SENDER)
        when (header.type) {
            HeaderType.PROMOTIONAL -> return reject(RejectReason.PROMOTIONAL)
            HeaderType.GOVERNMENT -> return reject(RejectReason.GOVERNMENT)
            else -> Unit
        }
        val body = sms.body.trim()
        val institution = Institutions.forEntity(header.entity)

        if (FUTURE.containsMatchIn(body)) return reject(RejectReason.FUTURE_OR_REMINDER)
        if (REQUEST.containsMatchIn(body)) return reject(RejectReason.REQUEST)

        val debitAt = DEBIT_VERB.find(body)?.range?.first
        val creditAt = CREDIT_VERB.find(body)?.range?.first
        val hasCompletedVerb = debitAt != null || creditAt != null

        if (OTP.containsMatchIn(body) && !hasCompletedVerb) return reject(RejectReason.OTP)
        if (FAILED.containsMatchIn(body) && !REFUND_WORDS.containsMatchIn(body)) return reject(RejectReason.FAILED)

        val amount = findAmount(body) ?: return reject(RejectReason.NO_AMOUNT)
        if (!hasCompletedVerb) {
            return reject(if (PROMO.containsMatchIn(body)) RejectReason.PROMOTIONAL else RejectReason.NOT_A_TRANSACTION)
        }

        // The earliest verb decides: "Acct XX710 debited for Rs 1,240; ZEPTO credited." is a debit.
        val direction = when {
            debitAt == null -> Direction.CREDIT
            creditAt == null -> Direction.DEBIT
            else -> if (debitAt <= creditAt) Direction.DEBIT else Direction.CREDIT
        }

        val cardMatch = CARD.find(body)
        val accountMatch = ACCOUNT_TIGHT.find(body)?.let { m -> m.groupValues[1].filter { it.isDigit() } }
            ?: ACCOUNT.find(body)?.groupValues?.get(1)
        val isCard = cardMatch != null && (accountMatch == null || cardMatch.range.first < (ACCOUNT.find(body)?.range?.first ?: Int.MAX_VALUE))
        val last4 = (if (isCard) cardMatch?.groupValues?.get(1) else accountMatch ?: cardMatch?.groupValues?.get(1))?.takeLast(4)

        val upiPath = UPI_PATH.find(body)
        val vpa = VPA.findAll(body).map { it.groupValues[1] }.firstOrNull { !it.contains("..") }
        val reference = upiPath?.groupValues?.get(1) ?: REFERENCE.find(body)?.groupValues?.get(1)
        val merchant = cleanMerchant(upiPath?.groupValues?.get(2) ?: extractMerchant(body, direction))
            ?: vpa?.substringBefore('@')?.let { cleanMerchant(it) }

        val kind = when {
            ATM.containsMatchIn(body) && direction == Direction.DEBIT -> TxKind.CASH_WITHDRAWAL
            CARD_BILL.containsMatchIn(body) -> TxKind.CARD_BILL_PAYMENT
            direction == Direction.CREDIT && REFUND_WORDS.containsMatchIn(body) -> TxKind.REFUND
            direction == Direction.CREDIT && SALARY.containsMatchIn(body) -> TxKind.SALARY
            direction == Direction.DEBIT && INVEST.containsMatchIn(body) -> TxKind.INVESTMENT
            else -> TxKind.NORMAL
        }

        var confidence = 0.45
        if (last4 != null) confidence += 0.2
        if (merchant != null || kind == TxKind.CASH_WITHDRAWAL || kind == TxKind.CARD_BILL_PAYMENT) confidence += 0.2
        if (reference != null || isCard) confidence += 0.1
        if (institution != null) confidence += 0.1
        if (header.type == HeaderType.UNMARKED && institution == null) confidence -= 0.2

        return ParseResult.Accepted(
            ParsedTransaction(
                smsId = sms.id,
                sender = sms.address,
                senderGroup = institution?.id ?: Institutions.UNRECOGNISED,
                institutionName = institution?.name,
                timestamp = sms.timestamp,
                amountPaise = amount,
                direction = direction,
                accountLast4 = last4,
                isCard = isCard,
                merchantRaw = merchant,
                vpa = vpa,
                reference = reference,
                balancePaise = BALANCE.find(body)?.groupValues?.get(1)?.let { Money.parseRupeesToPaise(it) },
                kind = kind,
                confidence = confidence.coerceIn(0.0, 1.0),
                body = body,
            )
        )
    }

    private fun reject(r: RejectReason) = ParseResult.Rejected(r)

    /** First currency amount that is not a balance or limit. */
    internal fun findAmount(body: String): Long? {
        for (m in CURRENCY_AMOUNT.findAll(body)) {
            val before = body.substring(maxOf(0, m.range.first - 24), m.range.first)
            if (BALANCE_LEAD.containsMatchIn(before)) continue
            val v = Money.parseRupeesToPaise(m.groupValues[1]) ?: continue
            if (v > 0) return v
        }
        BARE_AMOUNT.find(body)?.let { m -> Money.parseRupeesToPaise(m.groupValues[1])?.let { if (it > 0) return it } }
        return null
    }

    private val TERMINATORS = """(?=\s*(?:\n|$|\bon\b|\bref\b|\brefno\b|\bupi\b|\bvia\b|\bavl\b|\bavbl\b|\bbal\b|\bfrom\b|\bthru\b|\busing\b|\bcredited\b|\bdebited\b|\(|\.\s|\.$|;|,\s|\bif\b|\bnot\s+you\b|-\s))"""
    private val MERCHANT_PATTERNS_DEBIT = listOf(
        Regex("""(?i)\btrf\s+to\s+(.{2,40}?)$TERMINATORS"""),
        Regex("""(?i);\s*(.{2,40}?)\s+credited\b"""),
        Regex("""(?i)\bat\s+(.{2,40}?)$TERMINATORS"""),
        Regex("""(?i)\bon\s+\d{1,2}[-/][A-Za-z0-9]{2,3}[-/]\d{2,4}\s+on\s+(.{2,40}?)$TERMINATORS"""),
        Regex("""(?i)\bto\s+(?:vpa\s+)?(.{2,40}?)$TERMINATORS"""),
        Regex("""(?i)\btowards\s+(.{2,40}?)$TERMINATORS"""),
    )
    private val MERCHANT_PATTERNS_CREDIT = listOf(
        Regex("""(?i)\brefund\s+of\s+(?:rs\.?|inr|₹)\s*[0-9,.]+\s+from\s+(.{2,40}?)$TERMINATORS"""),
        Regex("""(?i)\bfrom\s+(?:vpa\s+)?(.{2,40}?)$TERMINATORS"""),
        Regex("""(?i)\bby\s+(?:neft|imps|rtgs|upi)?\s*(?:from\s+)?(.{2,40}?)$TERMINATORS"""),
    )

    private fun extractMerchant(body: String, direction: Direction): String? {
        val patterns = if (direction == Direction.DEBIT) MERCHANT_PATTERNS_DEBIT else MERCHANT_PATTERNS_CREDIT
        for (p in patterns) {
            val m = p.find(body) ?: continue
            val candidate = m.groupValues[1].trim()
            if (looksLikeAccount(candidate)) continue
            return candidate
        }
        return null
    }

    private val ACCOUNTISH = Regex("""(?i)^(?:your\s+)?(?:hdfc|icici|sbi|axis|kotak)?\s*(?:bank\s+)?(?:a/c|acct|account|ac|card|credit\s+card|debit\s+card)\b""")
    private fun looksLikeAccount(s: String): Boolean =
        ACCOUNTISH.containsMatchIn(s) || s.all { it.isDigit() || it == 'X' || it == 'x' || it == '*' || it == ' ' } || s.lowercase().startsWith("your ")

    private val NOISE = Regex("""(?i)\b(pvt|private|ltd|limited|llp|india|technologies|tech|services|systems|solutions|enterprises?|corporation|corp|retail|payments?|online)\b\.?""")

    /** "SWIGGY*BLR", "VPA swiggy@icici", "UBER INDIA SYSTEMS PVT LTD" -> "Swiggy", "Uber". */
    fun cleanMerchant(raw: String?): String? {
        if (raw == null) return null
        var s = raw.replace('\n', ' ').trim().trim('.', ',', ';', ':', '-', '*')
        s = s.replace(Regex("""(?i)^vpa\s+"""), "")
        if (s.contains('@')) s = s.substringBefore('@')
        s = s.replace(Regex("""[*_]+"""), " ").replace(Regex("""\s+"""), " ").trim()
        val withoutNoise = s.replace(NOISE, "").replace(Regex("""\s+"""), " ").trim()
        if (withoutNoise.length >= 2) s = withoutNoise
        if (s.length < 2 || s.all { it.isDigit() }) return null
        return s.split(' ').filter { it.isNotBlank() }.joinToString(" ") { w ->
            if (w.length <= 3 && w.all { it.isUpperCase() || it.isDigit() } && w.any { it.isLetter() } && w.length > 1 && !w.contains('.')) w
            else w.lowercase().replaceFirstChar { it.uppercase() }
        }.take(40)
    }
}
