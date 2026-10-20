package com.spendlens.app

import android.content.Context
import android.graphics.Bitmap
import android.content.pm.PackageManager
import androidx.core.graphics.drawable.toBitmap
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.spendlens.app.data.Db
import com.spendlens.app.data.Repository
import com.spendlens.app.security.KeyManager
import com.spendlens.app.sms.AndroidSmsSource
import com.spendlens.app.sms.ImportEngine
import com.spendlens.app.sms.SmsSource
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/** Wires the app together by hand: one place to swap in fakes for tests. */
class AppContainer(
    val context: Context,
    dbFactory: () -> SupportSQLiteOpenHelper.Factory,
    dbName: String?,
    val sms: SmsSource,
    val clock: Clock,
    val keyManager: KeyManager?,
) {
    val db = Db(context, dbFactory, dbName)
    val repo = Repository(db, clock)
    val importEngine = ImportEngine(sms, repo, clock)
    val appIcons = AppIcons(context)

    /**
     * Erases everything: the Keystore key that protected the database, then the database file.
     * Runs on the database thread so no query can reopen the old file halfway through; the next
     * read creates a fresh database under a new key.
     */
    suspend fun eraseEverything() = repo.exclusive {
        keyManager?.destroy()
        db.destroy()
    }

    fun keyProtection(): String = keyManager?.protectionLevel() ?: "Test database (not encrypted)"

    companion object {
        const val DB_NAME = "spendlens.db"

        fun production(context: Context): AppContainer {
            val app = context.applicationContext
            val keys = KeyManager(app)
            return AppContainer(
                context = app,
                dbFactory = {
                    System.loadLibrary("sqlcipher")
                    SupportOpenHelperFactory(keys.sqlcipherPassphrase())
                },
                dbName = DB_NAME,
                sms = AndroidSmsSource(app),
                clock = Clock.systemDefaultZone(),
                keyManager = keys,
            )
        }
    }
}

/**
 * Icons of installed apps (Swiggy, Uber...). Android draws them for us; we never ship anyone's logo.
 * Only packages declared in the manifest's <queries> block are visible on Android 11+.
 */
class AppIcons(private val context: Context) {
    private val cache = ConcurrentHashMap<String, Any>()
    private object Missing

    fun get(packageName: String?): Bitmap? {
        if (packageName == null) return null
        val v = cache.getOrPut(packageName) {
            try {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96)
            } catch (e: PackageManager.NameNotFoundException) {
                Missing
            } catch (e: Exception) {
                Missing
            }
        }
        return v as? Bitmap
    }
}
