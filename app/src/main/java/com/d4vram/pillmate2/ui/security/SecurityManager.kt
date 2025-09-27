package com.d4vram.pillmate2.ui.security

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.security.SecureRandom

private const val DEFAULT_AUTO_LOCK_MINUTES = 1L

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "security_preferences")

data class SecuritySettings(
    val lockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val isPinConfigured: Boolean = false,
    val autoLockDurationMs: Long = DEFAULT_AUTO_LOCK_MINUTES * 60_000L,
    val lastUnlockTimestamp: Long = 0L
)

class SecurityManager(private val context: Context) {
    companion object {
        private val LOCK_ENABLED_KEY = booleanPreferencesKey("lock_enabled")
        private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
        private val PIN_HASH_KEY = stringPreferencesKey("pin_hash")
        private val PIN_SALT_KEY = stringPreferencesKey("pin_salt")
        private val AUTO_LOCK_DURATION_KEY = longPreferencesKey("auto_lock_duration")
        private val LAST_UNLOCK_KEY = longPreferencesKey("last_unlock_timestamp")
    }

    val settings: Flow<SecuritySettings> = context.securityDataStore.data.map { preferences ->
        val hash = preferences[PIN_HASH_KEY]
        SecuritySettings(
            lockEnabled = preferences[LOCK_ENABLED_KEY] ?: false,
            biometricEnabled = preferences[BIOMETRIC_ENABLED_KEY] ?: false,
            isPinConfigured = !hash.isNullOrBlank(),
            autoLockDurationMs = preferences[AUTO_LOCK_DURATION_KEY]
                ?: (DEFAULT_AUTO_LOCK_MINUTES * 60_000L),
            lastUnlockTimestamp = preferences[LAST_UNLOCK_KEY] ?: 0L
        )
    }

    suspend fun setLockEnabled(enabled: Boolean) {
        context.securityDataStore.edit { prefs ->
            prefs[LOCK_ENABLED_KEY] = enabled
            if (!enabled) {
                prefs[LAST_UNLOCK_KEY] = 0L
            }
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.securityDataStore.edit { prefs ->
            prefs[BIOMETRIC_ENABLED_KEY] = enabled
        }
    }

    suspend fun updateAutoLockDuration(durationMs: Long) {
        context.securityDataStore.edit { prefs ->
            prefs[AUTO_LOCK_DURATION_KEY] = durationMs
        }
    }

    suspend fun recordUnlock(timestamp: Long = System.currentTimeMillis()) {
        context.securityDataStore.edit { prefs ->
            prefs[LAST_UNLOCK_KEY] = timestamp
        }
    }

    suspend fun clearUnlockTimestamp() {
        context.securityDataStore.edit { prefs ->
            prefs.remove(LAST_UNLOCK_KEY)
        }
    }

    suspend fun setPin(pin: String) {
        val saltBytes = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        val hash = hashPin(pin, saltBytes)
        context.securityDataStore.edit { prefs ->
            prefs[PIN_HASH_KEY] = hash
            prefs[PIN_SALT_KEY] = salt
        }
    }

    suspend fun clearPin() {
        context.securityDataStore.edit { prefs ->
            prefs.remove(PIN_HASH_KEY)
            prefs.remove(PIN_SALT_KEY)
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs = context.securityDataStore.data.first()
        val storedHash = prefs[PIN_HASH_KEY] ?: return false
        val saltBase64 = prefs[PIN_SALT_KEY] ?: return false
        val saltBytes = Base64.decode(saltBase64, Base64.NO_WRAP)
        val hash = hashPin(pin, saltBytes)
        return storedHash == hash
    }

    suspend fun isLockActive(now: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.securityDataStore.data.first()
        val enabled = prefs[LOCK_ENABLED_KEY] ?: false
        if (!enabled) return false
        val lastUnlock = prefs[LAST_UNLOCK_KEY] ?: 0L
        val duration = prefs[AUTO_LOCK_DURATION_KEY]
            ?: (DEFAULT_AUTO_LOCK_MINUTES * 60_000L)
        if (lastUnlock == 0L) return true
        return now - lastUnlock >= duration
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        val hash = digest.digest()
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}

@Composable
fun rememberSecurityManager(): SecurityManager {
    val context = LocalContext.current
    return remember { SecurityManager(context.applicationContext) }
}
