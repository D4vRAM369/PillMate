package com.d4vram.pillmate2.ui.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "onboarding_preferences"
)

class OnboardingManager(private val context: Context) {

    companion object {
        private val MAIN_ONBOARDING_COMPLETED = booleanPreferencesKey("main_onboarding_completed")
    }

    val isMainOnboardingCompleted: Flow<Boolean> = context.onboardingDataStore.data.map { prefs ->
        prefs[MAIN_ONBOARDING_COMPLETED] ?: false
    }

    suspend fun completeMainOnboarding() {
        context.onboardingDataStore.edit { prefs ->
            prefs[MAIN_ONBOARDING_COMPLETED] = true
        }
    }
}
