package com.spendlens.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.spendlens.core.Cat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The app's single database. On a phone it is SQLCipher (AES-256, key wrapped by Android Keystore);
 * in JVM tests it is plain framework SQLite in memory. Both sit behind the same SupportSQLite interface.
 *
 * Every write bumps [changes]; screens re-query when it moves, which keeps the UI live without an ORM.
 */
class Db(
    private val context: Context,
    private val factoryProvider: () -> SupportSQLiteOpenHelper.Factory,
    private val name: String?,
) {
    private val lock = Any()
    private var helper: SupportSQLiteOpenHelper? = null
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes

    fun get(): SupportSQLiteDatabase = synchronized(lock) {
        val h = helper ?: factoryProvider().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(SchemaCallback())
                .build()
        ).also { helper = it }
        h.writableDatabase
    }

    fun notifyChanged() = _changes.update { it + 1 }

    fun <T> transaction(block: (SupportSQLiteDatabase) -> T): T {
        val db = get()
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    /** Closes the database without deleting it. The next [get] reopens it. */
    fun close() {
        synchronized(lock) {
            runCatching { helper?.close() }
            helper = null
        }
    }

    /** Closes and deletes the database file. The next [get] creates a fresh one. */
    fun destroy() {
        synchronized(lock) {
            runCatching { helper?.close() }
            helper = null
            if (name != null) context.deleteDatabase(name)
        }
        notifyChanged()
    }

    private class SchemaCallback : SupportSQLiteOpenHelper.Callback(Schema.VERSION) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            Schema.CREATE.forEach { db.execSQL(it) }
            Schema.seed(db)
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Version 1 is the first release; future migrations go here.
        }
    }
}

object Schema {
    const val VERSION = 1
    const val OVERALL = "__overall__"

    val CREATE = listOf(
        """CREATE TABLE categories(
            id TEXT PRIMARY KEY NOT NULL,
            name TEXT NOT NULL,
            icon TEXT NOT NULL,
            color INTEGER NOT NULL,
            kind TEXT NOT NULL,
            sort_order INTEGER NOT NULL,
            is_system INTEGER NOT NULL DEFAULT 0,
            archived INTEGER NOT NULL DEFAULT 0)""",
        """CREATE TABLE transactions(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            amount_paise INTEGER NOT NULL,
            direction TEXT NOT NULL,
            occurred_at INTEGER NOT NULL,
            merchant_name TEXT NOT NULL,
            merchant_key TEXT NOT NULL,
            merchant_package TEXT,
            vpa TEXT,
            category_id TEXT NOT NULL,
            account_label TEXT,
            sender_group TEXT,
            sender TEXT,
            ref_no TEXT,
            source TEXT NOT NULL,
            sms_id INTEGER,
            needs_review INTEGER NOT NULL DEFAULT 0,
            excluded INTEGER NOT NULL DEFAULT 0,
            note TEXT,
            kind TEXT NOT NULL DEFAULT 'NORMAL',
            sms_body TEXT)""",
        "CREATE UNIQUE INDEX idx_tx_sms ON transactions(sms_id) WHERE sms_id IS NOT NULL",
        "CREATE INDEX idx_tx_time ON transactions(occurred_at)",
        "CREATE INDEX idx_tx_merchant ON transactions(merchant_key)",
        """CREATE TABLE rules(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            match_type TEXT NOT NULL,
            pattern TEXT NOT NULL,
            category_id TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            UNIQUE(match_type, pattern))""",
        "CREATE TABLE budgets(category_key TEXT PRIMARY KEY NOT NULL, limit_paise INTEGER NOT NULL)",
        // Remembers single transactions the user deleted, so no later scan or re-import brings them back.
        "CREATE TABLE deleted_sms(sms_id INTEGER PRIMARY KEY NOT NULL, deleted_at INTEGER NOT NULL)",
        "CREATE TABLE settings(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)",
    )

    data class SeedCategory(val id: String, val name: String, val icon: String, val color: Long, val kind: CategoryKind)

    val DEFAULT_CATEGORIES = listOf(
        SeedCategory(Cat.FOOD, "Food & Dining", "food", 0xFFD9622B, CategoryKind.EXPENSE),
        SeedCategory(Cat.GROCERIES, "Groceries", "groceries", 0xFF3F8A3A, CategoryKind.EXPENSE),
        SeedCategory(Cat.SHOPPING, "Shopping", "shopping", 0xFF7552C4, CategoryKind.EXPENSE),
        SeedCategory(Cat.TRANSPORT, "Transport", "transport", 0xFF2A6FB5, CategoryKind.EXPENSE),
        SeedCategory(Cat.FUEL, "Fuel", "fuel", 0xFF9A6B2F, CategoryKind.EXPENSE),
        SeedCategory(Cat.BILLS, "Bills & Utilities", "bills", 0xFF9E740C, CategoryKind.EXPENSE),
        SeedCategory(Cat.RECHARGE, "Recharge & Subscriptions", "recharge", 0xFFC23F73, CategoryKind.EXPENSE),
        SeedCategory(Cat.ENTERTAINMENT, "Entertainment", "entertainment", 0xFFB04FA5, CategoryKind.EXPENSE),
        SeedCategory(Cat.HEALTH, "Health", "health", 0xFF1F8F86, CategoryKind.EXPENSE),
        SeedCategory(Cat.TRAVEL, "Travel", "travel", 0xFF3A86C9, CategoryKind.EXPENSE),
        SeedCategory(Cat.EDUCATION, "Education", "education", 0xFF5866B8, CategoryKind.EXPENSE),
        SeedCategory(Cat.RENT, "Rent", "rent", 0xFF6B7D3A, CategoryKind.EXPENSE),
        SeedCategory(Cat.EMI, "EMI & Loans", "emi", 0xFF8A5A44, CategoryKind.EXPENSE),
        SeedCategory(Cat.INVESTMENTS, "Investments", "investments", 0xFF2E7D5B, CategoryKind.SAVING),
        SeedCategory(Cat.CASH, "Cash", "cash", 0xFF5E7480, CategoryKind.EXPENSE),
        SeedCategory(Cat.TRANSFERS, "Transfers", "transfer", 0xFF6F787D, CategoryKind.TRANSFER),
        SeedCategory(Cat.OTHERS, "Others", "others", 0xFF7F8682, CategoryKind.EXPENSE),
        SeedCategory(Cat.INCOME, "Income", "income", 0xFF0F6B4F, CategoryKind.INCOME),
    )

    fun seed(db: SupportSQLiteDatabase) {
        DEFAULT_CATEGORIES.forEachIndexed { i, c ->
            db.execSQL(
                "INSERT INTO categories(id, name, icon, color, kind, sort_order, is_system, archived) VALUES(?,?,?,?,?,?,1,0)",
                arrayOf<Any?>(c.id, c.name, c.icon, c.color, c.kind.name, i)
            )
        }
    }
}
