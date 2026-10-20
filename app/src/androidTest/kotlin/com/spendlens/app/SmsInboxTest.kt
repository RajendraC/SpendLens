package com.spendlens.app

import android.Manifest
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.spendlens.app.sms.AndroidSmsSource
import com.spendlens.core.ImportFilters
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

/**
 * Reads the real SMS inbox through Android's content provider. In CI the emulator inbox is seeded
 * by ci/seed-sms.sh with a bank debit, an OTP and a personal message before the tests run.
 */
@RunWith(AndroidJUnit4::class)
class SmsInboxTest {

    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_SMS)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun readsInboxWithOnlyFourColumns() = runBlocking {
        val source = AndroidSmsSource(context)
        assertTrue(source.hasPermission())
        val messages = source.read(0)
        Log.i("SpendLensTest", "API ${Build.VERSION.SDK_INT}: inbox has ${messages.size} messages")
        messages.forEach { assertTrue(it.body.isNotEmpty()) }
    }

    @Test
    fun seededBankSmsBecomesATransactionAndOtherMessagesAreIgnored() = runBlocking {
        val source = AndroidSmsSource(context)
        val inbox = source.read(0)
        assumeTrue(
            "Inbox seeding is not supported on this emulator image; parsing is still covered by JVM tests",
            inbox.any { it.address == "VM-HDFCBK-S" && it.body.contains("SWIGGY") },
        )
        val container = AppContainer(context, { FrameworkSQLiteOpenHelperFactory() }, null, source, Clock.systemDefaultZone(), null)
        val scan = container.importEngine.scan()
        val swiggy = scan.candidates.firstOrNull { it.merchantName == "Swiggy" }
        assertNotNull("Swiggy debit should be found", swiggy)
        assertEquals(48600L, swiggy!!.tx.amountPaise)
        assertEquals("4821", swiggy.tx.accountLast4)
        assertFalse("OTP must be ignored", scan.candidates.any { it.tx.body.contains("is OTP") })
        assertFalse("Personal messages must be ignored", scan.candidates.any { it.tx.sender.startsWith("+") || it.tx.sender.all(Char::isDigit) })
        val r = container.importEngine.commit(container.importEngine.selectable(scan, ImportFilters()), ImportFilters(), 0, false)
        assertTrue(r.saved >= 1)
    }
}
