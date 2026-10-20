package com.spendlens.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendlens.app.ui.screens.ActivityScreen
import com.spendlens.app.ui.screens.BudgetSetupScreen
import com.spendlens.app.ui.screens.BudgetsScreen
import com.spendlens.app.ui.screens.CategoriesScreen
import com.spendlens.app.ui.screens.DbErrorScreen
import com.spendlens.app.ui.screens.DisclosureScreen
import com.spendlens.app.ui.screens.HomeScreen
import com.spendlens.app.ui.screens.ImportFiltersScreen
import com.spendlens.app.ui.screens.ImportReviewScreen
import com.spendlens.app.ui.screens.ImportingScreen
import com.spendlens.app.ui.screens.InsightsScreen
import com.spendlens.app.ui.screens.LockScreen
import com.spendlens.app.ui.screens.MoreScreen
import com.spendlens.app.ui.screens.PrivacyScreen
import com.spendlens.app.ui.screens.SettingsScreen
import com.spendlens.app.ui.screens.SheetHost
import com.spendlens.app.ui.screens.WelcomeScreen
import com.spendlens.app.ui.theme.Ic

@Composable
fun SpendLensRoot(vm: AppViewModel, platform: Platform) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val confirm by vm.confirm.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            val r = snackbar.showSnackbar(m.text, m.actionLabel, withDismissAction = false, duration = if (m.action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) m.action?.invoke()
        }
    }

    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val atRoot = screen == null || screen == Screen.HOME || screen == Screen.WELCOME || screen == Screen.DB_ERROR
    // At the root screens the system handles Back (and closes the app), which keeps predictive back working.
    BackHandler(enabled = !locked && (sheet != null || confirm != null || !atRoot)) {
        vm.back()
    }

    if (locked) {
        LockScreen(onUnlock = {
            platform.authenticate("Unlock SpendLens", "Use your fingerprint, face or screen lock", onSuccess = { vm.unlocked() }, onFail = { })
        }, canUseDeviceLock = platform.canUseDeviceLock(), onFallbackUnlock = { vm.unlocked() })
        return
    }

    val current = screen
    val tab = current?.tab
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tab != null) {
                NavigationBar {
                    TabItem(vm, Tab.HOME, tab, "Home", Ic.home, Screen.HOME)
                    TabItem(vm, Tab.ACTIVITY, tab, "Activity", Ic.list, Screen.ACTIVITY)
                    TabItem(vm, Tab.BUDGETS, tab, "Budgets", Ic.wallet, Screen.BUDGETS)
                    TabItem(vm, Tab.MORE, tab, "More", Ic.grid, Screen.MORE)
                }
            }
        },
        floatingActionButton = {
            if (current == Screen.HOME || current == Screen.ACTIVITY) {
                FloatingActionButton(onClick = { vm.openSheet(Sheet.AddExpense) }, modifier = Modifier.testTag("add_expense")) {
                    Icon(Ic.plus, contentDescription = "Add expense")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (current) {
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(32.dp)) }
                Screen.WELCOME -> WelcomeScreen(vm)
                Screen.DISCLOSURE -> DisclosureScreen(vm, platform)
                Screen.IMPORT_FILTERS -> ImportFiltersScreen(vm)
                Screen.IMPORT_REVIEW -> ImportReviewScreen(vm)
                Screen.IMPORTING -> ImportingScreen(vm)
                Screen.BUDGET_SETUP -> BudgetSetupScreen(vm)
                Screen.HOME -> HomeScreen(vm)
                Screen.ACTIVITY -> ActivityScreen(vm)
                Screen.BUDGETS -> BudgetsScreen(vm)
                Screen.MORE -> MoreScreen(vm)
                Screen.CATEGORIES -> CategoriesScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm, platform)
                Screen.INSIGHTS -> InsightsScreen(vm)
                Screen.PRIVACY -> PrivacyScreen(vm)
                Screen.DB_ERROR -> DbErrorScreen(vm)
            }
        }
        SheetHost(vm)
    }

    when (confirm) {
        ConfirmKind.DELETE -> {
            val d by vm.deleteUi.collectAsStateWithLifecycle()
            val n = d.selectedCount
            val what = when (d.option) {
                DeleteOption.OLDER_3 -> "Transactions older than 3 months"
                DeleteOption.OLDER_6 -> "Transactions older than 6 months"
                DeleteOption.ACCOUNT -> "All transactions from ${vm.senderName(d.group)}"
                DeleteOption.ALL_SMS -> "Every transaction imported from SMS"
            }
            AlertDialog(
                onDismissRequest = { vm.dismissConfirm() },
                icon = { Icon(Ic.trash, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Delete $n ${if (n == 1) "transaction" else "transactions"}?") },
                text = {
                    Text(
                        "$what, with their saved SMS copies, will be erased from SpendLens. This can't be undone.\n\n" +
                            "Stays: manual entries, categories, rules and budgets. Your SMS inbox isn't touched."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { vm.confirmDelete() }, modifier = Modifier.testTag("confirm_delete")) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold)
                    }
                },
                dismissButton = { TextButton(onClick = { vm.dismissConfirm() }) { Text("Cancel") } },
            )
        }
        ConfirmKind.RESET -> {
            val secure = platform.canUseDeviceLock()
            AlertDialog(
                onDismissRequest = { vm.dismissConfirm() },
                icon = { Icon(Ic.reset, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Reset SpendLens?") },
                text = {
                    Text(
                        "This erases every transaction, saved SMS copy, category, rule, budget and setting, and destroys the encryption key. " +
                            "It can't be undone.\n\nYour SMS inbox isn't touched. You can set SpendLens up again straight away."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (secure) platform.authenticate("Confirm reset", "Confirm it's you to erase SpendLens", onSuccess = { vm.performReset() }, onFail = { })
                            else vm.performReset()
                        },
                        modifier = Modifier.testTag("confirm_reset"),
                    ) {
                        Text(if (secure) "Confirm it's you" else "Erase everything", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold)
                    }
                },
                dismissButton = { TextButton(onClick = { vm.dismissConfirm() }) { Text("Cancel") } },
            )
        }
        null -> Unit
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(vm: AppViewModel, tab: Tab, current: Tab, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, target: Screen) {
    NavigationBarItem(
        selected = tab == current,
        onClick = { if (target == Screen.ACTIVITY) vm.showActivity() else vm.go(target) },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        modifier = Modifier.testTag("tab_${tab.name.lowercase()}"),
    )
}
