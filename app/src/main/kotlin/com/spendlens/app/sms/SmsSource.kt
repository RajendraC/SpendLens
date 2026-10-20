package com.spendlens.app.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.spendlens.core.SmsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where messages come from. The real inbox on a phone; a fixed list in tests and the demo. */
interface SmsSource {
    fun hasPermission(): Boolean
    suspend fun read(sinceMillis: Long): List<SmsMessage>
}

/**
 * Reads the SMS inbox through the system content provider. Only four columns are requested:
 * message id, sender, text and time. Nothing is ever written to the inbox.
 */
class AndroidSmsSource(private val context: Context) : SmsSource {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    override suspend fun read(sinceMillis: Long): List<SmsMessage> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val out = ArrayList<SmsMessage>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val body = c.getString(iBody) ?: continue
                out += SmsMessage(c.getLong(iId), c.getString(iAddr) ?: "", body, c.getLong(iDate))
            }
        }
        out
    }
}

/** A fixed inbox, for tests and for trying the app without real messages. */
class FakeSmsSource(private val messages: () -> List<SmsMessage>, var granted: Boolean = true) : SmsSource {
    override fun hasPermission() = granted
    override suspend fun read(sinceMillis: Long) = if (granted) messages().filter { it.timestamp >= sinceMillis } else emptyList()
}
