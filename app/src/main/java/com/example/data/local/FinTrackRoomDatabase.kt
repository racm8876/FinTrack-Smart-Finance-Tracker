package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

@Entity(
    tableName = "local_transactions",
    indices = [
        Index(value = ["userId", "transactionTimestampMillis"]),
        Index(value = ["dedupFingerprint"], unique = true),
        Index(value = ["category"]),
        Index(value = ["counterpartyName"])
    ]
)
data class LocalTransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val accountId: String,
    val transactionTimestampMillis: Long,
    val dateIst: String,
    val timeIst: String,
    val amount: Double,
    val type: String,
    val senderName: String,
    val receiverName: String,
    val counterpartyName: String,
    val recipientMobile: String,
    val upiId: String,
    val bankAccountName: String,
    val maskedAccountIdentifier: String,
    val paymentApp: String,
    val paymentMethod: String,
    val referenceNumber: String,
    val utrOrUpiRef: String,
    val status: String,
    val category: String,
    val description: String,
    val source: String,
    val notes: String,
    val dedupFingerprint: String,
    val rawSourceHash: String,
    val syncedToCloud: Boolean = false
)

@Entity(
    tableName = "local_pending_sync",
    indices = [Index(value = ["userId"])]
)
data class PendingSyncEntity(
    @PrimaryKey val dedupFingerprint: String,
    val userId: String,
    val payloadJson: String,
    val createdAtMillis: Long,
    val retryCount: Int = 0
)

@Dao
interface LocalTransactionDao {
    @Query("SELECT * FROM local_transactions WHERE userId = :userId ORDER BY transactionTimestampMillis DESC")
    fun observeByUser(userId: String): Flow<List<LocalTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: LocalTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LocalTransactionEntity>)

    @Query("SELECT COUNT(*) FROM local_transactions WHERE dedupFingerprint = :fp")
    suspend fun countByFingerprint(fp: String): Int

    @Query("DELETE FROM local_transactions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface PendingSyncDao {
    @Query("SELECT * FROM local_pending_sync WHERE userId = :userId ORDER BY createdAtMillis ASC")
    suspend fun getAllForUser(userId: String): List<PendingSyncEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingSyncEntity)

    @Query("DELETE FROM local_pending_sync WHERE dedupFingerprint = :fp")
    suspend fun deleteByFingerprint(fp: String)

    @Query("DELETE FROM local_pending_sync WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Database(
    entities = [LocalTransactionEntity::class, PendingSyncEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FinTrackDatabase : RoomDatabase() {
    abstract fun localTransactionDao(): LocalTransactionDao
    abstract fun pendingSyncDao(): PendingSyncDao

    companion object {
        @Volatile
        private var INSTANCE: FinTrackDatabase? = null

        fun getInstance(context: Context): FinTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FinTrackDatabase::class.java,
                    "fintrack_encrypted_ledger.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

private val Context.finTrackDataStore: DataStore<Preferences> by preferencesDataStore(name = "fintrack_prefs")

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

data class UserSecurityPreferences(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val biometricLockEnabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val largeTransactionThreshold: Double = 5000.0,
    val notifyNewTransaction: Boolean = true,
    val notifyLargeTransaction: Boolean = true,
    val notifyBudgetExceeded: Boolean = true,
    val notifySyncFailures: Boolean = true,
    val notifyDuplicateDetected: Boolean = true,
    val notifyMonthlySummary: Boolean = true,
    val lastSyncMillis: Long = 0L,
    val lastSyncSource: String = "Not synced yet"
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val LARGE_TX_THRESHOLD = doublePreferencesKey("large_tx_threshold")
        val NOTIFY_NEW_TX = booleanPreferencesKey("notify_new_tx")
        val NOTIFY_LARGE_TX = booleanPreferencesKey("notify_large_tx")
        val NOTIFY_BUDGET = booleanPreferencesKey("notify_budget")
        val NOTIFY_SYNC_FAIL = booleanPreferencesKey("notify_sync_fail")
        val NOTIFY_DUP = booleanPreferencesKey("notify_dup")
        val NOTIFY_MONTHLY = booleanPreferencesKey("notify_monthly")
        val LAST_SYNC_MILLIS = longPreferencesKey("last_sync_millis")
        val LAST_SYNC_SOURCE = stringPreferencesKey("last_sync_source")
    }

    val preferences: Flow<UserSecurityPreferences> = context.finTrackDataStore.data.map { prefs ->
        val mode = prefs[Keys.THEME_MODE]?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }
        } ?: ThemeMode.SYSTEM
        UserSecurityPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
            themeMode = mode,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            pinHash = prefs[Keys.PIN_HASH] ?: "",
            pinSalt = prefs[Keys.PIN_SALT] ?: "",
            largeTransactionThreshold = prefs[Keys.LARGE_TX_THRESHOLD] ?: 5000.0,
            notifyNewTransaction = prefs[Keys.NOTIFY_NEW_TX] ?: true,
            notifyLargeTransaction = prefs[Keys.NOTIFY_LARGE_TX] ?: true,
            notifyBudgetExceeded = prefs[Keys.NOTIFY_BUDGET] ?: true,
            notifySyncFailures = prefs[Keys.NOTIFY_SYNC_FAIL] ?: true,
            notifyDuplicateDetected = prefs[Keys.NOTIFY_DUP] ?: true,
            notifyMonthlySummary = prefs[Keys.NOTIFY_MONTHLY] ?: true,
            lastSyncMillis = prefs[Keys.LAST_SYNC_MILLIS] ?: 0L,
            lastSyncSource = prefs[Keys.LAST_SYNC_SOURCE] ?: "Not synced yet"
        )
    }

    suspend fun setOnboardingCompleted(done: Boolean) {
        context.finTrackDataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.finTrackDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.finTrackDataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setPin(pin: String) {
        val salt = java.util.UUID.randomUUID().toString().take(16)
        val hash = sha256("$salt:$pin")
        context.finTrackDataStore.edit {
            it[Keys.PIN_SALT] = salt
            it[Keys.PIN_HASH] = hash
        }
    }

    suspend fun clearPin() {
        context.finTrackDataStore.edit {
            it.remove(Keys.PIN_SALT)
            it.remove(Keys.PIN_HASH)
        }
    }

    fun verifyPin(pin: String, prefs: UserSecurityPreferences): Boolean {
        if (prefs.pinHash.isBlank() || prefs.pinSalt.isBlank()) return false
        return sha256("${prefs.pinSalt}:$pin") == prefs.pinHash
    }

    suspend fun updateNotificationPrefs(
        newTx: Boolean,
        largeTx: Boolean,
        budget: Boolean,
        syncFail: Boolean,
        dup: Boolean,
        monthly: Boolean,
        threshold: Double
    ) {
        context.finTrackDataStore.edit {
            it[Keys.NOTIFY_NEW_TX] = newTx
            it[Keys.NOTIFY_LARGE_TX] = largeTx
            it[Keys.NOTIFY_BUDGET] = budget
            it[Keys.NOTIFY_SYNC_FAIL] = syncFail
            it[Keys.NOTIFY_DUP] = dup
            it[Keys.NOTIFY_MONTHLY] = monthly
            it[Keys.LARGE_TX_THRESHOLD] = threshold.coerceAtLeast(100.0)
        }
    }

    suspend fun updateLastSync(source: String, timestampMillis: Long) {
        context.finTrackDataStore.edit {
            it[Keys.LAST_SYNC_MILLIS] = timestampMillis
            it[Keys.LAST_SYNC_SOURCE] = source
        }
    }

    suspend fun clearAllLocalPrefs() {
        context.finTrackDataStore.edit { it.clear() }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
