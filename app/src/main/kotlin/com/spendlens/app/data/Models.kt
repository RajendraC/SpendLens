package com.spendlens.app.data

import com.spendlens.core.Direction
import com.spendlens.core.ImportFilters
import com.spendlens.core.TxKind
import org.json.JSONArray
import org.json.JSONObject

enum class Source { SMS, MANUAL }

/** EXPENSE counts toward budgets. SAVING, TRANSFER and INCOME never do. */
enum class CategoryKind { EXPENSE, SAVING, TRANSFER, INCOME }

data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val color: Long,
    val kind: CategoryKind,
    val sortOrder: Int,
    val isSystem: Boolean,
    val archived: Boolean,
) {
    val countsAsSpend: Boolean get() = kind == CategoryKind.EXPENSE
}

data class Txn(
    val id: Long,
    val amountPaise: Long,
    val direction: Direction,
    val occurredAt: Long,
    val merchantName: String,
    val merchantKey: String,
    val merchantPackage: String?,
    val vpa: String?,
    val categoryId: String,
    val accountLabel: String?,
    val senderGroup: String?,
    val sender: String?,
    val refNo: String?,
    val source: Source,
    val smsId: Long?,
    val needsReview: Boolean,
    val excluded: Boolean,
    val note: String?,
    val kind: TxKind,
    val smsBody: String?,
) {
    /** Spend contribution: debits add, credits (refunds) subtract. */
    val signedPaise: Long get() = if (direction == Direction.DEBIT) amountPaise else -amountPaise
}

/** A transaction about to be written; the database assigns the id. */
data class NewTxn(
    val amountPaise: Long,
    val direction: Direction,
    val occurredAt: Long,
    val merchantName: String,
    val merchantKey: String,
    val merchantPackage: String?,
    val vpa: String?,
    val categoryId: String,
    val accountLabel: String?,
    val senderGroup: String?,
    val sender: String?,
    val refNo: String?,
    val source: Source,
    val smsId: Long?,
    val needsReview: Boolean,
    val note: String? = null,
    val kind: TxKind = TxKind.NORMAL,
    val smsBody: String? = null,
    val excluded: Boolean = false,
)

data class StoredRule(val id: Long, val matchType: String, val pattern: String, val categoryId: String)

/** Everything that is not a transaction. Lives inside the encrypted database, not in plain preferences. */
data class AppSettings(
    val onboarded: Boolean = false,
    val smsEnabled: Boolean = false,
    val filters: ImportFilters = ImportFilters(),
    val budgetStartDay: Int = 1,
    val appLock: Boolean = false,
    val screenPrivacy: Boolean = true,
    val lastSyncAt: Long = 0,
    val lastSmsTimestamp: Long = 0,
) {
    fun toMap(): Map<String, String> = mapOf(
        "onboarded" to onboarded.toString(),
        "sms_enabled" to smsEnabled.toString(),
        "filters" to filtersToJson(filters),
        "budget_start_day" to budgetStartDay.toString(),
        "app_lock" to appLock.toString(),
        "screen_privacy" to screenPrivacy.toString(),
        "last_sync_at" to lastSyncAt.toString(),
        "last_sms_ts" to lastSmsTimestamp.toString(),
    )

    companion object {
        fun fromMap(m: Map<String, String>) = AppSettings(
            onboarded = m["onboarded"]?.toBoolean() ?: false,
            smsEnabled = m["sms_enabled"]?.toBoolean() ?: false,
            filters = m["filters"]?.let { runCatching { filtersFromJson(it) }.getOrNull() } ?: ImportFilters(),
            budgetStartDay = m["budget_start_day"]?.toIntOrNull() ?: 1,
            appLock = m["app_lock"]?.toBoolean() ?: false,
            screenPrivacy = m["screen_privacy"]?.toBoolean() ?: true,
            lastSyncAt = m["last_sync_at"]?.toLongOrNull() ?: 0,
            lastSmsTimestamp = m["last_sms_ts"]?.toLongOrNull() ?: 0,
        )

        fun filtersToJson(f: ImportFilters): String = JSONObject()
            .put("range", f.rangeMonths)
            .put("disabled", JSONArray(f.disabledSenders.toList()))
            .put("out", f.includeOut)
            .put("in", f.includeIn)
            .put("min", f.minAmountPaise)
            .put("words", JSONArray(f.skipWords))
            .toString()

        fun filtersFromJson(s: String): ImportFilters {
            val o = JSONObject(s)
            val disabled = o.optJSONArray("disabled")
            val words = o.optJSONArray("words")
            return ImportFilters(
                rangeMonths = o.optInt("range", 6),
                disabledSenders = (0 until (disabled?.length() ?: 0)).map { disabled!!.getString(it) }.toSet(),
                includeOut = o.optBoolean("out", true),
                includeIn = o.optBoolean("in", true),
                minAmountPaise = o.optLong("min", 0),
                skipWords = (0 until (words?.length() ?: 0)).map { words!!.getString(it) },
            )
        }
    }
}
