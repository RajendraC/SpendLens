package com.spendlens.app

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendlens.app.data.Db
import com.spendlens.app.data.Repository
import com.spendlens.app.security.KeyManager
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock

/**
 * Runs on real Android (emulators for each version in CI): Android Keystore wraps the key,
 * SQLCipher encrypts the file, and destroying the key makes the old file useless.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alias = "test_wrap_key"
    private val keyFile = "test_db_key.bin"
    private val dbName = "test_encrypted.db"

    @Before @After
    fun clean() {
        KeyManager(context, alias, keyFile).destroy()
        context.deleteDatabase(dbName)
    }

    private fun open(keys: KeyManager): Db {
        System.loadLibrary("sqlcipher")
        return Db(context, { SupportOpenHelperFactory(keys.sqlcipherPassphrase()) }, dbName)
    }

    @Test
    fun keyIsStableAcrossInstancesAndChangesAfterDestroy() {
        val k1 = KeyManager(context, alias, keyFile).rawKey()
        val k2 = KeyManager(context, alias, keyFile).rawKey()
        assertEquals(32, k1.size)
        assertArrayEquals(k1, k2)
        KeyManager(context, alias, keyFile).destroy()
        val k3 = KeyManager(context, alias, keyFile).rawKey()
        assertFalse(k1.contentEquals(k3))
        Log.i("SpendLensTest", "API ${Build.VERSION.SDK_INT}: key protected by ${KeyManager(context, alias, keyFile).protectionLevel()}")
    }

    @Test
    fun databaseFileIsEncryptedAndReopens() = runBlocking {
        val keys = KeyManager(context, alias, keyFile)
        val db = open(keys)
        val repo = Repository(db, Clock.systemDefaultZone())
        repo.open()
        repo.updateSettings { it.copy(onboarded = true, budgetStartDay = 25) }
        db.close()

        val header = File(context.getDatabasePath(dbName).path).inputStream().use { s -> ByteArray(16).also { s.read(it) } }
        assertFalse("A plain SQLite file starts with 'SQLite format 3'", String(header, Charsets.US_ASCII).startsWith("SQLite format"))

        val reopened = Repository(open(keys), Clock.systemDefaultZone())
        assertEquals(25, reopened.settings().budgetStartDay)
        assertTrue(reopened.settings().onboarded)
    }

    @Test
    fun oldFileCannotBeOpenedAfterKeyIsDestroyed() = runBlocking {
        val keys = KeyManager(context, alias, keyFile)
        val db = open(keys)
        Repository(db, Clock.systemDefaultZone()).updateSettings { it.copy(onboarded = true) }
        db.close()
        keys.destroy()
        val failed = runCatching { Repository(open(KeyManager(context, alias, keyFile)), Clock.systemDefaultZone()).open() }.isFailure
        assertTrue("The old database must be unreadable with a new key", failed)
    }
}
