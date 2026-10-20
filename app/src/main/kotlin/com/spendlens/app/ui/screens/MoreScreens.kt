package com.spendlens.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendlens.app.ui.AppViewModel
import com.spendlens.app.ui.ImportMode
import com.spendlens.app.ui.Platform
import com.spendlens.app.ui.Screen
import com.spendlens.app.ui.Sheet
import com.spendlens.app.ui.components.CategoryTile
import com.spendlens.app.ui.components.Divider
import com.spendlens.app.ui.components.Gap
import com.spendlens.app.ui.components.InfoNote
import com.spendlens.app.ui.components.MerchantAvatar
import com.spendlens.app.ui.components.NavRow
import com.spendlens.app.ui.components.Panel
import com.spendlens.app.ui.components.ScreenHeader
import com.spendlens.app.ui.components.SectionLabel
import com.spendlens.app.ui.components.SwitchRow
import com.spendlens.app.ui.theme.Ic
import com.spendlens.app.ui.theme.LocalStatusColors
import com.spendlens.core.Institutions
import com.spendlens.core.Money
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MoreScreen(vm: AppViewModel) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader("More")
        InfoNote(
            "SpendLens has no internet permission, so your messages and spending never leave this phone.",
            icon = Ic.noNet, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Panel(padding = 8.dp) {
            NavRow(Ic.tag, "Categories", "${cats.count { !it.archived }} categories · add, edit, archive") { vm.go(Screen.CATEGORIES) }
            Divider()
            NavRow(Ic.chart, "Insights", "Trends and top merchants") { vm.go(Screen.INSIGHTS) }
            Divider()
            NavRow(Ic.sliders, "Settings", "App lock, SMS import, delete or reset") { vm.go(Screen.SETTINGS) }
        }
        Text("SpendLens 0.1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
fun CategoriesScreen(vm: AppViewModel) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val d by vm.dashboard.collectAsStateWithLifecycle()
    val rules by vm.ruleCount.collectAsStateWithLifecycle()
    val spentBy = d?.byCategory?.associate { it.category.id to it.paise }.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader("Categories", onBack = { vm.back() }) {
                IconButton(onClick = { vm.openSheet(Sheet.CategoryEdit(null)) }, modifier = Modifier.testTag("add_category")) { Icon(Ic.plus, contentDescription = "Add category") }
            }
        }
        item {
            Text(
                "Tap a category to rename it or change its icon and colour. When you recategorize a transaction, SpendLens can remember it as a rule" +
                    if (rules > 0) " (you have $rules)." else ".",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        item {
            Panel(padding = 8.dp) {
                cats.filter { !it.archived }.forEachIndexed { i, c ->
                    if (i > 0) Divider()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CategoryTile(c)
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall)
                            val kind = when (c.kind) {
                                com.spendlens.app.data.CategoryKind.EXPENSE -> ""
                                com.spendlens.app.data.CategoryKind.INCOME -> "Income · "
                                else -> "Not counted in spend · "
                            }
                            Text(kind + (spentBy[c.id]?.let { "${Money.formatInr(it)} this month" } ?: "Nothing this month"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.openSheet(Sheet.CategoryEdit(c.id)) }) { Icon(Ic.edit, contentDescription = "Edit ${c.name}") }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { vm.openSheet(Sheet.CategoryEdit(null)) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Add category") }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, platform: Platform) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val keyProtection by vm.keyProtection.collectAsStateWithLifecycle()
    val settings = s ?: return
    val f = settings.filters
    val sc = LocalStatusColors.current
    val granted = vm.smsPermissionGranted()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Settings", onBack = { vm.back() })

        SectionLabel("Security")
        Panel(padding = 12.dp) {
            SwitchRow("App lock", "Fingerprint, face or screen lock · locks a minute after you leave", settings.appLock, { on ->
                if (on) platform.authenticate("Turn on app lock", "Confirm it's you", onSuccess = { vm.setAppLock(true) }, onFail = { msg -> vm.messages.tryEmit(com.spendlens.app.ui.UiMessage(msg)) })
                else vm.setAppLock(false)
            }, Ic.lock)
            Divider()
            SwitchRow("Screen privacy", "Blocks screenshots and hides SpendLens in Recents", settings.screenPrivacy, { vm.setScreenPrivacy(it) }, Ic.eye)
            if (settings.appLock) {
                Divider()
                NavRow(Ic.finger, "Lock now") { vm.lockNow() }
            }
            Divider()
            NavRow(Ic.shield, "Encryption key", "AES-256 \u00b7 key held in $keyProtection", trailing = {}) { }
        }

        SectionLabel("SMS import")
        Panel(padding = 12.dp) {
            NavRow(
                Ic.message, "SMS access",
                when {
                    settings.smsEnabled && granted -> "Allowed · last scan " + if (settings.lastSyncAt > 0) java.time.Instant.ofEpochMilli(settings.lastSyncAt).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)) else "never"
                    settings.smsEnabled -> "Permission was removed in Android settings"
                    else -> "Off · manual mode"
                },
            ) { if (settings.smsEnabled && granted) platform.openAppSettings() else vm.go(Screen.DISCLOSURE) }
            Divider()
            NavRow(
                Ic.filter, "What to import",
                "${f.rangeMonths} months · " + (if (f.disabledSenders.isEmpty()) "all senders" else "${f.disabledSenders.size} sender${if (f.disabledSenders.size == 1) "" else "s"} left out") +
                    if (f.skipWords.isNotEmpty()) " · ${f.skipWords.size} skip words" else "",
            ) { if (settings.smsEnabled && granted) vm.startImport(ImportMode.SETTINGS) else vm.go(Screen.DISCLOSURE) }
            Divider()
            NavRow(Ic.refresh, "Rescan inbox", "Also runs when you open the app and every 6 hours", trailing = {}) { vm.rescan() }
            storage.bySenderGroup.entries.sortedByDescending { it.value }.forEach { (g, n) ->
                Divider()
                NavRow(Ic.bank, Institutions.nameFor(g), (if (g in f.disabledSenders) "Paused" else "Importing") + " · $n stored", trailing = {}) { }
            }
        }

        SectionLabel("Your data")
        Panel(padding = 12.dp) {
            NavRow(
                Ic.database,
                if (storage.total > 0) "${storage.total} transactions stored" else "Nothing stored yet",
                storage.oldestMs?.let { "Since " + vm.dateOf(it).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)) + " · encrypted on this phone" } ?: "Import or add expenses to get started",
                iconTint = MaterialTheme.colorScheme.primary, iconBg = MaterialTheme.colorScheme.primaryContainer, trailing = {},
            ) { }
            Divider()
            NavRow(Ic.trash, "Delete transactions", "By age, by bank or card, or everything from SMS", iconTint = sc.overFg, iconBg = sc.overBg) { vm.openDelete() }
            Divider()
            NavRow(Ic.reset, "Reset SpendLens", "Erase everything on this phone and start again", iconTint = sc.overFg, iconBg = sc.overBg, titleColor = sc.overFg) { vm.requestReset() }
        }

        SectionLabel("About")
        Panel(padding = 12.dp) {
            NavRow(Ic.doc, "Privacy policy", "What SpendLens reads, keeps and never does") { vm.go(Screen.PRIVACY) }
            Divider()
            val context = androidx.compose.ui.platform.LocalContext.current
            val version = androidx.compose.runtime.remember {
                @Suppress("DEPRECATION")
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
            }
            NavRow(Ic.info, "Version", version, trailing = {}) { }
        }
    }
}

@Composable
fun InsightsScreen(vm: AppViewModel) {
    val ins by vm.insights.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val catMap = cats.associateBy { it.id }
    val sc = LocalStatusColors.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("Insights", onBack = { vm.back() }) }
        val data = ins
        if (data == null) {
            item { Text("Loading…", modifier = Modifier.padding(16.dp)) }
            return@LazyColumn
        }
        item {
            Panel {
                Text("Monthly spend", style = MaterialTheme.typography.titleMedium)
                Text(
                    data.average?.let { "Average ${Money.formatInr(it)} over earlier months" } ?: "Earlier months show here once they're imported",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(12.dp)
                val maxV = (data.bars.filter { it.hasData }.maxOfOrNull { it.spent } ?: 0L).coerceAtLeast((data.budget * 11) / 10).coerceAtLeast(1L)
                val barColor = MaterialTheme.colorScheme.primary
                val pastColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                val lineColor = MaterialTheme.colorScheme.onSurface
                val overColor = sc.overRing
                val desc = data.bars.joinToString { "${it.label}: " + if (it.hasData) Money.formatInr(it.spent) else "no data" }
                Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "Monthly spend. $desc" }) {
                    val n = data.bars.size
                    val gap = 10.dp.toPx()
                    val w = (size.width - gap * (n - 1)) / n
                    data.bars.forEachIndexed { i, b ->
                        if (!b.hasData) return@forEachIndexed
                        val h = (b.spent.toFloat() / maxV) * size.height
                        val color = if (data.budget > 0 && b.spent > data.budget) overColor else if (b.isCurrent) barColor else pastColor
                        drawRoundRect(color, topLeft = Offset(i * (w + gap), size.height - h), size = Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx()))
                    }
                    if (data.budget > 0) {
                        val y = size.height - (data.budget.toFloat() / maxV) * size.height
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                    }
                }
                Gap(6.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    data.bars.forEach { b ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(b.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (b.hasData) Money.formatShort(b.spent) else "–", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Gap(6.dp)
                Text(
                    (if (data.budget > 0) "Dashed line: your ${Money.formatInr(data.budget)} budget. Red bars went over. " else "") + "* is this month so far.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Panel(padding = 12.dp) {
                Text("Top merchants this month", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(4.dp))
                if (data.top.isEmpty()) Text("Nothing yet.", modifier = Modifier.padding(4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                data.top.forEachIndexed { i, t ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MerchantAvatar(vm.appIcons, t.pkg, t.name, catMap[t.categoryId], 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleSmall)
                            Text("${t.count} ${if (t.count == 1) "payment" else "payments"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Money.formatInr(t.paise), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Privacy policy", onBack = { vm.back() })
        PrivacyPara("What SpendLens reads", "With your permission, SpendLens reads SMS messages in your inbox to find bank, card, wallet and UPI transactions. It reads each message's sender, text and time. It does not read contacts, call logs, photos, location or any other data.")
        PrivacyPara("What it ignores", "Messages from people (phone-number senders), OTPs and verification codes, promotional messages, reminders and failed payments are skipped in memory and never stored.")
        PrivacyPara("What it keeps", "The transactions you choose to import, with a copy of each original message so you can check it. They are stored in a database encrypted with AES-256. Its key is protected by Android Keystore and never leaves the phone.")
        PrivacyPara("What it shares", "Nothing. SpendLens has no internet permission, no analytics, no ads and no crash-reporting service. Your data is excluded from Android cloud backup and device-to-device transfer.")
        PrivacyPara("Your choices", "You choose which senders and transactions to import, and can change that any time. You can delete single transactions, delete by age or by bank, or reset the app, which erases everything and destroys the encryption key. You can also remove SMS access in Android settings; the app keeps working with manual entries.")
        PrivacyPara("Contact", "[Your support email address goes here before publishing.]")
    }
}

@Composable
private fun PrivacyPara(title: String, body: String) {
    Panel {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Gap(4.dp)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
