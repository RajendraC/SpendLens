package com.spendlens.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendlens.app.ui.AppViewModel
import com.spendlens.app.ui.ImportMode
import com.spendlens.app.ui.ReviewChip
import com.spendlens.app.ui.components.Checkmark
import com.spendlens.app.ui.components.Divider
import com.spendlens.app.ui.components.Gap
import com.spendlens.app.ui.components.IconTile
import com.spendlens.app.ui.components.InfoNote
import com.spendlens.app.ui.components.Panel
import com.spendlens.app.ui.components.ScreenHeader
import com.spendlens.app.ui.components.SectionLabel
import com.spendlens.app.ui.components.SwitchRow
import com.spendlens.app.ui.theme.Ic
import com.spendlens.core.Direction
import com.spendlens.core.Institutions
import com.spendlens.core.Money
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Choose what to import": senders, range, money in/out, minimum amount, skip words. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportFiltersScreen(vm: AppViewModel) {
    val ui by vm.importUi.collectAsStateWithLifecycle()
    val onboarding = ui.mode == ImportMode.ONBOARDING
    val f = ui.draft
    val selectable = remember(ui.scan, ui.draft) { vm.selectable() }
    val senders = ui.scan?.groups.orEmpty()
    val sendersOn = senders.count { f.senderEnabled(it.id) }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            title = if (onboarding) "Choose what to import" else "Import filters",
            subtitle = if (onboarding) "Only what you tick is read and saved." else "New messages follow these choices. Re-import to apply them to past messages too.",
            onBack = { vm.back() },
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionLabel("How far back") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(3, 6, 12).forEach { m ->
                        FilterChip(
                            selected = f.rangeMonths == m,
                            onClick = { vm.updateDraft { it.copy(rangeMonths = m) } },
                            label = { Text("$m months") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item { SectionLabel("Senders found in your SMS") }
            item {
                Panel(padding = 4.dp) {
                    if (ui.scanning) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Text("Looking through your inbox…")
                        }
                    } else if (senders.isEmpty()) {
                        Text(
                            "No bank, card or UPI messages found in the last 12 months.",
                            modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        senders.forEachIndexed { i, g ->
                            if (i > 0) Divider()
                            val on = f.senderEnabled(g.id)
                            val n = vm.messagesFor(g.id)
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                    .toggleable(value = on, role = Role.Checkbox) { vm.toggleSender(g.id) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                IconTile(if (g.id == Institutions.UNRECOGNISED) Ic.info else Ic.bank, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                                Column(Modifier.weight(1f)) {
                                    Text(g.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(g.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "$n ${if (n == 1) "transaction" else "transactions"}" + if (on) "" else " · left out",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Checkmark(on)
                            }
                        }
                    }
                }
            }
            item { SectionLabel("What to include") }
            item {
                Panel(padding = 12.dp) {
                    SwitchRow("Money out", "Spends, bills, transfers, ATM", f.includeOut, { v -> vm.updateDraft { it.copy(includeOut = v) } })
                    Divider()
                    SwitchRow("Money in", "Refunds, salary, cashback", f.includeIn, { v -> vm.updateDraft { it.copy(includeIn = v) } })
                    Divider()
                    Gap(8.dp)
                    Text("Skip small amounts", style = MaterialTheme.typography.titleSmall)
                    Text("Leaves out spends below the amount you pick", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(6.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0L, 1_000L, 5_000L, 10_000L).forEach { p ->
                            FilterChip(selected = f.minAmountPaise == p, onClick = { vm.updateDraft { it.copy(minAmountPaise = p) } }, label = { Text(if (p == 0L) "Off" else "Under ${Money.formatInr(p)}") })
                        }
                    }
                    Gap(8.dp)
                    Divider()
                    Gap(10.dp)
                    Text("Skip messages containing", style = MaterialTheme.typography.titleSmall)
                    Text("Checks the merchant and the message text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(6.dp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ui.wordInput,
                            onValueChange = vm::setWordInput,
                            placeholder = { Text("e.g. wallet top-up, NEFT") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { vm.addSkipWord() }),
                            modifier = Modifier.weight(1f).testTag("skip_word"),
                        )
                        FilledTonalButton(onClick = { vm.addSkipWord() }) { Text("Add") }
                    }
                    if (f.skipWords.isNotEmpty()) {
                        Gap(6.dp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            f.skipWords.forEach { w ->
                                InputChip(
                                    selected = false,
                                    onClick = { vm.removeSkipWord(w) },
                                    label = { Text(w) },
                                    trailingIcon = { Icon(Ic.close, contentDescription = "Remove $w", modifier = Modifier.size(16.dp)) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                InfoNote(
                    "Always skipped: OTPs, promotional (-P) messages and texts from people. " +
                        "${vm.ignoredCount()} messages in this period are ignored this way and never stored.",
                    icon = Ic.shield, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (ui.scanning) "Scanning…" else if (selectable.isEmpty()) "Nothing selected yet" else "${selectable.size} transactions from $sendersOn of ${senders.size} senders",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("import_summary"),
                )
                Gap(6.dp)
                if (onboarding) {
                    Button(onClick = { vm.toReview() }, enabled = !ui.scanning, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("review_button")) {
                        Text(if (selectable.isEmpty()) "Review transactions" else "Review ${selectable.size} transactions")
                    }
                    TextButton(onClick = { vm.runImport(withReview = false) }, enabled = !ui.scanning && selectable.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("import_all")) {
                        Text("Skip review and import all")
                    }
                } else {
                    Button(onClick = { vm.saveFiltersOnly() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save filters") }
                    TextButton(onClick = { vm.toReview() }, enabled = !ui.scanning, modifier = Modifier.fillMaxWidth()) { Text("Save and re-import past messages") }
                }
            }
        }
    }
}

/** Every transaction found, newest first, with a tick box. Untick anything you don't want saved. */
@Composable
fun ImportReviewScreen(vm: AppViewModel) {
    val ui by vm.importUi.collectAsStateWithLifecycle()
    val all = remember(ui.scan, ui.draft) { vm.selectable() }
    val shown = remember(all, ui.chip) {
        all.filter {
            when (ui.chip) {
                ReviewChip.ALL -> true
                ReviewChip.OUT -> it.tx.direction == Direction.DEBIT
                ReviewChip.IN -> it.tx.direction == Direction.CREDIT
                ReviewChip.UNSURE -> it.tx.needsReview
            }
        }
    }
    val byMonth = remember(shown) { shown.groupBy { vm.monthOf(it) } }
    val selected = all.count { it.smsId !in ui.reviewOff && vm.monthOf(it) !in ui.monthsOff }
    val allShownOn = shown.all { it.smsId !in ui.reviewOff && vm.monthOf(it) !in ui.monthsOff }
    val unsure = all.count { it.tx.needsReview }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Review before importing", "$selected of ${all.size} selected · tap any to leave it out", onBack = { vm.back() })
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(ui.chip == ReviewChip.ALL, { vm.setChip(ReviewChip.ALL) }, { Text("All") })
            FilterChip(ui.chip == ReviewChip.OUT, { vm.setChip(ReviewChip.OUT) }, { Text("Money out") })
            FilterChip(ui.chip == ReviewChip.IN, { vm.setChip(ReviewChip.IN) }, { Text("Money in") })
            FilterChip(ui.chip == ReviewChip.UNSURE, { vm.setChip(ReviewChip.UNSURE) }, { Text("Unsure · $unsure") })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${shown.size} shown", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                vm.setReviewed(shown.map { it.smsId }, selected = !allShownOn)
                if (!allShownOn) byMonth.keys.filter { it in ui.monthsOff }.forEach { vm.toggleMonth(it) }
            }) { Text(if (allShownOn) "Clear all" else "Select all") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            byMonth.forEach { (month, monthItems) ->
                item(key = "m$month") {
                    val monthOn = month !in ui.monthsOff
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = monthOn, role = Role.Checkbox) { vm.toggleMonth(month) }.padding(start = 6.dp, top = 14.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Checkmark(monthOn)
                        Text(
                            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)) + " · ${monthItems.size}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
                items(monthItems, key = { it.smsId }) { cand ->
                    val on = cand.smsId !in ui.reviewOff && month !in ui.monthsOff
                    Panel(padding = 12.dp, modifier = Modifier.padding(vertical = 4.dp).toggleable(value = on, enabled = month !in ui.monthsOff, role = Role.Checkbox) { vm.toggleReview(cand.smsId) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Checkmark(on)
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(cand.merchantName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    val credit = cand.tx.direction == Direction.CREDIT
                                    Text(
                                        (if (credit) "+" else "−") + Money.formatInr(cand.tx.amountPaise),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (credit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        cand.tx.sender + " · " + java.time.Instant.ofEpochMilli(cand.tx.timestamp).atZone(java.time.ZoneId.systemDefault()).format(fmt),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    if (cand.tx.needsReview) UnsureBadge()
                                }
                                Text(
                                    cand.tx.body.replace('\n', ' '),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            if (shown.isEmpty()) item { Text("Nothing here with this filter.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Surface(tonalElevation = 3.dp) {
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(onClick = { vm.runImport(withReview = true) }, enabled = selected > 0 || ui.mode == ImportMode.SETTINGS, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("import_selected")) {
                    Text(if (selected > 0) "Import $selected transactions" else "Nothing selected")
                }
            }
        }
    }
}

@Composable
fun UnsureBadge() {
    val sc = com.spendlens.app.ui.theme.LocalStatusColors.current
    Text(
        "Unsure",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
        color = sc.warnFg,
        modifier = Modifier.background(sc.warnBg, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun ImportingScreen(vm: AppViewModel) {
    val ui by vm.importUi.collectAsStateWithLifecycle()
    val r = ui.result
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Gap(24.dp)
        Text(
            if (!ui.running && r != null) "All set" else if (ui.mode == ImportMode.ONBOARDING) "Importing your transactions" else "Re-importing past messages",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            if (!ui.running && r != null) "Your transactions are saved, encrypted, on this phone." else "Reading only the messages you selected. Nothing leaves this phone.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Panel {
            Row(Modifier.fillMaxWidth()) {
                Text(if (ui.running) "Saving…" else "Import complete", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${(ui.progress * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
            }
            Gap(10.dp)
            LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth())
            if (r != null) {
                Gap(14.dp)
                StatRow("Transactions saved", r.saved.toString())
                StatRow("Need a quick look", r.needsReview.toString())
                StatRow("Duplicates merged", r.duplicates.toString())
                StatRow("Left out by you", r.leftOut.toString())
                if (r.removed > 0) StatRow("Removed by new filters", r.removed.toString())
                if (r.byGroup.isNotEmpty()) {
                    Gap(10.dp)
                    Divider()
                    Gap(10.dp)
                    Text("SAVED BY SENDER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    r.byGroup.entries.sortedByDescending { it.value }.forEach { (g, n) -> StatRow(Institutions.nameFor(g), "$n") }
                }
            }
        }
        if (!ui.running && r != null) {
            Button(onClick = { vm.finishImport() }, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("import_continue")) { Text("Continue") }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
