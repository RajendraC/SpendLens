package com.spendlens.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyTest {
    @Test fun parsesIndianGrouping() {
        assertEquals(12345678L, Money.parseRupeesToPaise("1,23,456.78"))
        assertEquals(218000L, Money.parseRupeesToPaise("2180.0"))
        assertEquals(500L, Money.parseRupeesToPaise("5"))
        assertNull(Money.parseRupeesToPaise("abc"))
        assertNull(Money.parseRupeesToPaise("1.2.3"))
    }

    @Test fun formatsIndianGrouping() {
        assertEquals("₹1,23,457", Money.formatInr(12345678))
        assertEquals("₹1,23,456.78", Money.formatInr(12345678, showPaise = true))
        assertEquals("₹999", Money.formatInr(99900))
        assertEquals("₹75,000", Money.formatInr(7500000))
        assertEquals("₹12,00,000", Money.formatInr(120000000))
        assertEquals("-₹2,340", Money.formatInr(-234000))
        assertEquals("₹73.4k", Money.formatShort(7345000))
        assertEquals("₹1.2L", Money.formatShort(12000000))
    }
}

class SenderTest {
    @Test fun readsTraiSuffix() {
        assertEquals(HeaderType.SERVICE, SenderParser.parse("VM-HDFCBK-S")!!.type)
        assertEquals(HeaderType.TRANSACTIONAL, SenderParser.parse("JD-ICICIT-T")!!.type)
        assertEquals(HeaderType.PROMOTIONAL, SenderParser.parse("VM-HDFCBK-P")!!.type)
        assertEquals(HeaderType.UNMARKED, SenderParser.parse("AD-SBIUPI")!!.type)
        assertEquals("SBIUPI", SenderParser.parse("ad-sbiupi-s")!!.entity)
        assertNull(SenderParser.parse("+919876543210"))
        assertNull(SenderParser.parse("56161"))
    }

    @Test fun mapsEntitiesToBanks() {
        assertEquals("hdfc", Institutions.forEntity("HDFCBK")!!.id)
        assertEquals("sbi", Institutions.forEntity("ATMSBI")!!.id)
        assertEquals("sbicard", Institutions.forEntity("SBICRD")!!.id)
        assertEquals("icici", Institutions.forEntity("ICICIT")!!.id)
        assertNull(Institutions.forEntity("XYZPAY"))
    }
}

class MerchantTest {
    @Test fun specificNamesWinOverBroadOnes() {
        assertEquals("swiggy_instamart", MerchantRegistry.match("SWIGGY INSTAMART", null)!!.key)
        assertEquals("swiggy", MerchantRegistry.match(null, "swiggy@icici")!!.key)
        assertEquals("primevideo", MerchantRegistry.match("AMAZON PRIME VIDEO", null)!!.key)
        assertEquals("amazon", MerchantRegistry.match("AMAZON PAY INDIA", null)!!.key)
        assertNull(MerchantRegistry.match("Credit card payment", null))
        assertNull(MerchantRegistry.match("Olam Traders", null))
    }

    @Test fun everyKnownMerchantHasAPackageOrCategory() {
        MerchantRegistry.all.forEach { assertTrue(it.category.isNotBlank()) }
        assertTrue(MerchantRegistry.packageNames.contains("in.swiggy.android"))
    }
}

class CategorizerTest {
    private fun tx(body: String, merchant: String?, dir: Direction = Direction.DEBIT, kind: TxKind = TxKind.NORMAL) =
        ParsedTransaction(1, "VM-HDFCBK-S", "hdfc", "HDFC Bank", 0, 100, dir, "4821", false, merchant, null, null, null, kind, 0.9, body)

    @Test fun userRuleBeatsDefaults() {
        val c = Categorizer(listOf(CategoryRule(RuleMatch.MERCHANT, "swiggy", Cat.GROCERIES)))
        val t = tx("", "Swiggy")
        assertEquals(Cat.GROCERIES, c.categorize(t, "swiggy", MerchantRegistry.match("Swiggy", null)))
    }

    @Test fun keywordDefaults() {
        val c = Categorizer()
        assertEquals(Cat.FUEL, c.categorize(tx("", "HP Petrol Pump"), "hp_petrol_pump", null))
        assertEquals(Cat.FOOD, c.categorize(tx("", "Saravana Bhavan"), "saravana_bhavan", null))
        assertEquals(Cat.OTHERS, c.categorize(tx("", "R Kumar"), "r_kumar", null))
        assertEquals(Cat.INCOME, c.categorize(tx("", "R Kumar", Direction.CREDIT), "r_kumar", null))
        assertEquals(Cat.CASH, c.categorize(tx("", null, kind = TxKind.CASH_WITHDRAWAL), "atm", null))
    }
}

class DeduperTest {
    @Test fun sameReferenceIsOnePayment() {
        val d = Deduper()
        assertTrue(d.offer(Deduper.DedupeEntry(48600, Direction.DEBIT, 0, "4821", "629112345678")))
        assertFalse(d.offer(Deduper.DedupeEntry(48600, Direction.DEBIT, 90_000, null, "629112345678")))
    }

    @Test fun bankAndAppAlertWithinFiveMinutesAreOnePayment() {
        val d = Deduper()
        assertTrue(d.offer(Deduper.DedupeEntry(48600, Direction.DEBIT, 0, "4821", null)))
        assertFalse(d.offer(Deduper.DedupeEntry(48600, Direction.DEBIT, 120_000, null, null)))
        assertTrue(d.offer(Deduper.DedupeEntry(48600, Direction.DEBIT, 10 * 60_000, "4821", null)))
        assertTrue(d.offer(Deduper.DedupeEntry(48600, Direction.DEBIT, 10 * 60_000 + 1000, "9012", null)))
    }

    @Test fun differentReferencesAreDifferentPayments() {
        val d = Deduper()
        assertTrue(d.offer(Deduper.DedupeEntry(2000, Direction.DEBIT, 0, "4821", "111111")))
        assertTrue(d.offer(Deduper.DedupeEntry(2000, Direction.DEBIT, 1000, "4821", "222222")))
    }
}

class FiltersTest {
    private fun tx(amount: Long, dir: Direction, group: String = "hdfc", merchant: String = "Shop", body: String = "") =
        ParsedTransaction(1, "x", group, null, 0, amount, dir, null, false, merchant, null, null, null, TxKind.NORMAL, 0.9, body)

    @Test fun defaultsSkipUnknownSendersOnly() {
        val f = ImportFilters()
        assertTrue(f.accepts(tx(100, Direction.DEBIT)))
        assertFalse(f.accepts(tx(100, Direction.DEBIT, group = Institutions.UNRECOGNISED)))
    }

    @Test fun moneyInOutMinAmountAndWords() {
        val f = ImportFilters(includeIn = false, minAmountPaise = 5000, skipWords = listOf("NEFT"))
        assertFalse(f.accepts(tx(100000, Direction.CREDIT)))
        assertFalse(f.accepts(tx(4999, Direction.DEBIT)))
        assertTrue(f.accepts(tx(5000, Direction.DEBIT)))
        assertFalse(f.accepts(tx(5000, Direction.DEBIT, body = "by neft to R Kumar")))
    }
}

class BudgetTest {
    @Test fun statesFollowPaceAndLimit() {
        assertEquals(BudgetState.ON_TRACK, BudgetMath.status(30_000_00, 75_000_00, 18, 31).state)
        // 75% used -> heads-up even if pace is fine
        assertEquals(BudgetState.HEADS_UP, BudgetMath.status(57_000_00, 75_000_00, 28, 31).state)
        // projected over -> heads-up
        assertEquals(BudgetState.HEADS_UP, BudgetMath.status(50_000_00, 75_000_00, 18, 31).state)
        assertEquals(BudgetState.OVER, BudgetMath.status(77_340_00, 75_000_00, 18, 31).state)
    }

    @Test fun safePerDayUsesDaysLeft() {
        val s = BudgetMath.status(20_000_00, 75_000_00, 18, 31)
        assertEquals(13, s.daysLeft)
        assertEquals((55_000_00L) / 13, s.safePerDayPaise)
    }

    @Test fun periodCanStartOnSalaryDay() {
        val p = BudgetMath.periodFor(LocalDate.of(2026, 10, 18), 25)
        assertEquals(LocalDate.of(2026, 9, 25), p.start)
        assertEquals(LocalDate.of(2026, 10, 25), p.endExclusive)
        assertEquals(30, p.days)
        assertEquals(24, p.dayNumber(LocalDate.of(2026, 10, 18)))
        val q = BudgetMath.periodFor(LocalDate.of(2026, 10, 18), 1)
        assertEquals(LocalDate.of(2026, 10, 1), q.start)
        assertEquals(31, q.days)
        assertEquals(LocalDate.of(2026, 9, 1), q.previous(1).start)
    }
}
