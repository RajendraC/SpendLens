package com.spendlens.app

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.spendlens.app.sms.FakeSmsSource
import com.spendlens.core.SampleSms
import com.spendlens.core.SmsMessage
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Saturday 18 Oct 2026, 1:14 PM in India: mid-month, so every sample lands in the current budget month. */
val TEST_CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-18T07:44:00Z"), ZoneId.of("Asia/Kolkata"))

fun sampleInbox(extra: List<SmsMessage> = emptyList()): MutableList<SmsMessage> =
    (SampleSms.messages(TEST_CLOCK.millis()) + extra).toMutableList()

/** In-memory database, sample inbox, fixed clock. No Keystore (Robolectric has none). */
fun testContainer(context: Context, inbox: MutableList<SmsMessage> = sampleInbox(), granted: Boolean = true) = AppContainer(
    context = context,
    dbFactory = { FrameworkSQLiteOpenHelperFactory() },
    dbName = null,
    sms = FakeSmsSource({ inbox.toList() }, granted),
    clock = TEST_CLOCK,
    keyManager = null,
)

/** Application used by Robolectric UI tests: same app, test container, no background scheduling. */
class TestApp : SpendLensApp() {
    override fun createContainer(): AppContainer = testContainer(this)
    override val scheduleBackgroundSync: Boolean = false
}
