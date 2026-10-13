package com.d4vram.pillmate2.ui.security

import com.d4vram.pillmate2.R
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

class SecurityManager(context: Context) {
    companion object {
        private val LOCK_ENABLED_KEY = booleanPreferencesKey("lock_enabled")
        private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
        private val PIN_HASH_KEY = stringPreferencesKey("pin_hash")
        private val PIN_SALT_KEY = stringPreferencesKey("pin_salt")
        private val AUTO_LOCK_DURATION_KEY = longPreferencesKey("auto_lock_duration")
        private val LAST_UNLOCK_KEY = longPreferencesKey("last_unlock_timestamp")
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _needsUnlock = MutableStateFlow(false)
    val needsUnlock: StateFlow<Boolean> = _needsUnlock.asStateFlow()

    private val _lockError = MutableStateFlow<String?>(null)
    val lockError: StateFlow<String?> = _lockError.asStateFlow()

    val settings: Flow<SecuritySettings> = appContext.securityDataStore.data
        .catch { throwable ->
            Log.e("SecurityManager", "Error reading security preferences", throwable)
            emit(emptyPreferences())
            scope.launch(Dispatchers.IO) { clearCorruptedPreferences() }
        }
        .map { preferences ->
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

    init {
        scope.launch {
            settings.collect { currentSettings ->
                if (!currentSettings.lockEnabled) {
                    _needsUnlock.value = false
                    _lockError.value = null
                } else {
                    _needsUnlock.value = shouldLock(currentSettings)
                }
            }
        }
    }

    suspend fun setLockEnabled(enabled: Boolean) {
        appContext.securityDataStore.edit { prefs ->
            prefs[LOCK_ENABLED_KEY] = enabled
            if (!enabled) {
                prefs[LAST_UNLOCK_KEY] = 0L
            }
        }
        val currentSettings = settings.first()
        if (!enabled || !currentSettings.isPinConfigured) {
            _needsUnlock.value = false
            _lockError.value = null
        } else {
            _needsUnlock.value = shouldLock(currentSettings)
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        appContext.securityDataStore.edit { prefs ->
            prefs[BIOMETRIC_ENABLED_KEY] = enabled
        }
    }

    suspend fun updateAutoLockDuration(durationMs: Long) {
        appContext.securityDataStore.edit { prefs ->
            prefs[AUTO_LOCK_DURATION_KEY] = durationMs
        }
        _needsUnlock.value = shouldLock(settings.first())
    }

    suspend fun recordUnlock(timestamp: Long = System.currentTimeMillis()) {
        appContext.securityDataStore.edit { prefs ->
            prefs[LAST_UNLOCK_KEY] = timestamp
        }
    }

    suspend fun clearUnlockTimestamp() {
        appContext.securityDataStore.edit { prefs ->
            prefs.remove(LAST_UNLOCK_KEY)
        }
    }

    private suspend fun clearCorruptedPreferences() {
        appContext.securityDataStore.edit { prefs ->
            prefs.clear()
        }
    }

    suspend fun setPin(pin: String) = withContext(Dispatchers.IO) {
        val saltBytes = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        val hash = hashPin(pin, saltBytes)
        appContext.securityDataStore.edit { prefs ->
            prefs[PIN_HASH_KEY] = hash
            prefs[PIN_SALT_KEY] = salt
        }
    }

    suspend fun clearPin() = withContext(Dispatchers.IO) {
        appContext.securityDataStore.edit { prefs ->
            prefs.remove(PIN_HASH_KEY)
            prefs.remove(PIN_SALT_KEY)
        }
    }

    suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val prefs = appContext.securityDataStore.data.first()
        val storedHashBase64 = prefs[PIN_HASH_KEY] ?: return@withContext false
        val saltBase64 = prefs[PIN_SALT_KEY] ?: return@withContext false
        val saltBytes = Base64.decode(saltBase64, Base64.NO_WRAP)
        val computedHashBase64 = hashPin(pin, saltBytes)
        val storedHashBytes = Base64.decode(storedHashBase64, Base64.NO_WRAP)
        val computedHashBytes = Base64.decode(computedHashBase64, Base64.NO_WRAP)
        MessageDigest.isEqual(storedHashBytes, computedHashBytes)
    }

    suspend fun isLockActive(now: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        shouldLock(settings.first(), now)
    }

    fun onAppResumed() {
        scope.launch {
            _lockError.value = null
            _needsUnlock.value = shouldLock(settings.first())
        }
    }

    fun onAppPaused() {
        scope.launch {
            if (!_needsUnlock.value) {
                recordUnlock()
            }
        }
    }

    fun submitPin(pin: String) {
        scope.launch {
            if (pin.length < 4) {
                _lockError.value = appContext.getString(R.string.lock_err_short)
                return@launch
            }

            val isValid = verifyPin(pin)
            if (isValid) {
                recordUnlock()
                _lockError.value = null
                _needsUnlock.value = false
            } else {
                _lockError.value = appContext.getString(R.string.settings_pin_err_wrong)
            }
        }
    }

    fun canUseBiometric(): Boolean {
        return BiometricManager.from(appContext).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun onUseBiometric(activity: FragmentActivity) {
        scope.launch {
            if (!canUseBiometric()) {
                _lockError.value = appContext.getString(R.string.lock_err_no_biometrics)
                return@launch
            }

            val currentSettings = settings.first()
            if (!currentSettings.biometricEnabled) {
                _lockError.value = appContext.getString(R.string.lock_err_enable_biometrics)
                return@launch
            }

            _lockError.value = null

            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    scope.launch {
                        recordUnlock()
                        _lockError.value = null
                        _needsUnlock.value = false
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        _lockError.value = errString.toString()
                    }
                }

                override fun onAuthenticationFailed() {
                    _lockError.value = appContext.getString(R.string.lock_err_auth_failed)
                }
            })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(appContext.getString(R.string.lock_prompt_title))
                .setSubtitle(appContext.getString(R.string.lock_prompt_subtitle))
                .setNegativeButtonText(appContext.getString(R.string.action_cancel))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

            prompt.authenticate(promptInfo)
        }
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        val hash = digest.digest()
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun shouldLock(settings: SecuritySettings, now: Long = System.currentTimeMillis()): Boolean {
        if (!settings.lockEnabled || !settings.isPinConfigured) {
            return false
        }
        val lastUnlock = settings.lastUnlockTimestamp
        if (lastUnlock == 0L) {
            return true
        }
        return now - lastUnlock >= settings.autoLockDurationMs
    }
}

@Composable
fun rememberSecurityManager(): SecurityManager {
    val context = LocalContext.current
    return remember { SecurityManager(context.applicationContext) }
}
