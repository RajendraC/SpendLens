package com.spendlens.core

/**
 * Representative bank, card and UPI alerts in the formats Indian banks use, with made-up numbers.
 * Used by unit tests, device tests and the demo inbox. Replace or extend these with masked
 * copies of your own messages to tune the parser for your banks.
 */
object SampleSms {

    data class Sample(
        val address: String,
        val body: String,
        val minutesAgo: Long,
        val expectAmountPaise: Long? = null,
        val expectDirection: Direction? = null,
        val expectLast4: String? = null,
        val expectMerchant: String? = null,
        val expectCategory: String? = null,
        val expectReject: RejectReason? = null,
    )

    private const val H = 60L
    private const val D = 24 * 60L

    val all: List<Sample> = listOf(
        Sample("VM-HDFCBK-S", "Sent Rs.486.00\nFrom HDFC Bank A/C *4821\nTo SWIGGY\nOn 18/10/26\nRef 629112345678\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
            minutesAgo = 2 * H, expectAmountPaise = 48600, expectDirection = Direction.DEBIT, expectLast4 = "4821", expectMerchant = "Swiggy", expectCategory = Cat.FOOD),
        Sample("VM-HDFCBK-T", "Spent Rs.312 On HDFC Bank Card 9012 At UBER INDIA On 2026-10-18:09:40:12 Bal Rs.142310 Not You? Call 18002586161/SMS BLOCK DC 9012 to 7308080808",
            minutesAgo = 5 * H, expectAmountPaise = 31200, expectDirection = Direction.DEBIT, expectLast4 = "9012", expectMerchant = "Uber", expectCategory = Cat.TRANSPORT),
        Sample("JD-ICICIT-S", "ICICI Bank Acct XX710 debited for Rs 1,240.00 on 17-Oct-26; ZEPTO credited. UPI:629812345678. Call 18002662 for dispute. SMS BLOCK 710 to 9215676766.",
            minutesAgo = 1 * D, expectAmountPaise = 124000, expectDirection = Direction.DEBIT, expectLast4 = "710", expectMerchant = "Zepto", expectCategory = Cat.GROCERIES),
        Sample("AD-SBIUPI-S", "Dear UPI user A/C X3307 debited by 2180.0 on date 17Oct26 trf to TNEB Refno 629712345678. If not u? call 1800111109. -SBI",
            minutesAgo = 1 * D + 2 * H, expectAmountPaise = 218000, expectDirection = Direction.DEBIT, expectLast4 = "3307", expectCategory = Cat.BILLS),
        Sample("VM-ICICIT-T", "INR 3,499.00 spent using ICICI Bank Card XX1234 on 15-Oct-26 on AMAZON. Avl Limit: INR 1,42,310.00. If not you, call 1800 2662/SMS BLOCK 1234 to 9215676766",
            minutesAgo = 3 * D, expectAmountPaise = 349900, expectDirection = Direction.DEBIT, expectLast4 = "1234", expectMerchant = "Amazon", expectCategory = Cat.SHOPPING),
        Sample("AX-AXISBK-S", "INR 649.00 debited\nA/c no. XX5521\n17-10-26, 20:14:03\nUPI/P2M/629212345678/NETFLIX\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank",
            minutesAgo = 1 * D + 5 * H, expectAmountPaise = 64900, expectDirection = Direction.DEBIT, expectLast4 = "5521", expectMerchant = "Netflix", expectCategory = Cat.RECHARGE),
        Sample("VK-KOTAKB-S", "Sent Rs.148.00 from Kotak Bank AC X8899 to rapido@ybl on 12-10-26.UPI Ref 629312345678. Not you, call 18602662666",
            minutesAgo = 6 * D, expectAmountPaise = 14800, expectDirection = Direction.DEBIT, expectLast4 = "8899", expectMerchant = "Rapido", expectCategory = Cat.TRANSPORT),
        Sample("VM-HDFCBK-S", "Rs.5000.00 withdrawn at ATM ANNA NAGAR CHENNAI from A/c XX4821 on 07-10-26. Avl Bal Rs.41,250.00. Not you? Call 18002586161",
            minutesAgo = 11 * D, expectAmountPaise = 500000, expectDirection = Direction.DEBIT, expectLast4 = "4821", expectCategory = Cat.CASH),
        Sample("VM-HDFCBK-T", "Refund of Rs.799.00 from AMAZON credited to your HDFC Bank Credit Card ending 9012 on 02-10-26",
            minutesAgo = 16 * D, expectAmountPaise = 79900, expectDirection = Direction.CREDIT, expectLast4 = "9012", expectMerchant = "Amazon", expectCategory = Cat.SHOPPING),
        Sample("VM-HDFCBK-S", "Rs.120000.00 credited to HDFC Bank A/c XX4821 on 01-10-26 by NEFT from ACME SOFTWARE (salary). Avl Bal Rs.1,46,250.00",
            minutesAgo = 17 * D, expectAmountPaise = 12000000, expectDirection = Direction.CREDIT, expectLast4 = "4821", expectCategory = Cat.INCOME),
        Sample("VM-HDFCBK-T", "Payment of Rs.18,400.00 received towards your HDFC Bank Credit Card ending 9012 on 08-10-26. Thank you.",
            minutesAgo = 10 * D, expectAmountPaise = 1840000, expectDirection = Direction.CREDIT, expectLast4 = "9012", expectCategory = Cat.TRANSFERS),
        Sample("VM-HDFCBK-S", "Rs.10000.00 debited from A/c XX4821 on 01-10-26 towards SIP NACH mandate ICCL. Avl Bal Rs.1,36,250.00",
            minutesAgo = 17 * D - 30, expectAmountPaise = 1000000, expectDirection = Direction.DEBIT, expectLast4 = "4821", expectCategory = Cat.INVESTMENTS),
        Sample("BP-HDFCBK-S", "Sent Rs.742.00 From HDFC Bank A/C *4821 To zomato@hdfcbank On 14/10/26 Ref 629412345678 Not You? Call 18002586161",
            minutesAgo = 4 * D, expectAmountPaise = 74200, expectDirection = Direction.DEBIT, expectLast4 = "4821", expectMerchant = "Zomato", expectCategory = Cat.FOOD),

        // Messages that must be skipped
        Sample("VM-HDFCBK-T", "123456 is OTP for txn of INR 2,000.00 at AMAZON on HDFC Bank card ending 9012. Valid till 10:42. Do not share OTP for security reasons",
            minutesAgo = 30, expectReject = RejectReason.OTP),
        Sample("VM-HDFCBK-P", "Get a pre-approved Personal Loan of Rs.5,00,000 at 10.5% p.a. Apply now in 2 clicks!",
            minutesAgo = 3 * H, expectReject = RejectReason.PROMOTIONAL),
        Sample("VM-HDFCBK-S", "Rs.10000.00 will be debited from your A/c XX4821 on 01-11-26 towards SIP. Ensure sufficient balance.",
            minutesAgo = 2 * D, expectReject = RejectReason.FUTURE_OR_REMINDER),
        Sample("VM-HDFCBK-T", "Your HDFC Bank Credit Card ending 9012 statement is ready. Total Amt Due Rs.18,400; Min Amt Due Rs.920; due by 25-10-26",
            minutesAgo = 9 * D, expectReject = RejectReason.NOT_A_TRANSACTION),
        Sample("JX-PAYTMB-S", "Ravi has requested money from you. On approving, Rs.500 will be debited from your account.",
            minutesAgo = 8 * H, expectReject = RejectReason.FUTURE_OR_REMINDER),
        Sample("VM-ICICIT-S", "Your transaction of Rs 350.00 to xyz@ybl has failed. Any amount debited will be reversed in 48 hours.",
            minutesAgo = 9 * H, expectReject = RejectReason.FUTURE_OR_REMINDER),
        Sample("+919876543210", "Sent Rs 500 to you for dinner, thanks!",
            minutesAgo = 4 * H, expectReject = RejectReason.PERSONAL_SENDER),
        Sample("AD-GOVTIN-G", "Rs.6000 PM-KISAN installment has been credited to your account.",
            minutesAgo = 5 * D, expectReject = RejectReason.GOVERNMENT),
    )

    fun messages(nowMillis: Long): List<SmsMessage> =
        all.mapIndexed { i, s -> SmsMessage(id = 1000L + i, address = s.address, body = s.body, timestamp = nowMillis - s.minutesAgo * 60_000L) }
}
