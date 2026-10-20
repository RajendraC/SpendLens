package com.spendlens.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendlens.app.data.Category
import com.spendlens.app.data.Schema
import com.spendlens.app.data.Txn
import com.spendlens.app.ui.ActivityFilter
import com.spendlens.app.ui.AppViewModel
import com.spendlens.app.ui.Screen
import com.spendlens.app.ui.Sheet
import com.spendlens.app.ui.components.BudgetRing
import com.spendlens.app.ui.components.CategoryTile
import com.spendlens.app.ui.components.Divider
import com.spendlens.app.ui.components.Donut
import com.spendlens.app.ui.components.DonutSlice
import com.spendlens.app.ui.components.Dot
import com.spendlens.app.ui.components.Gap
import com.spendlens.app.ui.components.IconTile
import com.spendlens.app.ui.components.MerchantAvatar
import com.spendlens.app.ui.components.NavRow
import com.spendlens.app.ui.components.PaceBar
import com.spendlens.app.ui.components.Panel
import com.spendlens.app.ui.components.ScreenHeader
import com.spendlens.app.ui.components.SectionLabel
import com.spendlens.app.ui.components.StatusPill
import com.spendlens.app.ui.theme.Ic
import com.spendlens.app.ui.theme.LocalStatusColors
import com.spendlens.core.BudgetState
import com.spendlens.core.Direction
import com.spendlens.core.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ======================= HOME =======================

@Composable
fun HomeScreen(vm: AppViewModel) {
    val d by vm.dashboard.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val canForward by vm.canGoForward.collectAsStateWithLifecycle()
    val dash = d ?: return
    val catMap = cats.associateBy { it.id }
    val sc = LocalStatusColors.current

    LazyColumn(Modifier.fillMaxSize().testTag("home"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (dash.isCurrent) "Budget month · day ${dash.dayNumber} of ${dash.period.days}" else "Past budget month",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.previousMonth() }) { Icon(Ic.chevL, contentDescription = "Previous month") }
                        Text(dash.label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        IconButton(onClick = { vm.nextMonth() }, enabled = canForward) { Icon(Ic.chevR, contentDescription = "Next month") }
                    }
                }
                IconButton(onClick = { vm.go(Screen.INSIGHTS) }) { Icon(Ic.chart, contentDescription = "Insights") }
            }
        }

        if (settings?.smsEnabled == false) item {
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Ic.message, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text("SMS import is off", style = MaterialTheme.typography.titleSmall)
                        Text("You're in manual mode. Add expenses with +, or turn on SMS import.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Gap(10.dp)
                OutlinedButton(onClick = { vm.go(Screen.DISCLOSURE) }, modifier = Modifier.fillMaxWidth()) { Text("Turn on SMS import") }
            }
        }

        item { BudgetHero(vm, dash.spent, dash.overall, dash.status, dash.period.days - dash.dayNumber, dash.isCurrent) }

        items(dash.over, key = { "over_" + it.budgetKey }) { o ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(sc.overBg).padding(16.dp).testTag("over_banner")) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Ic.alert, contentDescription = null, tint = sc.overFg, modifier = Modifier.size(22.dp))
                    Column {
                        Text(o.title, style = MaterialTheme.typography.titleMedium, color = sc.overFg)
                        Text(o.subtitle, style = MaterialTheme.typography.bodySmall, color = sc.overFg)
                    }
                }
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.showActivity(o.categoryId) }, colors = ButtonDefaults.buttonColors(containerColor = sc.overFg, contentColor = sc.overBg)) { Text("See where it went") }
                    OutlinedButton(onClick = { vm.openSheet(Sheet.BudgetEdit(o.budgetKey)) }) { Text("Adjust budget", color = sc.overFg) }
                }
            }
        }

        if (dash.reviewCount > 0) item {
            Panel(padding = 4.dp) {
                NavRow(
                    Ic.info, "${dash.reviewCount} ${if (dash.reviewCount == 1) "transaction needs" else "transactions need"} a quick look",
                    "Unsure matches wait here until you confirm", iconTint = sc.warnFg, iconBg = sc.warnBg,
                ) { vm.showActivity(filter = ActivityFilter.REVIEW) }
            }
        }

        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Where it went", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.showActivity() }) { Text("See all") }
                }
                if (dash.byCategory.isEmpty()) {
                    Text("Nothing spent yet this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val top = dash.byCategory.take(5)
                    val rest = dash.byCategory.drop(5).sumOf { it.paise }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Donut(
                            top.map { DonutSlice(it.paise.toFloat(), Color(it.category.color)) } + if (rest > 0) listOf(DonutSlice(rest.toFloat(), Color(0xFFB9C2BC))) else emptyList(),
                            Money.formatShort(dash.spent),
                        )
                        Column(Modifier.weight(1f)) {
                            top.forEach { cs ->
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 36.dp).clickable { vm.showActivity(cs.category.id) },
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Dot(Color(cs.category.color))
                                    Text(cs.category.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(Money.formatInr(cs.paise), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            if (rest > 0) Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Dot(Color(0xFFB9C2BC))
                                Text("${dash.byCategory.size - 5} more", modifier = Modifier.weight(1f))
                                Text(Money.formatInr(rest), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        item {
            Panel(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    TextButton(onClick = { vm.showActivity() }) { Text("See all") }
                }
                if (dash.recent.isEmpty()) Text("No transactions yet. Tap + to add one.", modifier = Modifier.padding(4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                dash.recent.forEachIndexed { i, t ->
                    if (i > 0) Divider()
                    TxnRow(vm, t, catMap[t.categoryId], showDay = true)
                }
            }
        }

        item {
            Panel(padding = 4.dp) {
                NavRow(Ic.chart, "Insights", "6-month trend and top merchants", iconTint = MaterialTheme.colorScheme.primary, iconBg = MaterialTheme.colorScheme.primaryContainer) { vm.go(Screen.INSIGHTS) }
            }
        }
    }
}

@Composable
private fun BudgetHero(vm: AppViewModel, spent: Long, overall: Long, status: com.spendlens.core.BudgetStatus?, daysLeft: Int, isCurrent: Boolean) {
    val sc = LocalStatusColors.current
    Panel(modifier = Modifier.testTag("budget_hero")) {
        if (status == null) {
            Text("Spent this month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Money.formatInr(spent), style = MaterialTheme.typography.headlineMedium)
            Gap(8.dp)
            Button(onClick = { vm.openSheet(Sheet.BudgetEdit(Schema.OVERALL)) }) { Text("Set a monthly budget") }
            return@Panel
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            BudgetRing(status.fraction, status.state, "${(status.fraction * 100).toInt()}%", "of budget")
            Column(Modifier.weight(1f)) {
                StatusPill(status.state)
                Gap(6.dp)
                Text("Spent this month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.formatInr(spent), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("spent_total"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("of ${Money.formatInr(overall)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { vm.openSheet(Sheet.BudgetEdit(Schema.OVERALL)) }) { Text("Edit") }
                }
            }
        }
        val line = when {
            !isCurrent && status.state == BudgetState.OVER -> "Finished ${Money.formatInr(spent - overall)} over budget"
            !isCurrent -> "Finished ${Money.formatInr(overall - spent)} under budget"
            status.state == BudgetState.OVER -> "${Money.formatInr(spent - overall)} over budget with $daysLeft days left"
            status.state == BudgetState.HEADS_UP && status.projectedPaise > overall -> "At this pace you'll be ${Money.formatInr(status.projectedPaise - overall)} over by month end"
            status.state == BudgetState.HEADS_UP -> "You've used ${(status.fraction * 100).toInt()}% with $daysLeft days left"
            else -> "${Money.formatInr(status.safePerDayPaise)} a day is safe to spend for the next $daysLeft days"
        }
        Gap(12.dp)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(sc.bg(status.state)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(if (status.state == BudgetState.ON_TRACK) Ic.check else Ic.alert, contentDescription = null, tint = sc.fg(status.state), modifier = Modifier.size(20.dp))
            Text(line, color = sc.fg(status.state), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.testTag("budget_line"))
        }
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

fun dayLabel(vm: AppViewModel, d: LocalDate): String = when (d) {
    vm.today() -> "Today"
    vm.today().minusDays(1) -> "Yesterday"
    else -> d.format(dayFmt)
}

@Composable
fun TxnRow(vm: AppViewModel, t: Txn, category: Category?, showDay: Boolean) {
    val counted = !t.excluded && (category?.countsAsSpend ?: true)
    val credit = t.direction == Direction.CREDIT
    val whenText = if (showDay) dayLabel(vm, vm.dateOf(t.occurredAt))
    else java.time.Instant.ofEpochMilli(t.occurredAt).atZone(java.time.ZoneId.systemDefault()).format(timeFmt)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { vm.openSheet(Sheet.TxnDetail(t.id)) }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MerchantAvatar(vm.appIcons, t.merchantPackage, t.merchantName, category)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t.merchantName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (t.needsReview) UnsureBadge()
            }
            Text(
                "${category?.name ?: "Others"} · $whenText" + if (t.excluded) " · not counted" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            (if (credit) "+" else "−") + Money.formatInr(t.amountPaise),
            style = MaterialTheme.typography.titleSmall,
            color = when {
                credit -> MaterialTheme.colorScheme.primary
                counted -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

// ======================= ACTIVITY =======================

@Composable
fun ActivityScreen(vm: AppViewModel) {
    val list by vm.activity.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val filter by vm.activityFilter.collectAsStateWithLifecycle()
    val catFilter by vm.categoryFilter.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val d by vm.dashboard.collectAsStateWithLifecycle()
    val catMap = cats.associateBy { it.id }
    val groups = list.groupBy { vm.dateOf(it.occurredAt) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Activity", "${d?.label ?: ""} · ${list.size} ${if (list.size == 1) "transaction" else "transactions"}")
        OutlinedTextField(
            value = q, onValueChange = { vm.query.value = it },
            placeholder = { Text("Search merchant, category or note") },
            leadingIcon = { Icon(Ic.search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("search"),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            catFilter?.let { id ->
                InputChip(selected = true, onClick = { vm.categoryFilter.value = null }, label = { Text(catMap[id]?.name ?: "Category") },
                    trailingIcon = { Icon(Ic.close, contentDescription = "Remove category filter", modifier = Modifier.size(16.dp)) })
            }
            FilterChip(filter == ActivityFilter.ALL, { vm.activityFilter.value = ActivityFilter.ALL }, { Text("All") })
            FilterChip(filter == ActivityFilter.SPENT, { vm.activityFilter.value = ActivityFilter.SPENT }, { Text("Spent") })
            FilterChip(filter == ActivityFilter.RECEIVED, { vm.activityFilter.value = ActivityFilter.RECEIVED }, { Text("Received") })
            FilterChip(filter == ActivityFilter.REVIEW, { vm.activityFilter.value = ActivityFilter.REVIEW }, { Text("Needs review") })
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Ic.search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                    Gap(8.dp)
                    Text("No transactions match", style = MaterialTheme.typography.titleMedium)
                    Text("Try another search or filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            groups.forEach { (day, txns) ->
                item(key = "d$day") {
                    val total = txns.filter { vm.counts(it, catMap) }.sumOf { it.signedPaise }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                        Text(dayLabel(vm, day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (total > 0) Text("${Money.formatInr(total)} spent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item(key = "g$day") {
                    Panel(padding = 8.dp) {
                        txns.forEachIndexed { i, t ->
                            if (i > 0) Divider()
                            TxnRow(vm, t, catMap[t.categoryId], showDay = false)
                        }
                    }
                }
            }
        }
    }
}

// ======================= BUDGETS =======================

@Composable
fun BudgetsScreen(vm: AppViewModel) {
    val d by vm.dashboard.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dash = d ?: return
    val sc = LocalStatusColors.current
    val pace = dash.dayNumber.toDouble() / dash.period.days
    val startDay = settings?.budgetStartDay ?: 1

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Budgets", "${dash.label} · " + if (dash.isCurrent) "${dash.period.days - dash.dayNumber} days left" else "ended") }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Monthly budget", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (dash.overall > 0) "${Money.formatInr(dash.spent)} of ${Money.formatInr(dash.overall)}" else "Not set",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    OutlinedButton(onClick = { vm.openSheet(Sheet.BudgetEdit(Schema.OVERALL)) }) { Text(if (dash.overall > 0) "Edit" else "Set") }
                }
                dash.status?.let { s ->
                    Gap(12.dp)
                    PaceBar(s.fraction, pace, sc.ring(s.state, MaterialTheme.colorScheme.primary))
                    Gap(8.dp)
                    StatusPill(s.state)
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(width = 2.dp, height = 14.dp).background(MaterialTheme.colorScheme.onSurface))
                Text("The marker shows where an even pace would put you today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (dash.categoryRows.isNotEmpty()) {
            item { SectionLabel("By category") }
            item {
                Panel(padding = 12.dp) {
                    dash.categoryRows.forEachIndexed { i, r ->
                        if (i > 0) Divider()
                        Column(Modifier.fillMaxWidth().clickable { vm.openSheet(Sheet.BudgetEdit(r.category.id)) }.padding(vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CategoryTile(r.category, 36.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(r.category.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${Money.formatInr(r.spent)} of ${Money.formatInr(r.limit)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill(r.status.state)
                            }
                            Gap(8.dp)
                            PaceBar(r.status.fraction, pace, sc.ring(r.status.state, Color(r.category.color)), height = 8.dp)
                        }
                    }
                }
            }
        }
        item { SectionLabel("No budget yet") }
        item {
            Panel(padding = 12.dp) {
                dash.unbudgeted.forEachIndexed { i, cs ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryTile(cs.category, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(cs.category.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (cs.paise > 0) "${Money.formatInr(cs.paise)} spent this month" else "Nothing spent yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { vm.openSheet(Sheet.BudgetEdit(cs.category.id)) }) { Text("Set") }
                    }
                }
            }
        }
        item {
            Panel(padding = 4.dp) {
                NavRow(Ic.calendar, "Budget month starts on the ${ordinal(startDay)}", "Tap to match it to your salary day") {
                    val next = listOf(1, 5, 10, 25).let { opts -> opts[(opts.indexOf(startDay) + 1).mod(opts.size)] }
                    vm.setStartDay(next)
                }
            }
        }
    }
}

fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}
