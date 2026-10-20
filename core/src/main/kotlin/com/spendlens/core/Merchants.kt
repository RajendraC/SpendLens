package com.spendlens.core

/** Default category keys. The app seeds these; users can rename, recolor or add their own. */
object Cat {
    const val FOOD = "food"
    const val GROCERIES = "groceries"
    const val SHOPPING = "shopping"
    const val TRANSPORT = "transport"
    const val FUEL = "fuel"
    const val BILLS = "bills"
    const val RECHARGE = "recharge"
    const val ENTERTAINMENT = "entertainment"
    const val HEALTH = "health"
    const val TRAVEL = "travel"
    const val EDUCATION = "education"
    const val RENT = "rent"
    const val EMI = "emi"
    const val INVESTMENTS = "investments"
    const val CASH = "cash"
    const val TRANSFERS = "transfers"
    const val OTHERS = "others"
    const val INCOME = "income"
}

/**
 * A merchant we recognise. [packageName] is the Android app whose icon is shown when that app is
 * installed on the phone; every package listed here must also be listed in the app manifest's
 * <queries> block, or Android 11+ hides it from us.
 */
data class KnownMerchant(
    val key: String,
    val displayName: String,
    val packageName: String?,
    val category: String,
    val patterns: List<String>,
)

object MerchantRegistry {

    private fun m(key: String, name: String, pkg: String?, cat: String, vararg patterns: String) =
        KnownMerchant(key, name, pkg, cat, patterns.toList())

    /** Order matters: more specific names (Instamart, Prime Video) come before broader ones (Swiggy, Amazon). */
    val all: List<KnownMerchant> = listOf(
        m("swiggy_instamart", "Swiggy Instamart", "in.swiggy.android", Cat.GROCERIES, "swiggyinstamart", "instamart"),
        m("swiggy", "Swiggy", "in.swiggy.android", Cat.FOOD, "swiggy", "bundl"),
        m("zomato", "Zomato", "com.application.zomato", Cat.FOOD, "zomato"),
        m("blinkit", "Blinkit", "com.grofers.customerapp", Cat.GROCERIES, "blinkit", "grofers"),
        m("zepto", "Zepto", "com.zeptoconsumerapp", Cat.GROCERIES, "zepto", "kiranakart"),
        m("bigbasket", "BigBasket", "com.bigbasket.mobileapp", Cat.GROCERIES, "bigbasket", "supermarket grocery supplies"),
        m("jiomart", "JioMart", "com.jpl.jiomart", Cat.GROCERIES, "jiomart"),
        m("primevideo", "Prime Video", "com.amazon.avod.thirdpartyclient", Cat.ENTERTAINMENT, "primevideo", "prime video"),
        m("amazon", "Amazon", "in.amazon.mShop.android.shopping", Cat.SHOPPING, "amazon", "amzn"),
        m("flipkart", "Flipkart", "com.flipkart.android", Cat.SHOPPING, "flipkart", "fkrt"),
        m("myntra", "Myntra", "com.myntra.android", Cat.SHOPPING, "myntra"),
        m("ajio", "AJIO", "com.ril.ajio", Cat.SHOPPING, "ajio"),
        m("nykaa", "Nykaa", "com.fsn.nykaa", Cat.SHOPPING, "nykaa"),
        m("meesho", "Meesho", "com.meesho.supply", Cat.SHOPPING, "meesho"),
        m("tatacliq", "Tata CLiQ", "com.tul.tatacliq", Cat.SHOPPING, "tatacliq", "tata cliq"),
        m("lenskart", "Lenskart", "com.lenskart.app", Cat.SHOPPING, "lenskart"),
        m("uber", "Uber", "com.ubercab", Cat.TRANSPORT, "uber"),
        m("ola", "Ola", "com.olacabs.customer", Cat.TRANSPORT, "olacabs", "ola cabs", "ani technologies", "ola money"),
        m("rapido", "Rapido", "com.rapido.passenger", Cat.TRANSPORT, "rapido", "roppen"),
        m("irctc", "IRCTC", "cris.org.in.prs.ima", Cat.TRAVEL, "irctc"),
        m("redbus", "redBus", "in.redbus.android", Cat.TRAVEL, "redbus"),
        m("makemytrip", "MakeMyTrip", "com.makemytrip", Cat.TRAVEL, "makemytrip", "mmt"),
        m("goibibo", "Goibibo", "com.goibibo", Cat.TRAVEL, "goibibo"),
        m("cleartrip", "Cleartrip", "com.cleartrip.android", Cat.TRAVEL, "cleartrip"),
        m("netflix", "Netflix", "com.netflix.mediaclient", Cat.RECHARGE, "netflix"),
        m("spotify", "Spotify", "com.spotify.music", Cat.RECHARGE, "spotify"),
        m("hotstar", "JioHotstar", "in.startv.hotstar", Cat.RECHARGE, "hotstar", "jiohotstar", "novi digital"),
        m("youtube", "YouTube", "com.google.android.youtube", Cat.RECHARGE, "youtube", "google youtube"),
        m("bookmyshow", "BookMyShow", "com.bt.bms", Cat.ENTERTAINMENT, "bookmyshow", "bigtree"),
        m("airtel", "Airtel", "com.myairtelapp", Cat.RECHARGE, "airtel"),
        m("jio", "Jio", "com.jio.myjio", Cat.RECHARGE, "reliance jio", "jio"),
        m("cred", "CRED", "com.dreamplug.androidapp", Cat.TRANSFERS, "cred.club", "cred club", "dreamplug"),
        m("paytm", "Paytm", "net.one97.paytm", Cat.OTHERS, "paytm"),
        m("phonepe", "PhonePe", "com.phonepe.app", Cat.OTHERS, "phonepe"),
        m("gpay", "Google Pay", "com.google.android.apps.nbu.paisa.user", Cat.OTHERS, "google pay", "gpay"),
        m("pharmeasy", "PharmEasy", "com.phonegap.rxpal", Cat.HEALTH, "pharmeasy"),
        m("tata1mg", "Tata 1mg", "com.aranoah.healthkart.plus", Cat.HEALTH, "1mg", "healthkart"),
        m("apollo", "Apollo 24|7", "com.apollo.patientapp", Cat.HEALTH, "apollo"),
        m("dominos", "Domino's", "com.Dominos", Cat.FOOD, "dominos", "domino's", "jubilant foodworks"),
        m("urbancompany", "Urban Company", "com.urbanclap.urbanclap", Cat.BILLS, "urban company", "urbanclap"),
    )

    val packageNames: List<String> = all.mapNotNull { it.packageName }.distinct()

    private val compiled: List<Pair<KnownMerchant, List<Regex>>> =
        all.map { km -> km to km.patterns.map { Regex("""(?i)(?:^|[^a-z0-9])""" + Regex.escape(it)) } }

    fun match(merchantRaw: String?, vpa: String?): KnownMerchant? {
        val hay = listOfNotNull(merchantRaw, vpa).joinToString(" ").lowercase()
        if (hay.isBlank()) return null
        return compiled.firstOrNull { (_, regs) -> regs.any { it.containsMatchIn(hay) } }?.first
    }

    /** Stable key used to group a merchant and to attach rules: known key, else a slug of the cleaned name. */
    fun keyFor(known: KnownMerchant?, displayName: String?, vpa: String?): String =
        known?.key
            ?: displayName?.lowercase()?.replace(Regex("[^a-z0-9]+"), "_")?.trim('_')?.takeIf { it.isNotEmpty() }
            ?: vpa?.lowercase()
            ?: "unknown"
}
