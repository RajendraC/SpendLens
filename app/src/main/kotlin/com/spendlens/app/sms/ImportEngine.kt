package com.spendlens.app.sms

import com.spendlens.app.data.NewTxn
import com.spendlens.app.data.Repository
import com.spendlens.app.data.Source
import com.spendlens.core.Categorizer
import com.spendlens.core.Deduper
import com.spendlens.core.Direction
import com.spendlens.core.ImportFilters
import com.spendlens.core.Institutions
import com.spendlens.core.KnownMerchant
import com.spendlens.core.MerchantRegistry
import com.spendlens.core.ParseResult
import com.spendlens.core.ParsedTransaction
import com.spendlens.core.RejectReason
import com.spendlens.core.SmsParser
import com.spendlens.core.TxKind
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Reads the inbox, keeps only bank/card/UPI transactions, applies the user's import choices,
 * removes duplicates and writes the result. Personal messages and OTPs are dropped in memory and
 * never stored.
 */
class ImportEngine(
    private val sms: SmsSource,
    private val repo: Repository,
    private val clock: Clock,
) {
    private val zone: ZoneId get() = clock.zone

    data class Candidate(
        val tx: ParsedTransaction,
        val known: KnownMerchant?,
        val merchantName: String,
        val merchantKey: String,
    ) {
        val smsId: Long get() = tx.smsId
    }

    data class SenderGroup(val id: String, val name: String, val detail: String)

    /**
     * A message the parser left out, kept in memory only for the "See what was skipped" list.
     * Messages from people keep neither sender nor text, and OTPs keep no text.
     */
    data class Skipped(val sender: String?, val reason: RejectReason, val body: String?, val timestamp: Long)

    /** One read of the inbox covering the longest range the user can pick (12 months). */
    data class Scan(
        val candidates: List<Candidate>,   // newest first
        val messagesRead: List<Long>,      // timestamps of every message read, for "ignored" counts
        val groups: List<SenderGroup>,
        val skipped: List<Skipped> = emptyList(),  // newest first
    )

    suspend fun scan(months: Int = MAX_MONTHS): Scan {
        val since = LocalDate.now(clock).minusMonths(months.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val messages = sms.read(since)
        val candidates = ArrayList<Candidate>()
        val skipped = ArrayList<Skipped>()
        for (m in messages) {
            when (val r = SmsParser.parse(m)) {
                is ParseResult.Accepted -> candidates += toCandidate(r.tx)
                is ParseResult.Rejected -> skipped += Skipped(
                    sender = if (r.reason == RejectReason.PERSONAL_SENDER) null else m.address,
                    reason = r.reason,
                    body = if (r.reason == RejectReason.PERSONAL_SENDER || r.reason == RejectReason.OTP) null else m.body,
                    timestamp = m.timestamp,
                )
            }
        }
        candidates.sortByDescending { it.tx.timestamp }
        skipped.sortByDescending { it.timestamp }
        val groups = candidates.groupBy { it.tx.senderGroup }.map { (id, list) ->
            val name = list.first().tx.institutionName ?: "Unrecognised senders"
            val accounts = list.mapNotNull { c -> c.tx.accountLast4?.let { (if (c.tx.isCard) "Card ••" else "A/c ••") + it } }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(2).map { it.key }
            val headers = list.map { it.tx.sender }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            val detail = if (id == Institutions.UNRECOGNISED) "${headers.size} senders · may not be transactions"
            else (accounts + listOfNotNull(headers.firstOrNull()?.key)).joinToString(" · ")
            SenderGroup(id, name, detail)
        }.sortedWith(compareBy<SenderGroup> { it.id == Institutions.UNRECOGNISED }.thenByDescending { g -> candidates.count { it.tx.senderGroup == g.id } })
        return Scan(candidates, messages.map { it.timestamp }, groups, skipped)
    }

    fun toCandidate(t: ParsedTransaction): Candidate {
        val known = MerchantRegistry.match(t.merchantRaw, t.vpa)
        val name = known?.displayName
            ?: t.merchantRaw
            ?: when (t.kind) {
                TxKind.CASH_WITHDRAWAL -> "ATM withdrawal"
                TxKind.CARD_BILL_PAYMENT -> "Card bill payment"
                TxKind.SALARY -> "Salary"
                else -> t.institutionName ?: "Unknown"
            }
        return Candidate(t, known, name, MerchantRegistry.keyFor(known, t.merchantRaw ?: name, t.vpa))
    }

    fun rangeStart(filters: ImportFilters): Long =
        filters.startDate(LocalDate.now(clock)).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Candidates that pass the filters and the date range, newest first. */
    fun selectable(scan: Scan, filters: ImportFilters): List<Candidate> {
        val from = rangeStart(filters)
        return scan.candidates.filter { it.tx.timestamp >= from && filters.accepts(it.tx) }
    }

    fun messagesInRange(scan: Scan, filters: ImportFilters, group: String): Int {
        val from = rangeStart(filters)
        return scan.candidates.count { it.tx.senderGroup == group && it.tx.timestamp >= from }
    }

    fun ignoredInRange(scan: Scan, filters: ImportFilters): Int {
        val from = rangeStart(filters)
        val read = scan.messagesRead.count { it >= from }
        val tx = scan.candidates.count { it.tx.timestamp >= from }
        return (read - tx).coerceAtLeast(0)
    }

    fun skippedInRange(scan: Scan, filters: ImportFilters): List<Skipped> {
        val from = rangeStart(filters)
        return scan.skipped.filter { it.timestamp >= from }
    }

    fun monthOf(c: Candidate): YearMonth = YearMonth.from(Instant.ofEpochMilli(c.tx.timestamp).atZone(zone))

    data class Result(val saved: Int, val needsReview: Int, val duplicates: Int, val leftOut: Int, val removed: Int, val byGroup: Map<String, Int>)

    /**
     * Writes the chosen candidates. With [replaceRange] (re-import from Settings), SMS transactions in the
     * range that are no longer chosen are removed first; transactions you edited and still chose are kept as they are.
     */
    suspend fun commit(
        chosen: List<Candidate>,
        filters: ImportFilters,
        leftOut: Int,
        replaceRange: Boolean,
        onProgress: (Float) -> Unit = {},
    ): Result {
        val from = rangeStart(filters)
        val removed = if (replaceRange) repo.removeSmsTransactionsNotIn(from, chosen.map { it.smsId }.toSet()) else 0
        val known = repo.existingSmsIds()
        val existing = repo.smsTransactionsSince(from - Deduper.WINDOW_MS)
        val deduper = Deduper(existing.map { Deduper.DedupeEntry(it.amountPaise, it.direction, it.occurredAt, it.accountLabel?.filter { ch -> ch.isDigit() }?.takeLast(4), it.refNo) })
        val categorizer = Categorizer(repo.rules())

        var duplicates = 0
        val rows = ArrayList<NewTxn>()
        // Oldest first, so the first copy of a duplicated payment is the one kept.
        for (c in chosen.sortedBy { it.tx.timestamp }) {
            if (c.smsId in known) continue
            if (!deduper.offer(Deduper.entryOf(c.tx))) { duplicates++; continue }
            rows += c.toNewTxn(categorizer)
        }
        var saved = 0
        val chunks = rows.chunked(100)
        chunks.forEachIndexed { i, chunk ->
            saved += repo.insertAll(chunk)
            onProgress((i + 1).toFloat() / chunks.size)
        }
        if (chunks.isEmpty()) onProgress(1f)

        val latest = chosen.maxOfOrNull { it.tx.timestamp } ?: 0L
        repo.updateSettings { s ->
            s.copy(
                filters = filters,
                smsEnabled = true,
                lastSyncAt = clock.millis(),
                lastSmsTimestamp = maxOf(s.lastSmsTimestamp, latest, clock.millis() - 60_000),
            )
        }
        return Result(
            saved = saved,
            needsReview = rows.count { it.needsReview },
            duplicates = duplicates,
            leftOut = leftOut,
            removed = removed,
            byGroup = rows.groupingBy { it.senderGroup ?: Institutions.UNRECOGNISED }.eachCount(),
        )
    }

    /**
     * Incremental sync with the saved filters: on app open, pull to refresh and every 6 hours.
     * Returns how many new transactions were added.
     */
    suspend fun syncNew(): Int {
        val s = repo.settings()
        if (!s.onboarded || !s.smsEnabled || !sms.hasPermission()) return 0
        val rangeFrom = rangeStart(s.filters)
        val since = maxOf(rangeFrom, s.lastSmsTimestamp - OVERLAP_MS)
        val messages = sms.read(since)
        val known = repo.existingSmsIds()
        val fresh = messages.filter { it.id !in known }
            .mapNotNull { (SmsParser.parse(it) as? ParseResult.Accepted)?.tx }
            .filter { s.filters.accepts(it) }
            .map { toCandidate(it) }
        var added = 0
        if (fresh.isNotEmpty()) {
            val existing = repo.smsTransactionsSince(since - Deduper.WINDOW_MS)
            val deduper = Deduper(existing.map { Deduper.DedupeEntry(it.amountPaise, it.direction, it.occurredAt, it.accountLabel?.filter { ch -> ch.isDigit() }?.takeLast(4), it.refNo) })
            val categorizer = Categorizer(repo.rules())
            val rows = fresh.sortedBy { it.tx.timestamp }.filter { deduper.offer(Deduper.entryOf(it.tx)) }.map { it.toNewTxn(categorizer) }
            added = repo.insertAll(rows)
        }
        val latest = messages.maxOfOrNull { it.timestamp } ?: s.lastSmsTimestamp
        repo.updateSettings { it.copy(lastSyncAt = clock.millis(), lastSmsTimestamp = maxOf(it.lastSmsTimestamp, latest)) }
        return added
    }

    private fun Candidate.toNewTxn(categorizer: Categorizer): NewTxn {
        val t = tx
        val account = t.accountLast4?.let { last4 ->
            val prefix = t.institutionName ?: "Account"
            if (t.isCard) "$prefix card ••$last4" else "$prefix ••$last4"
        }
        return NewTxn(
            amountPaise = t.amountPaise,
            direction = t.direction,
            occurredAt = t.timestamp,
            merchantName = merchantName,
            merchantKey = merchantKey,
            merchantPackage = known?.packageName,
            vpa = t.vpa,
            categoryId = categorizer.categorize(t, merchantKey, known),
            accountLabel = account,
            senderGroup = t.senderGroup,
            sender = t.sender,
            refNo = t.reference,
            source = Source.SMS,
            smsId = t.smsId,
            needsReview = t.needsReview,
            kind = t.kind,
            smsBody = t.body,
        )
    }

    companion object {
        const val MAX_MONTHS = 12
        private const val OVERLAP_MS = 24 * 60 * 60_000L

        /** A cash or other manual expense. */
        fun manual(amountPaise: Long, merchant: String, categoryId: String, occurredAt: Long, direction: Direction = Direction.DEBIT): NewTxn {
            val name = merchant.trim()
            return NewTxn(
                amountPaise = amountPaise, direction = direction, occurredAt = occurredAt, merchantName = name,
                merchantKey = MerchantRegistry.keyFor(null, name, null), merchantPackage = MerchantRegistry.match(name, null)?.packageName,
                vpa = null, categoryId = categoryId, accountLabel = "Cash", senderGroup = null, sender = null, refNo = null,
                source = Source.MANUAL, smsId = null, needsReview = false,
            )
        }
    }
}
