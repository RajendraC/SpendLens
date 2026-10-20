package com.spendlens.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SmsParserTest {

    private val now = 1_792_300_000_000L

    @Test
    fun everySampleParsesOrIsSkippedAsExpected() {
        val failures = ArrayList<String>()
        SampleSms.all.forEachIndexed { i, s ->
            val sms = SmsMessage(i.toLong(), s.address, s.body, now - s.minutesAgo * 60_000)
            when (val r = SmsParser.parse(sms)) {
                is ParseResult.Rejected -> {
                    if (s.expectReject == null) failures += "#$i ${s.address}: rejected as ${r.reason}, expected a transaction"
                    else if (s.expectReject != r.reason) failures += "#$i ${s.address}: rejected as ${r.reason}, expected ${s.expectReject}"
                }
                is ParseResult.Accepted -> {
                    val t = r.tx
                    if (s.expectReject != null) { failures += "#$i ${s.address}: accepted, expected reject ${s.expectReject}"; return@forEachIndexed }
                    if (s.expectAmountPaise != null && t.amountPaise != s.expectAmountPaise) failures += "#$i amount ${t.amountPaise} != ${s.expectAmountPaise}"
                    if (s.expectDirection != null && t.direction != s.expectDirection) failures += "#$i direction ${t.direction} != ${s.expectDirection}"
                    if (s.expectLast4 != null && t.accountLast4 != s.expectLast4) failures += "#$i last4 ${t.accountLast4} != ${s.expectLast4}"
                    if (s.expectMerchant != null) {
                        val known = MerchantRegistry.match(t.merchantRaw, t.vpa)
                        val name = known?.displayName ?: t.merchantRaw
                        if (name != s.expectMerchant) failures += "#$i merchant '$name' (raw '${t.merchantRaw}') != '${s.expectMerchant}'"
                    }
                    if (s.expectCategory != null) {
                        val known = MerchantRegistry.match(t.merchantRaw, t.vpa)
                        val key = MerchantRegistry.keyFor(known, t.merchantRaw, t.vpa)
                        val cat = Categorizer().categorize(t, key, known)
                        if (cat != s.expectCategory) failures += "#$i category $cat != ${s.expectCategory} (merchant '${t.merchantRaw}', kind ${t.kind})"
                    }
                }
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    @Test
    fun hdfcUpiFieldsAreExtracted() {
        val t = accepted("VM-HDFCBK-S", SampleSms.all[0].body)
        assertEquals("hdfc", t.senderGroup)
        assertEquals("629112345678", t.reference)
        assertFalse(t.isCard)
        assertFalse(t.needsReview)
    }

    @Test
    fun cardSpendIsMarkedAsCardAndBalanceIsNotTheAmount() {
        val t = accepted("VM-HDFCBK-T", SampleSms.all[1].body)
        assertTrue(t.isCard)
        assertEquals(31200L, t.amountPaise)
        assertEquals(14231000L, t.balancePaise)
    }

    @Test
    fun earliestVerbWinsWhenBothDebitAndCreditAppear() {
        val t = accepted("JD-ICICIT-S", SampleSms.all[2].body)
        assertEquals(Direction.DEBIT, t.direction)
    }

    @Test
    fun genuineDebitWithOtpWarningIsKept() {
        val t = accepted("VM-HDFCBK-S", "Rs.250.00 debited from A/c XX4821 to CHAI POINT on 18-10-26. Never share your OTP or PIN with anyone. -HDFC Bank")
        assertEquals(25000L, t.amountPaise)
    }

    @Test
    fun failedPaymentRefundCreditIsKept() {
        val t = accepted("VM-ICICIT-S", "Rs 350.00 has been reversed to your ICICI Bank Acct XX710 for the failed transaction to xyz@ybl on 18-Oct-26.")
        assertEquals(Direction.CREDIT, t.direction)
        assertEquals(TxKind.REFUND, t.kind)
    }

    @Test
    fun unmarkedOldHeaderStillParses() {
        val t = accepted("VM-HDFCBK", "Rs.99.00 debited from A/c XX4821 to SPOTIFY on 10-10-26.")
        assertEquals("hdfc", t.senderGroup)
    }

    @Test
    fun unknownServiceSenderIsAcceptedButFlaggedForReview() {
        val t = accepted("VM-XYZPAY-S", "Rs.120 debited from your wallet for order 5512 on 10-10-26.")
        assertEquals(Institutions.UNRECOGNISED, t.senderGroup)
        assertTrue(t.needsReview)
    }

    @Test
    fun personalMessagesAreNeverRead() {
        val r = SmsParser.parse(SmsMessage(1, "9876543210", "Rs 500 debited from my account lol", now))
        assertTrue(r is ParseResult.Rejected && r.reason == RejectReason.PERSONAL_SENDER)
    }

    @Test
    fun cleanMerchantTidiesNames() {
        assertEquals("Uber", SmsParser.cleanMerchant("UBER INDIA SYSTEMS PVT LTD"))
        assertEquals("Swiggy", SmsParser.cleanMerchant("VPA swiggy@icici"))
        assertNull(SmsParser.cleanMerchant("12345"))
    }

    private fun accepted(address: String, body: String): ParsedTransaction {
        val r = SmsParser.parse(SmsMessage(1, address, body, now))
        assertTrue("Expected a transaction but got $r", r is ParseResult.Accepted)
        return (r as ParseResult.Accepted).tx.also { assertNotNull(it) }
    }
}
