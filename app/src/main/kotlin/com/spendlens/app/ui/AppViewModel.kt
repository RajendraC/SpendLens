package com.spendlens.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendlens.app.AppContainer
import com.spendlens.app.data.AppSettings
import com.spendlens.app.data.Category
import com.spendlens.app.data.Repository
import com.spendlens.app.data.Schema
import com.spendlens.app.data.Txn
import com.spendlens.app.sms.ImportEngine
import com.spendlens.core.BudgetMath
import com.spendlens.core.BudgetPeriod
import com.spendlens.core.BudgetState
import com.spendlens.core.BudgetStatus
import com.spendlens.core.Direction
import com.spendlens.core.ImportFilters
import com.spendlens.core.Institutions
import com.spendlens.core.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Tab { HOME, ACTIVITY, BUDGETS, MORE }

enum class Screen(val tab: Tab? = null) {
    WELCOME, DISCLOSURE, IMPORT_FILTERS, IMPORT_REVIEW, IMPORTING, BUDGET_SETUP,
    HOME(Tab.HOME), ACTIVITY(Tab.ACTIVITY), BUDGETS(Tab.BUDGETS), MORE(Tab.MORE),
    CATEGORIES, SETTINGS, INSIGHTS, PRIVACY, DB_ERROR,
}

sealed interface Sheet {
    data class TxnDetail(val id: Long) : Sheet
    data class CategoryPick(val txId: Long) : Sheet
    data class BudgetEdit(val key: String) : Sheet
    data class CategoryEdit(val id: String?) : Sheet
    data object AddExpense : Sheet
    data object DeleteData : Sheet
}

enum class ConfirmKind { DELETE, RESET }
enum class ImportMode { ONBOARDING, SETTINGS }
enum class ReviewChip { ALL, OUT, IN, UNSURE }
enum class DeleteOption { OLDER_3, OLDER_6, ACCOUNT, ALL_SMS }
enum class ActivityFilter { ALL, SPENT, RECEIVED, REVIEW }

class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

data class CategorySpend(val category: Category, val paise: Long)
data class CategoryBudgetRow(val category: Category, val spent: Long, val limit: Long, val status: BudgetStatus)
data class OverItem(val title: String, val subtitle: String, val categoryId: String?, val budgetKey: String)

data class Dashboard(
    val period: BudgetPeriod,
    val label: String,
    val isCurrent: Boolean,
    val dayNumber: Int,
    val spent: Long,
    val overall: Long,
    val status: BudgetStatus?,
    val byCategory: List<CategorySpend>,
    val recent: List<Txn>,
    val reviewCount: Int,
    val over: List<OverItem>,
    val categoryRows: List<CategoryBudgetRow>,
    val unbudgeted: List<CategorySpend>,
)

data class MonthBar(val label: String, val spent: Long, val hasData: Boolean, val isCurrent: Boolean)
data class TopMerchant(val name: String, val pkg: String?, val categoryId: String, val paise: Long, val count: Int)
data class Insights(val bars: List<MonthBar>, val budget: Long, val average: Long?, val top: List<TopMerchant>)

data class ImportUi(
    val mode: ImportMode = ImportMode.ONBOARDING,
    val scanning: Boolean = false,
    val scan: ImportEngine.Scan? = null,
    val draft: ImportFilters = ImportFilters(),
    val wordInput: String = "",
    val reviewOff: Set<Long> = emptySet(),
    val monthsOff: Set<YearMonth> = emptySet(),
    val chip: ReviewChip = ReviewChip.ALL,
    val running: Boolean = false,
    val progress: Float = 0f,
    val result: ImportEngine.Result? = null,
)

data class DeleteUi(
    val option: DeleteOption = DeleteOption.OLDER_3,
    val group: String? = null,
    val stopImporting: Boolean = true,
    val counts: Map<DeleteOption, Int> = emptyMap(),
    val groups: List<Pair<String, Int>> = emptyList(),
) {
    val selectedCount: Int get() = counts[option] ?: 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val c: AppContainer) : ViewModel() {

    private val repo = c.repo
    private val engine = c.importEngine
    private val zone: ZoneId get() = c.clock.zone
    fun today(): LocalDate = LocalDate.now(c.clock)
    fun now(): Long = c.clock.millis()
    val appIcons get() = c.appIcons

    // ---------------- navigation ----------------

    private val _screen = MutableStateFlow<Screen?>(null)
    val screen: StateFlow<Screen?> = _screen.asStateFlow()
    private val history = ArrayDeque<Screen>()

    private val _sheet = MutableStateFlow<Sheet?>(null)
    val sheet: StateFlow<Sheet?> = _sheet.asStateFlow()

    private val _confirm = MutableStateFlow<ConfirmKind?>(null)
    val confirm: StateFlow<ConfirmKind?> = _confirm.asStateFlow()

    val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val locked = MutableStateFlow(false)
    private var backgroundAt: Long? = null

    fun go(s: Screen) {
        val cur = _screen.value
        if (cur != null && cur != s) {
            if (s.tab != null) history.clear()
            else if (cur != Screen.IMPORTING) history.addLast(cur)
        }
        _screen.value = s
    }

    /** Returns false when there is nowhere to go back to, so the system can close the app. */
    fun back(): Boolean {
        if (_confirm.value != null) { _confirm.value = null; return true }
        if (_sheet.value != null) { closeSheet(); return true }
        val cur = _screen.value ?: return false
        if (cur == Screen.IMPORTING) return true
        if (history.isNotEmpty()) { _screen.value = history.removeLast(); return true }
        if (cur.tab != null && cur != Screen.HOME) { _screen.value = Screen.HOME; return true }
        return false
    }

    fun openSheet(s: Sheet) { _sheet.value = s }
    fun closeSheet() { _sheet.value = null }
    fun dismissConfirm() { _confirm.value = null }

    private fun say(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        messages.tryEmit(UiMessage(text, actionLabel, action))
    }

    // ---------------- data ----------------

    val settings: StateFlow<AppSettings?> = repo.observeSettings()
        .catch { _screen.value = Screen.DB_ERROR }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val categories: StateFlow<List<Category>> = repo.observeCategories()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val budgets: StateFlow<Map<String, Long>> = repo.observeBudgets()
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val storage: StateFlow<Repository.Storage> = repo.observeStorage()
        .catch { emit(Repository.Storage(0, null, emptyMap())) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Repository.Storage(0, null, emptyMap()))

    val ruleCount: StateFlow<Int> = repo.observeRuleCount().catch { emit(0) }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val monthOffset = MutableStateFlow(0)

    val period: StateFlow<BudgetPeriod> = combine(settings, monthOffset) { s, off -> periodAt(s?.budgetStartDay ?: 1, off) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, periodAt(1, 0))

    val canGoForward: StateFlow<Boolean> = monthOffset.map { it < 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun previousMonth() = monthOffset.update { (it - 1).coerceAtLeast(-24) }
    fun nextMonth() = monthOffset.update { (it + 1).coerceAtMost(0) }

    val periodTxns: StateFlow<List<Txn>> = period
        .flatMapLatest { p -> repo.observeTransactions(startMs(p.start), startMs(p.endExclusive)) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dashboard: StateFlow<Dashboard?> = combine(periodTxns, categories, budgets, period) { t, cats, b, p -> buildDashboard(t, cats, b, p) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun txnFlow(id: Long): Flow<Txn?> = repo.observeTransaction(id)

    // Activity tab
    val query = MutableStateFlow("")
    val activityFilter = MutableStateFlow(ActivityFilter.ALL)
    val categoryFilter = MutableStateFlow<String?>(null)

    val activity: StateFlow<List<Txn>> = combine(periodTxns, query, activityFilter, categoryFilter, categories) { txns, q, f, cf, cats ->
        val names = cats.associate { it.id to it.name.lowercase() }
        val needle = q.trim().lowercase()
        txns.filter { t ->
            (f != ActivityFilter.SPENT || t.direction == Direction.DEBIT) &&
                (f != ActivityFilter.RECEIVED || t.direction == Direction.CREDIT) &&
                (f != ActivityFilter.REVIEW || t.needsReview) &&
                (cf == null || t.categoryId == cf) &&
                (needle.isEmpty() || t.merchantName.lowercase().contains(needle) || (names[t.categoryId] ?: "").contains(needle) ||
                    (t.note ?: "").lowercase().contains(needle))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun showActivity(category: String? = null, filter: ActivityFilter = ActivityFilter.ALL) {
        categoryFilter.value = category
        activityFilter.value = filter
        query.value = ""
        go(Screen.ACTIVITY)
    }

    val insights: StateFlow<Insights?> = combine(settings, budgets) { s, b -> (s?.budgetStartDay ?: 1) to (b[Schema.OVERALL] ?: 0L) }
        .flatMapLatest { (startDay, budget) ->
            val periods = (0 downTo -5).map { periodAt(startDay, it) }.reversed()
            repo.observeTransactions(startMs(periods.first().start), startMs(periods.last().endExclusive)).map<List<Txn>, Insights?> { txns ->
                buildInsights(txns, categories.value, periods, budget)
            }
        }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // ---------------- startup, lock, sync ----------------

    init {
        viewModelScope.launch {
            try {
                repo.open()
            } catch (e: Throwable) {
                _screen.value = Screen.DB_ERROR
                return@launch
            }
            launch(Dispatchers.IO) { keyProtection.value = runCatching { c.keyProtection() }.getOrDefault("Android Keystore") }
            val s = repo.settings()
            if (s.appLock) locked.value = true
            _screen.value = if (s.onboarded) Screen.HOME else Screen.WELCOME
            if (s.onboarded) syncQuietly()
        }
    }

    fun onAppBackground() { backgroundAt = now() }

    fun onAppForeground() {
        val s = settings.value ?: return
        val at = backgroundAt
        if (s.appLock && at != null && now() - at > LOCK_AFTER_MS) locked.value = true
        backgroundAt = null
        if (s.onboarded && !locked.value) syncQuietly()
    }

    fun lockNow() { if (settings.value?.appLock == true) locked.value = true }
    fun unlocked() { locked.value = false }

    fun setAppLock(on: Boolean) = viewModelScope.launch {
        repo.updateSettings { it.copy(appLock = on) }
        say(if (on) "App lock on. SpendLens locks a minute after you leave it." else "App lock off")
    }

    fun setScreenPrivacy(on: Boolean) = viewModelScope.launch { repo.updateSettings { it.copy(screenPrivacy = on) } }

    fun setStartDay(day: Int) = viewModelScope.launch {
        repo.updateSettings { it.copy(budgetStartDay = day.coerceIn(1, 28)) }
        monthOffset.value = 0
    }

    private fun syncQuietly() = viewModelScope.launch { runCatching { engine.syncNew() } }

    fun rescan() = viewModelScope.launch {
        val s = settings.value
        if (s == null || !s.smsEnabled || !c.sms.hasPermission()) { say("Turn on SMS access first"); return@launch }
        val n = runCatching { engine.syncNew() }.getOrDefault(0)
        say(if (n == 0) "Scanned your inbox: no new transactions" else "Added $n new ${if (n == 1) "transaction" else "transactions"}")
    }

    fun smsPermissionGranted(): Boolean = c.sms.hasPermission()

    // ---------------- onboarding & import ----------------

    val importUi = MutableStateFlow(ImportUi())

    fun chooseManualMode() = viewModelScope.launch {
        repo.updateSettings { it.copy(smsEnabled = false) }
        val s = repo.settings()
        if (s.onboarded) {
            _screen.value = Screen.SETTINGS
            say("SMS import is off. You can still add expenses yourself.")
        } else {
            go(Screen.BUDGET_SETUP)
            say("No problem. You're in manual mode: add expenses with +.")
        }
    }

    fun onSmsPermission(granted: Boolean) {
        if (granted) startImport(if (settings.value?.onboarded == true) ImportMode.SETTINGS else ImportMode.ONBOARDING)
        else {
            chooseManualMode()
        }
    }

    fun startImport(mode: ImportMode) {
        val current = settings.value?.filters ?: ImportFilters()
        importUi.value = ImportUi(mode = mode, scanning = true, draft = current)
        go(Screen.IMPORT_FILTERS)
        viewModelScope.launch {
            val scan = runCatching { engine.scan() }.getOrElse { ImportEngine.Scan(emptyList(), emptyList(), emptyList()) }
            importUi.update { it.copy(scanning = false, scan = scan) }
        }
    }

    fun updateDraft(transform: (ImportFilters) -> ImportFilters) = importUi.update { it.copy(draft = transform(it.draft)) }
    fun setWordInput(s: String) = importUi.update { it.copy(wordInput = s) }

    fun addSkipWord() {
        val ui = importUi.value
        val w = ui.wordInput.trim()
        when {
            w.isEmpty() -> say("Type a word or phrase first")
            ui.draft.skipWords.any { it.equals(w, ignoreCase = true) } -> say("“$w” is already on the list")
            else -> importUi.update { it.copy(draft = it.draft.copy(skipWords = it.draft.skipWords + w), wordInput = "") }
        }
    }

    fun removeSkipWord(w: String) = updateDraft { it.copy(skipWords = it.skipWords - w) }

    fun toggleSender(group: String) = updateDraft { f ->
        f.copy(disabledSenders = if (group in f.disabledSenders) f.disabledSenders - group else f.disabledSenders + group)
    }

    fun selectable(): List<ImportEngine.Candidate> {
        val ui = importUi.value
        val scan = ui.scan ?: return emptyList()
        return engine.selectable(scan, ui.draft)
    }

    fun monthOf(cand: ImportEngine.Candidate): YearMonth = engine.monthOf(cand)
    fun messagesFor(group: String): Int = importUi.value.scan?.let { engine.messagesInRange(it, importUi.value.draft, group) } ?: 0
    fun ignoredCount(): Int = importUi.value.scan?.let { engine.ignoredInRange(it, importUi.value.draft) } ?: 0

    fun toReview() {
        if (selectable().isEmpty()) { say("Nothing selected. Tick a sender, and money in or money out."); return }
        importUi.update { it.copy(chip = ReviewChip.ALL) }
        go(Screen.IMPORT_REVIEW)
    }

    fun setChip(chip: ReviewChip) = importUi.update { it.copy(chip = chip) }

    fun toggleReview(smsId: Long) = importUi.update { ui ->
        ui.copy(reviewOff = if (smsId in ui.reviewOff) ui.reviewOff - smsId else ui.reviewOff + smsId)
    }

    fun toggleMonth(m: YearMonth) = importUi.update { ui ->
        ui.copy(monthsOff = if (m in ui.monthsOff) ui.monthsOff - m else ui.monthsOff + m)
    }

    /** Select or clear every transaction in [smsIds]. */
    fun setReviewed(smsIds: Collection<Long>, selected: Boolean) = importUi.update { ui ->
        ui.copy(reviewOff = if (selected) ui.reviewOff - smsIds.toSet() else ui.reviewOff + smsIds)
    }

    fun chosenForImport(withReview: Boolean): List<ImportEngine.Candidate> {
        val ui = importUi.value
        val all = selectable()
        return if (!withReview) all else all.filter { it.smsId !in ui.reviewOff && engine.monthOf(it) !in ui.monthsOff }
    }

    fun runImport(withReview: Boolean) {
        val ui = importUi.value
        val all = selectable()
        val chosen = chosenForImport(withReview)
        if (chosen.isEmpty() && ui.mode == ImportMode.ONBOARDING) { say("Tick at least one transaction"); return }
        importUi.update { it.copy(running = true, progress = 0f, result = null) }
        _screen.value = Screen.IMPORTING
        viewModelScope.launch {
            val result = runCatching {
                engine.commit(chosen, ui.draft, all.size - chosen.size, replaceRange = ui.mode == ImportMode.SETTINGS) { p ->
                    importUi.update { it.copy(progress = p) }
                }
            }.getOrElse {
                say("Import stopped: ${it.message ?: "unknown error"}")
                ImportEngine.Result(0, 0, 0, 0, 0, emptyMap())
            }
            importUi.update { it.copy(running = false, progress = 1f, result = result) }
        }
    }

    fun finishImport() {
        val mode = importUi.value.mode
        history.clear()
        if (mode == ImportMode.ONBOARDING) {
            _screen.value = if (budgets.value[Schema.OVERALL] != null && settings.value?.onboarded == true) Screen.HOME else Screen.BUDGET_SETUP
        } else {
            history.addLast(Screen.MORE)
            _screen.value = Screen.SETTINGS
            say("Re-import complete")
        }
    }

    fun saveFiltersOnly() = viewModelScope.launch {
        val f = importUi.value.draft
        repo.updateSettings { it.copy(filters = f) }
        history.clear(); history.addLast(Screen.MORE)
        _screen.value = Screen.SETTINGS
        say("Saved. New messages will follow these filters.")
    }

    fun finishOnboarding(budgetPaise: Long) = viewModelScope.launch {
        repo.setBudget(Schema.OVERALL, budgetPaise)
        repo.updateSettings { it.copy(onboarded = true) }
        history.clear()
        _screen.value = Screen.HOME
    }

    // ---------------- transactions ----------------

    fun setCategory(txId: Long, categoryId: String, remember: Boolean) = viewModelScope.launch {
        val t = repo.transaction(txId) ?: return@launch
        val n = repo.setCategory(txId, categoryId, remember)
        val name = categories.value.firstOrNull { it.id == categoryId }?.name ?: "category"
        _sheet.value = Sheet.TxnDetail(txId)
        say(if (remember) "Rule saved: ${t.merchantName} → $name" + (if (n > 1) " ($n updated)" else "") else "Moved to $name")
    }

    fun setNote(txId: Long, note: String) = viewModelScope.launch { repo.setNote(txId, note) }
    fun setExcluded(txId: Long, excluded: Boolean) = viewModelScope.launch { repo.setExcluded(txId, excluded) }

    fun confirmReview(txId: Long) = viewModelScope.launch {
        repo.confirmReview(txId)
        say("Confirmed")
    }

    fun deleteTxn(txId: Long) = viewModelScope.launch {
        val t = repo.deleteTransaction(txId) ?: return@launch
        closeSheet()
        say("Transaction deleted", "Undo") { viewModelScope.launch { repo.restoreTransaction(t); say("Transaction restored") } }
    }

    fun addManual(amountPaise: Long, merchant: String, categoryId: String, daysAgo: Int) = viewModelScope.launch {
        val day = today().minusDays(daysAgo.toLong())
        val at = if (daysAgo == 0) now() else day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        repo.addManual(ImportEngine.manual(amountPaise, merchant, categoryId, at))
        closeSheet()
        val name = categories.value.firstOrNull { it.id == categoryId }?.name ?: ""
        say("Added ${Money.formatInr(amountPaise)} to $name")
    }

    // ---------------- budgets & categories ----------------

    fun saveBudget(key: String, paise: Long?) = viewModelScope.launch {
        repo.setBudget(key, paise)
        closeSheet()
        say(if (paise == null || paise <= 0) "Budget removed" else "Budget set to ${Money.formatInr(paise)}")
    }

    fun saveCategory(id: String?, name: String, icon: String, color: Long, countsAsSpend: Boolean) = viewModelScope.launch {
        if (name.isBlank()) { say("Give the category a name"); return@launch }
        repo.saveCategory(id, name.trim(), icon, color, countsAsSpend)
        closeSheet()
        say(if (id == null) "${name.trim()} added" else "Category updated")
    }

    fun archiveCategory(id: String) = viewModelScope.launch {
        if (id == com.spendlens.core.Cat.OTHERS || id == com.spendlens.core.Cat.INCOME) { say("This category can't be archived"); return@launch }
        val name = categories.value.firstOrNull { it.id == id }?.name ?: "Category"
        val moved = repo.archiveCategory(id)
        closeSheet()
        say("$name archived" + if (moved > 0) " · $moved moved to Others" else "")
    }

    // ---------------- delete & reset ----------------

    val deleteUi = MutableStateFlow(DeleteUi())

    fun openDelete() {
        _sheet.value = Sheet.DeleteData
        refreshDeleteCounts()
    }

    private fun cutoff(months: Long) = startMs(today().minusMonths(months))

    private fun refreshDeleteCounts() = viewModelScope.launch {
        val groups = storage.value.bySenderGroup.entries.sortedByDescending { it.value }.map { it.key to it.value }
        val group = deleteUi.value.group?.takeIf { g -> groups.any { it.first == g } } ?: groups.firstOrNull()?.first
        val counts = mapOf(
            DeleteOption.OLDER_3 to repo.countSmsBefore(cutoff(3)),
            DeleteOption.OLDER_6 to repo.countSmsBefore(cutoff(6)),
            DeleteOption.ACCOUNT to (group?.let { repo.countSmsInGroup(it) } ?: 0),
            DeleteOption.ALL_SMS to repo.countAllSms(),
        )
        deleteUi.update { it.copy(counts = counts, groups = groups, group = group) }
    }

    fun setDeleteOption(o: DeleteOption) { deleteUi.update { it.copy(option = o) }; refreshDeleteCounts() }
    fun setDeleteGroup(g: String) { deleteUi.update { it.copy(group = g) }; refreshDeleteCounts() }
    fun setStopImporting(on: Boolean) = deleteUi.update { it.copy(stopImporting = on) }

    fun requestDelete() {
        if (deleteUi.value.selectedCount == 0) { say("Nothing to delete for this choice"); return }
        _confirm.value = ConfirmKind.DELETE
    }

    fun confirmDelete() = viewModelScope.launch {
        val d = deleteUi.value
        val n = when (d.option) {
            DeleteOption.OLDER_3 -> repo.deleteSmsBefore(cutoff(3))
            DeleteOption.OLDER_6 -> repo.deleteSmsBefore(cutoff(6))
            DeleteOption.ACCOUNT -> d.group?.let { g ->
                val removed = repo.deleteSmsInGroup(g)
                if (d.stopImporting) repo.updateSettings { s -> s.copy(filters = s.filters.copy(disabledSenders = s.filters.disabledSenders + g)) }
                removed
            } ?: 0
            DeleteOption.ALL_SMS -> repo.deleteAllSms()
        }
        _confirm.value = null
        closeSheet()
        say("Deleted $n ${if (n == 1) "transaction" else "transactions"}")
    }

    fun requestReset() { _confirm.value = ConfirmKind.RESET }

    /** Erases the database and its key, then starts again at the welcome screen. */
    fun performReset() = viewModelScope.launch {
        _confirm.value = null
        _sheet.value = null
        withContext(Dispatchers.IO) { c.eraseEverything() }
        importUi.value = ImportUi()
        deleteUi.value = DeleteUi()
        monthOffset.value = 0
        locked.value = false
        history.clear()
        _screen.value = Screen.WELCOME
        say("SpendLens was reset. Everything it stored on this phone is gone.")
    }

    /** Where the database key lives, worked out once off the main thread (Keystore calls are slow). */
    val keyProtection = MutableStateFlow("Checking\u2026")

    // ---------------- helpers ----------------

    fun startMs(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

    fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun periodAt(startDay: Int, offset: Int): BudgetPeriod {
        var p = BudgetMath.periodFor(today(), startDay)
        repeat(-offset) { p = p.previous(startDay) }
        return p
    }

    fun periodLabel(p: BudgetPeriod): String =
        if (p.start.dayOfMonth == 1) p.start.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        else p.start.format(SHORT) + " – " + p.endExclusive.minusDays(1).format(SHORT)

    fun counts(t: Txn, cats: Map<String, Category>): Boolean = !t.excluded && (cats[t.categoryId]?.countsAsSpend ?: true)

    private fun buildDashboard(txns: List<Txn>, cats: List<Category>, budgets: Map<String, Long>, p: BudgetPeriod): Dashboard {
        val catMap = cats.associateBy { it.id }
        val today = today()
        val isCurrent = p.contains(today)
        val dayNumber = if (isCurrent) p.dayNumber(today) else p.days
        val counted = txns.filter { counts(it, catMap) }
        val spent = counted.sumOf { it.signedPaise }
        val byCat = counted.groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.signedPaise } }
        val overall = budgets[Schema.OVERALL] ?: 0L
        val status = if (overall > 0) BudgetMath.status(spent, overall, dayNumber, p.days) else null
        val byCategory = byCat.filter { it.value > 0 }.mapNotNull { (id, v) -> catMap[id]?.let { CategorySpend(it, v) } }.sortedByDescending { it.paise }
        val rows = budgets.filterKeys { it != Schema.OVERALL }.mapNotNull { (id, lim) ->
            val cat = catMap[id]?.takeIf { !it.archived } ?: return@mapNotNull null
            val sp = byCat[id] ?: 0L
            CategoryBudgetRow(cat, sp, lim, BudgetMath.status(sp, lim, dayNumber, p.days))
        }.sortedByDescending { it.spent.toDouble() / it.limit }
        val unbudgeted = cats.filter { it.countsAsSpend && !it.archived && budgets[it.id] == null }.map { CategorySpend(it, byCat[it.id] ?: 0L) }
        val daysLeftText = if (isCurrent) "${p.days - dayNumber} days left this month" else "This budget month has ended"
        val over = buildList {
            if (status?.state == BudgetState.OVER) add(OverItem("${Money.formatInr(spent - overall)} over your monthly budget", daysLeftText, null, Schema.OVERALL))
            rows.filter { it.status.state == BudgetState.OVER }.forEach {
                add(OverItem("${Money.formatInr(it.spent - it.limit)} over on ${it.category.name}", "${Money.formatInr(it.spent)} spent of ${Money.formatInr(it.limit)} · $daysLeftText", it.category.id, it.category.id))
            }
        }
        return Dashboard(
            period = p, label = periodLabel(p), isCurrent = isCurrent, dayNumber = dayNumber, spent = spent, overall = overall, status = status,
            byCategory = byCategory, recent = txns.take(5), reviewCount = txns.count { it.needsReview }, over = over,
            categoryRows = rows, unbudgeted = unbudgeted,
        )
    }

    private fun buildInsights(txns: List<Txn>, cats: List<Category>, periods: List<BudgetPeriod>, budget: Long): Insights {
        val catMap = cats.associateBy { it.id }
        val today = today()
        val bars = periods.map { p ->
            val inP = txns.filter { p.contains(dateOf(it.occurredAt)) }
            val spent = inP.filter { counts(it, catMap) }.sumOf { it.signedPaise }
            MonthBar(
                label = p.start.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)) + if (p.contains(today)) "*" else "",
                spent = spent.coerceAtLeast(0),
                hasData = inP.isNotEmpty() || p.contains(today),
                isCurrent = p.contains(today),
            )
        }
        val past = bars.filter { !it.isCurrent && it.hasData }
        val current = periods.last()
        val top = txns.filter { current.contains(dateOf(it.occurredAt)) && it.direction == Direction.DEBIT && counts(it, catMap) }
            .groupBy { it.merchantKey }
            .map { (_, list) -> TopMerchant(list.first().merchantName, list.first().merchantPackage, list.first().categoryId, list.sumOf { it.amountPaise }, list.size) }
            .sortedByDescending { it.paise }.take(5)
        return Insights(bars, budget, if (past.isEmpty()) null else past.sumOf { it.spent } / past.size, top)
    }

    fun senderName(group: String?): String = Institutions.nameFor(group)

    companion object {
        const val LOCK_AFTER_MS = 60_000L
        private val SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    }
}
