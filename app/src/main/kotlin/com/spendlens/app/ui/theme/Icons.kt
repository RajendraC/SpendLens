package com.spendlens.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Line icons drawn from SVG paths, so the app needs no icon library. Tint them with Icon(tint = ...). */
object Ic {
    private fun icon(path: String, stroke: Float = 1.9f): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()

    val logo = icon("M10.5 4a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13zM15.5 15.5 20 20M8 12.5V11M10.5 12.5V8.5M13 12.5V10", 2f)
    val home = icon("M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")
    val list = icon("M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01")
    val wallet = icon("M4 7.5A2.5 2.5 0 0 1 6.5 5H18v3M4 7.5V18a2 2 0 0 0 2 2h14V9H6.5A2.5 2.5 0 0 1 4 7.5zM16 14.5h.01")
    val grid = icon("M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z")
    val plus = icon("M12 5v14M5 12h14", 2.2f)
    val search = icon("M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4")
    val back = icon("M15 5l-7 7 7 7", 2f)
    val chevL = icon("M14.5 6 8.5 12l6 6", 2f)
    val chevR = icon("M9.5 6l6 6-6 6", 2f)
    val close = icon("M6 6l12 12M18 6 6 18", 2f)
    val check = icon("M5 12.5l4.5 4.5L19 7", 2.4f)
    val lock = icon("M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 0 1 7 0v3")
    val finger = icon("M7.5 12a4.5 4.5 0 0 1 9 0v1.5M12 11.5v4.5M9.8 19c.5-1.5.7-3.2.7-5v-2a1.5 1.5 0 0 1 3 0v1.5M4.8 9.5a8 8 0 0 1 14.4 0M5.5 15c.2 1.2.6 2.3 1.1 3.3M16.4 16.5c-.2 1.2-.6 2.4-1.1 3.5", 1.7f)
    val message = icon("M4 5h16v11H9l-5 4z")
    val noNet = icon("M3 3l18 18M8.5 16.4a5 5 0 0 1 7 0M5 12.6a10 10 0 0 1 4.3-2.4M13.4 10a10 10 0 0 1 5.6 2.6M2 8.9A15 15 0 0 1 6.4 6M10.4 5.1A15 15 0 0 1 22 8.9M12 20h.01")
    val tag = icon("M3 12V4h8l9 9-8 8zM7.5 7.5h.01")
    val edit = icon("M4 20h4L19 9l-4-4L4 16zM14 6l4 4")
    val trash = icon("M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13")
    val eye = icon("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12zM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z")
    val alert = icon("M12 4 2.5 20h19zM12 10v4.5M12 17.5h.01", 2f)
    val refresh = icon("M20 11a8 8 0 0 0-14.5-4.5M4 4v4h4M4 13a8 8 0 0 0 14.5 4.5M20 20v-4h-4")
    val chart = icon("M4 20V10M10 20V4M16 20v-7M2 20h20")
    val sliders = icon("M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1M15 4v4M9 10v4M17 16v4")
    val card = icon("M3 6h18v12H3zM3 10h18M7 15h3")
    val bank = icon("M3 9l9-5 9 5M5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 20h18")
    val calendar = icon("M4 6h16v14H4zM4 10h16M8 3v5M16 3v5")
    val info = icon("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v5M12 8h.01")
    val shield = icon("M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6zM9 12l2 2 4-4")
    val filter = icon("M4 5h16l-6 7.5V19l-4 2v-8.5z")
    val database = icon("M4 6c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3zM4 6v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3")
    val reset = icon("M4 12a8 8 0 1 0 2.3-5.7M4 4v4h4")
    val doc = icon("M6 3h9l4 4v14H6zM14 3v5h5M9 13h7M9 17h5")

    // Category icons
    private val categoryIcons: Map<String, ImageVector> = mapOf(
        "food" to icon("M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M17 3c-2 1-3 3.5-3 6.5V13h3M17 3v18"),
        "coffee" to icon("M5 9h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5zM16 10h1.5a2.5 2.5 0 0 1 0 5H16M8 3v3M11 3v3"),
        "groceries" to icon("M3 4h2l2.4 11h10.2L20 7H6.2M9 20h.01M17 20h.01"),
        "shopping" to icon("M5 8h14l-1 13H6zM9 8V6a3 3 0 0 1 6 0v2"),
        "transport" to icon("M5 16v-5l2-5h10l2 5v5M5 16h14M5 16v2.5M19 16v2.5M5 11h14M8 13.5h.01M16 13.5h.01"),
        "fuel" to icon("M4 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16M3 21h12M4 10h10M14 8h2a2 2 0 0 1 2 2v6a1.5 1.5 0 0 0 3 0V8l-3-3"),
        "bills" to icon("M13 3 5 13h6l-1 8 8-10h-6z"),
        "recharge" to icon("M7 3h10v18H7zM11 18h2"),
        "entertainment" to icon("M4 7h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4zM14 7v10"),
        "health" to icon("M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"),
        "travel" to icon("M4 8h16v11H4zM9 8V5h6v3M9 8v11M15 8v11"),
        "education" to icon("M4 5h6a2 2 0 0 1 2 2v13a2 2 0 0 0-2-2H4zM20 5h-6a2 2 0 0 0-2 2v13a2 2 0 0 1 2-2h6z"),
        "rent" to icon("M4 11l8-6 8 6M6 9.5V20h12V9.5M10 20v-5h4v5"),
        "emi" to icon("M3 9l9-5 9 5M5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 20h18"),
        "investments" to icon("M3 17l6-6 4 4 8-8M15 7h6v6"),
        "cash" to icon("M3 7h18v10H3zM12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM6 10h.01M18 14h.01"),
        "transfer" to icon("M4 8h14l-3-3M20 16H6l3 3"),
        "others" to icon("M6 12h.01M12 12h.01M18 12h.01", 2.6f),
        "income" to icon("M12 4v11M7 10l5 5 5-5M5 20h14"),
        "gift" to icon("M4 10h16v10H4zM3 7h18v3H3zM12 7v13M12 7c-1.5-3-5-3-5-1s3 1 5 1zM12 7c1.5-3 5-3 5-1s-3 1-5 1z"),
        "pet" to icon("M12 13c-3 0-5 2.5-5 4.5S9 20 12 20s5-.5 5-2.5S15 13 12 13zM6 9.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM18 9.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM9.5 5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM14.5 5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z"),
        "tag" to tag,
        "bank" to bank,
        "card" to card,
        "wallet" to wallet,
    )

    val categoryChoices = listOf(
        "tag", "food", "coffee", "groceries", "shopping", "transport", "fuel", "bills", "recharge", "entertainment",
        "health", "travel", "education", "rent", "emi", "investments", "cash", "gift", "pet", "others",
    )

    val colorChoices = listOf(
        0xFFD9622BL, 0xFF3F8A3AL, 0xFF7552C4L, 0xFF2A6FB5L, 0xFF9E740CL,
        0xFFC23F73L, 0xFF1F8F86L, 0xFF6B7D3AL, 0xFF8A5A44L, 0xFF5E7480L,
    )

    fun forCategory(key: String): ImageVector = categoryIcons[key] ?: categoryIcons.getValue("others")
}
