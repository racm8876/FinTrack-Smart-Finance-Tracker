package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface FinTrackRoute {
    // 1. Splash, 2. Onboarding, 3. Login handled in root flow
    @Serializable data object Dashboard : FinTrackRoute
    @Serializable data object AllTransactions : FinTrackRoute
    @Serializable data class TransactionDetails(val transactionId: String) : FinTrackRoute
    @Serializable data object PeopleList : FinTrackRoute
    @Serializable data class PersonHistory(val personKey: String) : FinTrackRoute
    @Serializable data object ReportsHub : FinTrackRoute
    @Serializable data object DailyAnalytics : FinTrackRoute
    @Serializable data object WeeklyAnalytics : FinTrackRoute
    @Serializable data object MonthlyAnalytics : FinTrackRoute
    @Serializable data object YearlyAnalytics : FinTrackRoute
    @Serializable data object Budgets : FinTrackRoute
    @Serializable data object Categories : FinTrackRoute
    @Serializable data object Permissions : FinTrackRoute
    @Serializable data object AccountConnections : FinTrackRoute
    @Serializable data object Notifications : FinTrackRoute
    @Serializable data object DataExport : FinTrackRoute
    @Serializable data object SecuritySettings : FinTrackRoute
    @Serializable data object PrivacySettings : FinTrackRoute
    @Serializable data object AccountManagement : FinTrackRoute
}
