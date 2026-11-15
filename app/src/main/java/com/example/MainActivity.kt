package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.local.FinTrackDatabase
import com.example.data.local.ThemeMode
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.FinTrackRepository
import com.example.service.FinTrackSyncWorker
import com.example.service.NotificationHelper
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.components.AddManualTransactionDialog
import com.example.ui.components.PasteSmsAlertDialog
import com.example.ui.navigation.FinTrackRoute
import com.example.ui.screens.AccountManagementScreen
import com.example.ui.screens.AllTransactionsScreen
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.BudgetManagementScreen
import com.example.ui.screens.DailyAnalyticsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DataExportScreen
import com.example.ui.screens.ExpenseCategoriesScreen
import com.example.ui.screens.FinancialAccountConnectionsScreen
import com.example.ui.screens.FinancialReportsHubScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MonthlyAnalyticsScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PeopleAndContactsScreen
import com.example.ui.screens.PermissionManagementScreen
import com.example.ui.screens.PersonTransactionHistoryScreen
import com.example.ui.screens.PrivacySettingsScreen
import com.example.ui.screens.SecuritySettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TransactionDetailsScreen
import com.example.ui.screens.WeeklyAnalyticsScreen
import com.example.ui.screens.YearlyAnalyticsScreen
import com.example.ui.theme.FinTrackTheme
import com.example.ui.viewmodel.FinTrackViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private var sharedTextExtra by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannels(this)
        handleIncomingShareIntent(intent)

        setContent {
            FinTrackAppRoot(
                sharedSmsText = sharedTextExtra,
                onClearSharedSmsText = { sharedTextExtra = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedTextExtra = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }
}

@Composable
fun FinTrackAppRoot(
    sharedSmsText: String?,
    onClearSharedSmsText: () -> Unit
) {
    val context = LocalContext.current
    val prefsRepo = remember(context) { UserPreferencesRepository(context) }
    val prefs by prefsRepo.preferences.collectAsStateWithLifecycle(
        initialValue = com.example.data.local.UserSecurityPreferences()
    )
    val currentUser by Firebase.auth.authStateFlow().collectAsStateWithLifecycle(
        initialValue = Firebase.auth.currentUser
    )
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showSplash by remember { mutableStateOf(true) }
    var showOnboarding by remember { mutableStateOf(false) }
    var appUnlocked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = scope
        )
    }

    FinTrackTheme(themeMode = prefs.themeMode) {
        when {
            showSplash -> {
                SplashScreen(
                    onFinished = {
                        showSplash = false
                        showOnboarding = !prefs.onboardingCompleted && currentUser == null
                    }
                )
            }
            currentUser == null && showOnboarding -> {
                BackHandler { showOnboarding = false }
                OnboardingScreen(
                    onContinue = {
                        scope.launch { prefsRepo.setOnboardingCompleted(true) }
                        showOnboarding = false
                    }
                )
            }
            currentUser == null -> {
                LoginScreen(
                    onAuthSuccess = {
                        scope.launch { prefsRepo.setOnboardingCompleted(true) }
                        FinTrackSyncWorker.schedulePeriodicSync(context)
                    },
                    onShowOnboarding = { showOnboarding = true }
                )
            }
            (prefs.biometricLockEnabled || prefs.pinHash.isNotBlank()) && !appUnlocked -> {
                AppLockScreen(
                    biometricEnabled = prefs.biometricLockEnabled,
                    hasPin = prefs.pinHash.isNotBlank(),
                    verifyPin = { input -> prefsRepo.verifyPin(input, prefs) },
                    onUnlocked = { appUnlocked = true }
                )
            }
            else -> {
                val user = currentUser!!
                LaunchedEffect(user.uid) {
                    FinTrackSyncWorker.schedulePeriodicSync(context)
                }
                key(user.uid) {
                    AuthenticatedFinTrackShell(
                        currentUserId = user.uid,
                        userDisplayName = user.displayName ?: "FinTrack User",
                        userEmail = user.email ?: "",
                        sharedSmsText = sharedSmsText,
                        onClearSharedSmsText = onClearSharedSmsText,
                        onSignOut = {
                            appUnlocked = false
                            signOutUser(credentialManager, scope)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedFinTrackShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String,
    sharedSmsText: String?,
    onClearSharedSmsText: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: FinTrackViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(databaseId)
                FinTrackViewModel(
                    repository = FinTrackRepository(firestore),
                    preferencesRepository = UserPreferencesRepository(app),
                    localDatabase = FinTrackDatabase.getInstance(app),
                    currentUserId = currentUserId,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail
                )
            }
        }
    )

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val consents by viewModel.consents.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val people by viewModel.peopleSummaries.collectAsStateWithLifecycle()
    val searchResult by viewModel.smartSearchResult.collectAsStateWithLifecycle()
    val filter by viewModel.filterCriteria.collectAsStateWithLifecycle()
    val prefs by viewModel.userPreferences.collectAsStateWithLifecycle()
    val banner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var showManualDialog by remember { mutableStateOf(false) }
    var showPasteSmsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sharedSmsText) {
        if (!sharedSmsText.isNullOrBlank()) {
            showPasteSmsDialog = true
        }
    }

    LaunchedEffect(banner) {
        if (!banner.isNullOrBlank()) {
            snackbarHostState.showSnackbar(banner!!)
            viewModel.clearBanner()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    val topLevelRoutes = listOf(
        Triple(FinTrackRoute.Dashboard, "Home", Icons.Default.Dashboard),
        Triple(FinTrackRoute.AllTransactions, "Transactions", Icons.AutoMirrored.Filled.ReceiptLong),
        Triple(FinTrackRoute.PeopleList, "People", Icons.Default.People),
        Triple(FinTrackRoute.ReportsHub, "Reports", Icons.Default.Analytics),
        Triple(FinTrackRoute.SecuritySettings, "Settings", Icons.Default.Settings)
    )

    val showBottomBar = topLevelRoutes.any { (route, _, _) ->
        currentDest?.hasRoute(route::class) == true
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showBottomBar && !useRail) {
                    NavigationBar {
                        topLevelRoutes.forEach { (route, label, icon) ->
                            val selected = currentDest?.hasRoute(route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_${label.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (showBottomBar && useRail) {
                    NavigationRail {
                        topLevelRoutes.forEach { (route, label, icon) ->
                            val selected = currentDest?.hasRoute(route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("rail_${label.lowercase()}")
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = FinTrackRoute.Dashboard
                    ) {
                composable<FinTrackRoute.Dashboard> {
                    DashboardScreen(
                        metrics = metrics,
                        transactions = transactions,
                        unreadAlertCount = alerts.count { !it.isRead },
                        preferences = prefs,
                        onAddManualClick = { showManualDialog = true },
                        onPasteSmsClick = { showPasteSmsDialog = true },
                        onSyncNowClick = { viewModel.triggerSyncNow(context) },
                        onOpenNotifications = { navController.navigate(FinTrackRoute.Notifications) },
                        onOpenAccounts = { navController.navigate(FinTrackRoute.AccountConnections) },
                        onOpenTransactions = { navController.navigate(FinTrackRoute.AllTransactions) },
                        onOpenTransactionDetail = { id -> navController.navigate(FinTrackRoute.TransactionDetails(id)) },
                        onOpenReports = { navController.navigate(FinTrackRoute.ReportsHub) },
                        onOpenBudgets = { navController.navigate(FinTrackRoute.Budgets) },
                        onOpenPeople = { navController.navigate(FinTrackRoute.PeopleList) },
                        onOpenExport = { navController.navigate(FinTrackRoute.DataExport) },
                        onOpenPermissions = { navController.navigate(FinTrackRoute.Permissions) },
                        onOpenSecurity = { navController.navigate(FinTrackRoute.SecuritySettings) }
                    )
                }

                composable<FinTrackRoute.AllTransactions> {
                    AllTransactionsScreen(
                        searchResult = searchResult,
                        filter = filter,
                        categories = categories.map { it.name },
                        accounts = accounts.map { it.accountName },
                        onFilterChange = viewModel::updateFilter,
                        onClearFilters = viewModel::clearFilters,
                        onOpenTransactionDetail = { id -> navController.navigate(FinTrackRoute.TransactionDetails(id)) },
                        onAddManualClick = { showManualDialog = true },
                        onExportClick = { navController.navigate(FinTrackRoute.DataExport) }
                    )
                }

                composable<FinTrackRoute.TransactionDetails> { entry ->
                    val args = entry.toRoute<FinTrackRoute.TransactionDetails>()
                    val tx = transactions.firstOrNull { it.id == args.transactionId }
                    val audits by viewModel.observeTransactionAudits(args.transactionId)
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    TransactionDetailsScreen(
                        transaction = tx,
                        audits = audits,
                        categories = categories.map { it.name },
                        onBack = { navController.popBackStack() },
                        onUpdateMeta = viewModel::updateTransactionMetadata,
                        onDelete = viewModel::deleteTransaction
                    )
                }

                composable<FinTrackRoute.PeopleList> {
                    PeopleAndContactsScreen(
                        people = people,
                        onOpenPerson = { key -> navController.navigate(FinTrackRoute.PersonHistory(key)) },
                        onAddTransactionClick = { showManualDialog = true }
                    )
                }

                composable<FinTrackRoute.PersonHistory> { entry ->
                    val args = entry.toRoute<FinTrackRoute.PersonHistory>()
                    val person = people.firstOrNull { it.key == args.personKey }
                    PersonTransactionHistoryScreen(
                        person = person,
                        onBack = { navController.popBackStack() },
                        onOpenTransaction = { id -> navController.navigate(FinTrackRoute.TransactionDetails(id)) }
                    )
                }

                composable<FinTrackRoute.ReportsHub> {
                    FinancialReportsHubScreen(
                        transactions = transactions,
                        onOpenDaily = { navController.navigate(FinTrackRoute.DailyAnalytics) },
                        onOpenWeekly = { navController.navigate(FinTrackRoute.WeeklyAnalytics) },
                        onOpenMonthly = { navController.navigate(FinTrackRoute.MonthlyAnalytics) },
                        onOpenYearly = { navController.navigate(FinTrackRoute.YearlyAnalytics) },
                        onOpenExport = { navController.navigate(FinTrackRoute.DataExport) },
                        onOpenBudgets = { navController.navigate(FinTrackRoute.Budgets) },
                        onOpenCategories = { navController.navigate(FinTrackRoute.Categories) }
                    )
                }

                composable<FinTrackRoute.DailyAnalytics> {
                    DailyAnalyticsScreen(transactions = transactions, onBack = { navController.popBackStack() })
                }
                composable<FinTrackRoute.WeeklyAnalytics> {
                    WeeklyAnalyticsScreen(transactions = transactions, onBack = { navController.popBackStack() })
                }
                composable<FinTrackRoute.MonthlyAnalytics> {
                    MonthlyAnalyticsScreen(transactions = transactions, onBack = { navController.popBackStack() })
                }
                composable<FinTrackRoute.YearlyAnalytics> {
                    YearlyAnalyticsScreen(transactions = transactions, onBack = { navController.popBackStack() })
                }

                composable<FinTrackRoute.Budgets> {
                    BudgetManagementScreen(
                        budgets = budgets,
                        transactions = transactions,
                        categories = categories.map { it.name },
                        onSaveBudget = viewModel::saveBudget,
                        onDeleteBudget = viewModel::deleteBudget,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<FinTrackRoute.Categories> {
                    ExpenseCategoriesScreen(
                        categories = categories,
                        onSaveCategory = viewModel::saveCategory,
                        onDeleteCategory = viewModel::deleteCategory,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<FinTrackRoute.Permissions> {
                    PermissionManagementScreen(
                        consents = consents,
                        onUpdateConsent = viewModel::updateConsent,
                        onScanSmsInbox = { viewModel.scanSmsInbox(context) },
                        onPasteSmsClick = { showPasteSmsDialog = true },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<FinTrackRoute.AccountConnections> {
                    FinancialAccountConnectionsScreen(
                        accounts = accounts,
                        onUpsertAccount = viewModel::upsertAccount,
                        onDeleteAccount = viewModel::deleteAccount,
                        onImportStatementUri = { uri, fileName, bankName, masked ->
                            viewModel.importStatementUri(context, uri, fileName, bankName, masked)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<FinTrackRoute.Notifications> {
                    NotificationsScreen(
                        alerts = alerts,
                        prefs = prefs,
                        onMarkRead = viewModel::markAlertRead,
                        onSavePrefs = viewModel::updateNotificationPrefs,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<FinTrackRoute.DataExport> {
                    DataExportScreen(
                        transactions = transactions,
                        categories = categories.map { it.name },
                        accounts = accounts.map { it.accountName },
                        onExport = { fmt, list, title -> viewModel.exportReportFile(context, fmt, list, title) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<FinTrackRoute.SecuritySettings> {
                    SecuritySettingsScreen(
                        prefs = prefs,
                        syncLogs = syncLogs,
                        onToggleBiometric = viewModel::setBiometricEnabled,
                        onSetPin = viewModel::setPin,
                        onClearPin = viewModel::clearPin,
                        onThemeChange = viewModel::setThemeMode,
                        onOpenPrivacy = { navController.navigate(FinTrackRoute.PrivacySettings) },
                        onOpenAccountMgmt = { navController.navigate(FinTrackRoute.AccountManagement) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<FinTrackRoute.PrivacySettings> {
                    PrivacySettingsScreen(
                        consents = consents,
                        onUpdateConsent = viewModel::updateConsent,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<FinTrackRoute.AccountManagement> {
                    AccountManagementScreen(
                        userEmail = userEmail,
                        userName = userDisplayName,
                        userId = currentUserId,
                        prefs = prefs,
                        onForceSync = { viewModel.triggerSyncNow(context) },
                        onSignOut = onSignOut,
                        onDeleteAllData = {
                            viewModel.deleteAllFinancialData {
                                onSignOut()
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        AddManualTransactionDialog(
            categories = categories.map { it.name },
            onDismiss = { showManualDialog = false },
            onSubmit = { amt, type, party, mobile, upi, bank, masked, app, method, ref, st, cat, desc, notes ->
                viewModel.addManualTransaction(
                    amount = amt,
                    type = type,
                    counterpartyName = party,
                    recipientMobile = mobile,
                    upiId = upi,
                    bankAccountName = bank,
                    maskedAccount = masked,
                    paymentApp = app,
                    paymentMethod = method,
                    referenceNumber = ref,
                    status = st,
                    category = cat,
                    description = desc,
                    notes = notes,
                    context = context
                )
                showManualDialog = false
            }
        )
    }

    if (showPasteSmsDialog) {
        PasteSmsAlertDialog(
            initialText = sharedSmsText.orEmpty(),
            onDismiss = {
                showPasteSmsDialog = false
                onClearSharedSmsText()
            },
            onParse = { raw ->
                viewModel.parseAndSaveRawAlertText(raw, context)
                showPasteSmsDialog = false
                onClearSharedSmsText()
            }
        )
    }
}
