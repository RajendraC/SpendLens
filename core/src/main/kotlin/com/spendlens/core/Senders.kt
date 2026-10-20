package com.spendlens.core

/** TRAI header suffix since May 2025: -T transactional, -S service, -P promotional, -G government. */
enum class HeaderType { TRANSACTIONAL, SERVICE, PROMOTIONAL, GOVERNMENT, UNMARKED }

data class SenderHeader(val raw: String, val entity: String, val type: HeaderType)

object SenderParser {
    // VM-HDFCBK-S, AD-SBIUPI, JD-ICICIT-T: operator and circle prefix, DLT entity, TRAI suffix
    private val DLT = Regex("""^([A-Za-z]{2})-([A-Za-z0-9]{3,9})(?:-([A-Za-z]))?$""")
    // HDFCBK-S: prefix stripped, suffix kept
    private val NO_PREFIX = Regex("""^([A-Za-z0-9]{3,9})-([A-Za-z])$""")
    // AXHDFCBK: prefix joined to the entity without a hyphen
    private val JOINED = Regex("""^([A-Za-z]{2})([A-Za-z0-9]{6})$""")
    // HDFCBK, SBIUPI: just the entity. Needs a letter; digits-only senders are phone numbers or short codes.
    private val BARE = Regex("""^[A-Za-z0-9]{3,9}$""")

    /**
     * Returns null for phone numbers and short codes: those are personal messages. In India only
     * registered businesses can send from a name, so any 3-9 character sender with a letter is a header.
     */
    fun parse(address: String?): SenderHeader? {
        val a = address?.trim()?.removePrefix("+")?.trim().orEmpty()
        if (a.isEmpty() || a.none { it.isLetter() }) return null
        DLT.matchEntire(a)?.let { m -> return SenderHeader(a, m.groupValues[2].uppercase(), typeOf(m.groupValues[3])) }
        NO_PREFIX.matchEntire(a)?.let { m -> return SenderHeader(a, m.groupValues[1].uppercase(), typeOf(m.groupValues[2])) }
        JOINED.matchEntire(a)?.let { m ->
            val entity = m.groupValues[2].uppercase()
            if (Institutions.forEntity(entity) != null) return SenderHeader(a, entity, HeaderType.UNMARKED)
        }
        if (BARE.matches(a)) return SenderHeader(a, a.uppercase(), HeaderType.UNMARKED)
        return null
    }

    private fun typeOf(suffix: String) = when (suffix.uppercase()) {
        "T" -> HeaderType.TRANSACTIONAL
        "S" -> HeaderType.SERVICE
        "P" -> HeaderType.PROMOTIONAL
        "G" -> HeaderType.GOVERNMENT
        else -> HeaderType.UNMARKED
    }
}

enum class InstitutionKind { BANK, CARD, WALLET }

data class Institution(val id: String, val name: String, val kind: InstitutionKind)

/**
 * Known sender entities. Matching is by prefix or contains on the 6-character DLT entity,
 * so HDFCBK, HDFCBN and HDFCCC all map to HDFC Bank. Extend this list as you meet new senders.
 */
object Institutions {
    const val UNRECOGNISED = "unknown"

    private data class Entry(val pattern: Regex, val institution: Institution)

    private fun e(pattern: String, id: String, name: String, kind: InstitutionKind = InstitutionKind.BANK) =
        Entry(Regex(pattern), Institution(id, name, kind))

    private val entries = listOf(
        e("^SBICRD|^SBICARD|^SBMCRD", "sbicard", "SBI Card", InstitutionKind.CARD),
        e("^HDFC", "hdfc", "HDFC Bank"),
        e("^ICICI", "icici", "ICICI Bank"),
        e("SBI", "sbi", "SBI"),
        e("^AXIS", "axis", "Axis Bank"),
        e("^KOTAK|^KOTAKB", "kotak", "Kotak Mahindra Bank"),
        e("^PAYTM", "paytm", "Paytm Payments Bank", InstitutionKind.WALLET),
        e("^PHONPE|^PHONEPE", "phonepe", "PhonePe", InstitutionKind.WALLET),
        e("^AMZPAY|^AMAZONP", "amazonpay", "Amazon Pay", InstitutionKind.WALLET),
        e("^MOBIKW", "mobikwik", "MobiKwik", InstitutionKind.WALLET),
        e("^AIRBNK|^AIRTLB", "airtelbank", "Airtel Payments Bank"),
        e("^IDFC", "idfc", "IDFC FIRST Bank"),
        e("^YESB", "yes", "Yes Bank"),
        e("^INDUS", "indusind", "IndusInd Bank"),
        e("^PNB", "pnb", "Punjab National Bank"),
        e("^BOIIND|^BOISMS|^BOI", "boi", "Bank of India"),
        e("^BOB|^BARODA", "bob", "Bank of Baroda"),
        e("^CANB", "canara", "Canara Bank"),
        e("^UNION|^UBIN", "union", "Union Bank of India"),
        e("^AUBANK|^AUSFB", "au", "AU Small Finance Bank"),
        e("^FEDB|^FEDBNK", "federal", "Federal Bank"),
        e("^IDBI", "idbi", "IDBI Bank"),
        e("^IOB", "iob", "Indian Overseas Bank"),
        e("^UCO", "uco", "UCO Bank"),
        e("^CENTB|^CBOI", "central", "Central Bank of India"),
        e("^INDBNK|^INDIAN", "indianbank", "Indian Bank"),
        e("^RBL", "rbl", "RBL Bank"),
        e("^BANDHN", "bandhan", "Bandhan Bank"),
        e("^SCB|^SCBANK|^STANC", "scb", "Standard Chartered"),
        e("^CITI", "citi", "Citibank"),
        e("^AMEX", "amex", "American Express", InstitutionKind.CARD),
        e("^ONECRD|^ONECARD", "onecard", "OneCard", InstitutionKind.CARD),
        e("^SLICE", "slice", "slice", InstitutionKind.CARD),
    )

    fun forEntity(entity: String): Institution? = entries.firstOrNull { it.pattern.containsMatchIn(entity) }?.institution

    fun nameFor(id: String?): String = when (id) {
        null, UNRECOGNISED -> "Unrecognised senders"
        else -> entries.firstOrNull { it.institution.id == id }?.institution?.name ?: id
    }
}
