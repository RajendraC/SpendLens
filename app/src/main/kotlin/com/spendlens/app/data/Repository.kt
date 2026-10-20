package com.spendlens.app.data

import android.database.Cursor
import androidx.core.content.contentValuesOf
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spendlens.core.Cat
import com.spendlens.core.CategoryRule
import com.spendlens.core.Direction
import com.spendlens.core.RuleMatch
import com.spendlens.core.TxKind
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock

/**
 * All reads and writes go through here, on one background thread. Reads are exposed as Flows that
 * re-run whenever any write happens.
 */
class Repository(private val db: Db, private val clock: Clock) {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)

    private suspend fun <T> read(block: (SupportSQLiteDatabase) -> T): T = withContext(io) { block(db.get()) }

    private suspend fun <T> write(block: (SupportSQLiteDatabase) -> T): T {
        val r = withContext(io) { db.transaction(block) }
        db.notifyChanged()
        return r
    }

    private fun <T> observe(block: (SupportSQLiteDatabase) -> T): Flow<T> =
        db.changes.map { read(block) }.distinctUntilChanged()

    /** Opens the database once; throws if the encryption key can't be unlocked on this phone. */
    suspend fun open() = read { it.query("SELECT COUNT(*) FROM categories").use { c -> c.moveToFirst() } }

    // ---------------- settings ----------------

    fun observeSettings(): Flow<AppSettings> = observe { readSettings(it) }

    suspend fun settings(): AppSettings = read { readSettings(it) }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = write { d ->
        val next = transform(readSettings(d))
        next.toMap().forEach { (k, v) -> d.insert("settings", CONFLICT_REPLACE, contentValuesOf("key" to k, "value" to v)) }
    }

    private fun readSettings(d: SupportSQLiteDatabase): AppSettings {
        val m = HashMap<String, String>()
        d.query("SELECT key, value FROM settings").use { c -> while (c.moveToNext()) m[c.getString(0)] = c.getString(1) }
        return AppSettings.fromMap(m)
    }

    // ---------------- categories ----------------

    fun observeCategories(): Flow<List<Category>> = observe { readCategories(it) }

    suspend fun categories(): List<Category> = read { readCategories(it) }

    private fun readCategories(d: SupportSQLiteDatabase): List<Category> =
        d.query("SELECT id, name, icon, color, kind, sort_order, is_system, archived FROM categories ORDER BY sort_order, name").use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Category(
                        id = c.getString(0), name = c.getString(1), icon = c.getString(2), color = c.getLong(3),
                        kind = runCatching { CategoryKind.valueOf(c.getString(4)) }.getOrDefault(CategoryKind.EXPENSE),
                        sortOrder = c.getInt(5), isSystem = c.getInt(6) == 1, archived = c.getInt(7) == 1,
                    )
                )
            }
        }

    /** Adds a new category (id null) or saves changes to an existing one. Returns its id. */
    suspend fun saveCategory(id: String?, name: String, icon: String, color: Long, countsAsSpend: Boolean): String = write { d ->
        if (id == null) {
            val newId = "c_" + clock.millis()
            val order = d.query("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM categories").use { c -> c.moveToFirst(); c.getInt(0) }
            d.insert(
                "categories", CONFLICT_REPLACE,
                contentValuesOf(
                    "id" to newId, "name" to name, "icon" to icon, "color" to color,
                    "kind" to (if (countsAsSpend) CategoryKind.EXPENSE else CategoryKind.TRANSFER).name,
                    "sort_order" to order, "is_system" to 0, "archived" to 0,
                )
            )
            newId
        } else {
            val currentKind = d.query("SELECT kind FROM categories WHERE id = ?", arrayOf<Any?>(id)).use { c ->
                if (c.moveToFirst()) runCatching { CategoryKind.valueOf(c.getString(0)) }.getOrDefault(CategoryKind.EXPENSE) else CategoryKind.EXPENSE
            }
            val kind = when {
                currentKind == CategoryKind.INCOME -> CategoryKind.INCOME
                countsAsSpend -> CategoryKind.EXPENSE
                currentKind == CategoryKind.EXPENSE -> CategoryKind.TRANSFER
                else -> currentKind
            }
            d.update("categories", CONFLICT_REPLACE, contentValuesOf("name" to name, "icon" to icon, "color" to color, "kind" to kind.name), "id = ?", arrayOf<Any?>(id))
            id
        }
    }

    /** Archives a category; its transactions move to Others and its budget is removed. Returns how many moved. */
    suspend fun archiveCategory(id: String): Int = write { d ->
        val moved = d.update("transactions", CONFLICT_REPLACE, contentValuesOf("category_id" to Cat.OTHERS), "category_id = ?", arrayOf<Any?>(id))
        d.update("rules", CONFLICT_REPLACE, contentValuesOf("category_id" to Cat.OTHERS), "category_id = ?", arrayOf<Any?>(id))
        d.delete("budgets", "category_key = ?", arrayOf<Any?>(id))
        d.update("categories", CONFLICT_REPLACE, contentValuesOf("archived" to 1), "id = ?", arrayOf<Any?>(id))
        moved
    }

    // ---------------- budgets ----------------

    fun observeBudgets(): Flow<Map<String, Long>> = observe { d ->
        d.query("SELECT category_key, limit_paise FROM budgets").use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getLong(1)) }
        }
    }

    suspend fun setBudget(key: String, limitPaise: Long?): Unit = write { d ->
        if (limitPaise == null || limitPaise <= 0) d.delete("budgets", "category_key = ?", arrayOf<Any?>(key))
        else d.insert("budgets", CONFLICT_REPLACE, contentValuesOf("category_key" to key, "limit_paise" to limitPaise))
        Unit
    }

    // ---------------- transactions ----------------

    fun observeTransactions(fromMs: Long, toMs: Long): Flow<List<Txn>> = observe { d ->
        d.query("SELECT $TX_COLS FROM transactions WHERE occurred_at >= ? AND occurred_at < ? ORDER BY occurred_at DESC, id DESC", arrayOf<Any?>(fromMs, toMs))
            .use { c -> c.toTxns() }
    }

    suspend fun transactions(fromMs: Long, toMs: Long): List<Txn> = read { d ->
        d.query("SELECT $TX_COLS FROM transactions WHERE occurred_at >= ? AND occurred_at < ? ORDER BY occurred_at DESC, id DESC", arrayOf<Any?>(fromMs, toMs))
            .use { c -> c.toTxns() }
    }

    suspend fun transaction(id: Long): Txn? = read { d ->
        d.query("SELECT $TX_COLS FROM transactions WHERE id = ?", arrayOf<Any?>(id)).use { c -> c.toTxns().firstOrNull() }
    }

    fun observeTransaction(id: Long): Flow<Txn?> = observe { d ->
        d.query("SELECT $TX_COLS FROM transactions WHERE id = ?", arrayOf<Any?>(id)).use { c -> c.toTxns().firstOrNull() }
    }

    suspend fun smsTransactionsSince(fromMs: Long): List<Txn> = read { d ->
        d.query("SELECT $TX_COLS FROM transactions WHERE source = 'SMS' AND occurred_at >= ?", arrayOf<Any?>(fromMs)).use { c -> c.toTxns() }
    }

    suspend fun existingSmsIds(): Set<Long> = read { d ->
        d.query("SELECT sms_id FROM transactions WHERE sms_id IS NOT NULL").use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } } +
            d.query("SELECT sms_id FROM deleted_sms").use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }
    }

    suspend fun deletedSmsIds(): Set<Long> = read { d ->
        d.query("SELECT sms_id FROM deleted_sms").use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }
    }

    /** Inserts imported or manual transactions. Rows whose SMS is already stored are skipped. Returns how many were added. */
    suspend fun insertAll(rows: List<NewTxn>): Int = write { d ->
        var added = 0
        rows.forEach { r -> if (d.insert("transactions", CONFLICT_IGNORE, r.toValues()) != -1L) added++ }
        added
    }

    suspend fun addManual(row: NewTxn): Long = write { d -> d.insert("transactions", CONFLICT_IGNORE, row.toValues()) }

    /** Re-import: removes SMS transactions from [fromMs] onward whose message is not in [keepSmsIds]. */
    suspend fun removeSmsTransactionsNotIn(fromMs: Long, keepSmsIds: Set<Long>): Int = write { d ->
        val ids = d.query("SELECT id, sms_id FROM transactions WHERE source = 'SMS' AND occurred_at >= ?", arrayOf<Any?>(fromMs)).use { c ->
            buildList { while (c.moveToNext()) if (c.getLong(1) !in keepSmsIds) add(c.getLong(0)) }
        }
        ids.forEach { d.delete("transactions", "id = ?", arrayOf<Any?>(it)) }
        ids.size
    }

    suspend fun setCategory(txId: Long, categoryId: String, rememberForMerchant: Boolean): Int = write { d ->
        val key = d.query("SELECT merchant_key FROM transactions WHERE id = ?", arrayOf<Any?>(txId)).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: return@write 0
        if (rememberForMerchant) {
            d.insert(
                "rules", CONFLICT_REPLACE,
                contentValuesOf("match_type" to RuleMatch.MERCHANT.name, "pattern" to key, "category_id" to categoryId, "created_at" to clock.millis())
            )
            d.update("transactions", CONFLICT_REPLACE, contentValuesOf("category_id" to categoryId, "needs_review" to 0), "merchant_key = ?", arrayOf<Any?>(key))
        } else {
            d.update("transactions", CONFLICT_REPLACE, contentValuesOf("category_id" to categoryId, "needs_review" to 0), "id = ?", arrayOf<Any?>(txId))
        }
    }

    suspend fun setNote(txId: Long, note: String) = write { d ->
        d.update("transactions", CONFLICT_REPLACE, contentValuesOf("note" to note.ifBlank { null }), "id = ?", arrayOf<Any?>(txId))
    }

    suspend fun setExcluded(txId: Long, excluded: Boolean) = write { d ->
        d.update("transactions", CONFLICT_REPLACE, contentValuesOf("excluded" to if (excluded) 1 else 0), "id = ?", arrayOf<Any?>(txId))
    }

    suspend fun confirmReview(txId: Long) = write { d ->
        d.update("transactions", CONFLICT_REPLACE, contentValuesOf("needs_review" to 0), "id = ?", arrayOf<Any?>(txId))
    }

    /** Deletes one transaction. If it came from an SMS, remembers that so it never comes back. */
    suspend fun deleteTransaction(id: Long): Txn? = write { d ->
        val t = d.query("SELECT $TX_COLS FROM transactions WHERE id = ?", arrayOf<Any?>(id)).use { c -> c.toTxns().firstOrNull() } ?: return@write null
        if (t.smsId != null) d.insert("deleted_sms", CONFLICT_REPLACE, contentValuesOf("sms_id" to t.smsId, "deleted_at" to clock.millis()))
        d.delete("transactions", "id = ?", arrayOf<Any?>(id))
        t
    }

    /** Undo for [deleteTransaction]. */
    suspend fun restoreTransaction(t: Txn) = write { d ->
        if (t.smsId != null) d.delete("deleted_sms", "sms_id = ?", arrayOf<Any?>(t.smsId))
        val v = t.toNew().toValues()
        v.put("id", t.id)
        d.insert("transactions", CONFLICT_REPLACE, v)
    }

    // ---------------- bulk delete ----------------

    suspend fun countSmsBefore(cutoffMs: Long): Int = count("SELECT COUNT(*) FROM transactions WHERE source = 'SMS' AND occurred_at < ?", cutoffMs)
    suspend fun countSmsInGroup(group: String): Int = count("SELECT COUNT(*) FROM transactions WHERE source = 'SMS' AND sender_group = ?", group)
    suspend fun countAllSms(): Int = count("SELECT COUNT(*) FROM transactions WHERE source = 'SMS'")

    private suspend fun count(sql: String, vararg args: Any?): Int = read { d ->
        d.query(sql, arrayOf(*args)).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    suspend fun deleteSmsBefore(cutoffMs: Long): Int = write { d -> d.delete("transactions", "source = 'SMS' AND occurred_at < ?", arrayOf<Any?>(cutoffMs)) }
    suspend fun deleteSmsInGroup(group: String): Int = write { d -> d.delete("transactions", "source = 'SMS' AND sender_group = ?", arrayOf<Any?>(group)) }
    suspend fun deleteAllSms(): Int = write { d -> d.delete("transactions", "source = 'SMS'", null) }

    data class Storage(val total: Int, val oldestMs: Long?, val bySenderGroup: Map<String, Int>)

    fun observeStorage(): Flow<Storage> = observe { d ->
        val (total, oldest) = d.query("SELECT COUNT(*), MIN(occurred_at) FROM transactions").use { c ->
            c.moveToFirst(); c.getInt(0) to (if (c.isNull(1)) null else c.getLong(1))
        }
        val groups = d.query("SELECT sender_group, COUNT(*) FROM transactions WHERE source = 'SMS' GROUP BY sender_group").use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0) ?: "unknown", c.getInt(1)) }
        }
        Storage(total, oldest, groups)
    }

    // ---------------- rules ----------------

    suspend fun rules(): List<CategoryRule> = read { d ->
        d.query("SELECT match_type, pattern, category_id FROM rules ORDER BY created_at DESC").use { c ->
            buildList {
                while (c.moveToNext()) {
                    val m = runCatching { RuleMatch.valueOf(c.getString(0)) }.getOrNull() ?: continue
                    add(CategoryRule(m, c.getString(1), c.getString(2)))
                }
            }
        }
    }

    fun observeRuleCount(): Flow<Int> = observe { d -> d.query("SELECT COUNT(*) FROM rules").use { c -> c.moveToFirst(); c.getInt(0) } }

    companion object {
        private const val TX_COLS = "id, amount_paise, direction, occurred_at, merchant_name, merchant_key, merchant_package, vpa, category_id, " +
            "account_label, sender_group, sender, ref_no, source, sms_id, needs_review, excluded, note, kind, sms_body"

        private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)

        private fun Cursor.toTxns(): List<Txn> = buildList {
            while (moveToNext()) add(
                Txn(
                    id = getLong(0), amountPaise = getLong(1),
                    direction = if (getString(2) == Direction.CREDIT.name) Direction.CREDIT else Direction.DEBIT,
                    occurredAt = getLong(3), merchantName = getString(4), merchantKey = getString(5),
                    merchantPackage = str(6), vpa = str(7), categoryId = getString(8), accountLabel = str(9),
                    senderGroup = str(10), sender = str(11), refNo = str(12),
                    source = if (getString(13) == Source.MANUAL.name) Source.MANUAL else Source.SMS,
                    smsId = if (isNull(14)) null else getLong(14), needsReview = getInt(15) == 1, excluded = getInt(16) == 1,
                    note = str(17), kind = runCatching { TxKind.valueOf(getString(18)) }.getOrDefault(TxKind.NORMAL), smsBody = str(19),
                )
            )
        }

        private fun NewTxn.toValues() = contentValuesOf(
            "amount_paise" to amountPaise, "direction" to direction.name, "occurred_at" to occurredAt,
            "merchant_name" to merchantName, "merchant_key" to merchantKey, "merchant_package" to merchantPackage,
            "vpa" to vpa, "category_id" to categoryId, "account_label" to accountLabel, "sender_group" to senderGroup,
            "sender" to sender, "ref_no" to refNo, "source" to source.name, "sms_id" to smsId,
            "needs_review" to if (needsReview) 1 else 0, "excluded" to if (excluded) 1 else 0, "note" to note,
            "kind" to kind.name, "sms_body" to smsBody,
        )

        fun Txn.toNew() = NewTxn(
            amountPaise, direction, occurredAt, merchantName, merchantKey, merchantPackage, vpa, categoryId, accountLabel,
            senderGroup, sender, refNo, source, smsId, needsReview, note, kind, smsBody, excluded,
        )
    }
}
