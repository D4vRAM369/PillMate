package com.d4vram.pillmate2.ui.security

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.d4vram.pillmate2.data.voice.VoiceNoteEncryption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private val Context.voiceNoteSecurityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "voice_notes_security"
)

data class VoiceNoteSecuritySettings(
    val isPasswordConfigured: Boolean,
    val passwordHint: String?
)

class VoiceNoteSecurityManager(private val context: Context) {
    companion object {
        private val PASSWORD_HASH_KEY = stringPreferencesKey("voice_password_hash")
        private val PASSWORD_SALT_KEY = stringPreferencesKey("voice_password_salt")
        private val PASSWORD_HINT_KEY = stringPreferencesKey("voice_password_hint")
    }

    val settings: Flow<VoiceNoteSecuritySettings> = context.voiceNoteSecurityDataStore.data
        .catch { throwable ->
            Log.e("VoiceNoteSecurity", "Error reading voice note security prefs", throwable)
            emit(emptyPreferences())
            clearCorruptedPreferences()
        }
        .map { prefs ->
            val hash = prefs[PASSWORD_HASH_KEY]
            VoiceNoteSecuritySettings(
                isPasswordConfigured = !hash.isNullOrEmpty(),
                passwordHint = prefs[PASSWORD_HINT_KEY]
            )
        }

    suspend fun setPassword(password: String, hint: String? = null) = withContext(Dispatchers.IO) {
        val saltBytes = VoiceNoteEncryption.generateSalt()
        val passwordChars = password.toCharArray()
        val key = VoiceNoteEncryption.deriveKey(passwordChars, saltBytes)
        VoiceNoteEncryption.wipePassword(passwordChars)

        val hashBytes = key.encoded
        val hashBase64 = Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        val saltBase64 = Base64.encodeToString(saltBytes, Base64.NO_WRAP)

        context.voiceNoteSecurityDataStore.edit { prefs ->
            prefs[PASSWORD_HASH_KEY] = hashBase64
            prefs[PASSWORD_SALT_KEY] = saltBase64
            if (hint.isNullOrBlank()) {
                prefs.remove(PASSWORD_HINT_KEY)
            } else {
                prefs[PASSWORD_HINT_KEY] = hint
            }
        }
    }

    suspend fun verifyPassword(password: String): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.voiceNoteSecurityDataStore.data.first()
        val storedHashBase64 = prefs[PASSWORD_HASH_KEY] ?: return@withContext false
        val saltBase64 = prefs[PASSWORD_SALT_KEY] ?: return@withContext false
        val saltBytes = Base64.decode(saltBase64, Base64.NO_WRAP)

        val passwordChars = password.toCharArray()
        val derivedKey = VoiceNoteEncryption.deriveKey(passwordChars, saltBytes)
        VoiceNoteEncryption.wipePassword(passwordChars)

        val storedHashBytes = Base64.decode(storedHashBase64, Base64.NO_WRAP)
        val computedHashBytes = derivedKey.encoded

        MessageDigest.isEqual(storedHashBytes, computedHashBytes)
    }

    suspend fun changePassword(
        currentPassword: String, 
        newPassword: String, 
        newHint: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        // Verificar contraseña actual
        if (!verifyPassword(currentPassword)) {
            return@withContext false
        }
        
        // Configurar nueva contraseña
        setPassword(newPassword, newHint)
        return@withContext true
    }

    suspend fun clearPassword() = withContext(Dispatchers.IO) {
        context.voiceNoteSecurityDataStore.edit { prefs ->
            prefs.remove(PASSWORD_HASH_KEY)
            prefs.remove(PASSWORD_SALT_KEY)
            prefs.remove(PASSWORD_HINT_KEY)
        }
    }

    private suspend fun clearCorruptedPreferences() {
        context.voiceNoteSecurityDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}

@Composable
fun rememberVoiceNoteSecurityManager(): VoiceNoteSecurityManager {
    val context = LocalContext.current
    return remember { VoiceNoteSecurityManager(context.applicationContext) }
}
