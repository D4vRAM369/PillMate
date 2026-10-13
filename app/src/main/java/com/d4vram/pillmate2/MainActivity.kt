package com.d4vram.pillmate2

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import com.d4vram.pillmate2.ui.navigation.PillMateNavigation
import com.d4vram.pillmate2.ui.onboarding.OnboardingManager
import com.d4vram.pillmate2.ui.security.AppLockScreen
import com.d4vram.pillmate2.ui.security.SecurityManager
import com.d4vram.pillmate2.ui.security.SecuritySettings
import com.d4vram.pillmate2.ui.theme.PillMateTheme
import com.d4vram.pillmate2.ui.theme.ThemeManager
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var themeManager: ThemeManager
    private lateinit var securityManager: SecurityManager
    private lateinit var onboardingManager: OnboardingManager
    private var isLoading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { isLoading }
        super.onCreate(savedInstanceState)
        setupCrashReporter()

        themeManager = ThemeManager(this)
        securityManager = SecurityManager(this)
        onboardingManager = OnboardingManager(this)

        lifecycleScope.launch {
            themeManager.uiStyle.collect {
                if (isLoading) {
                    isLoading = false
                }
            }
        }

        setContent {
            val securitySettings by securityManager.settings.collectAsState(initial = SecuritySettings())
            val needsUnlock by securityManager.needsUnlock.collectAsState(initial = false)
            val lockError by securityManager.lockError.collectAsState()

            PillMateTheme(themeManager = themeManager) {
                if (securitySettings.lockEnabled && needsUnlock) {
                    AppLockScreen(
                        isBiometricEnabled = securitySettings.biometricEnabled && securityManager.canUseBiometric(),
                        onUseBiometric = { securityManager.onUseBiometric(this@MainActivity) },
                        onSubmitPin = securityManager::submitPin,
                        errorMessage = lockError
                    )
                } else {
                    PillMateNavigation(
                        themeManager = themeManager,
                        securityManager = securityManager,
                        onboardingManager = onboardingManager
                    )
                }
            }
        }
    }

    private fun setupCrashReporter() {
        val crashFile = File(filesDir, "last_crash.txt")
        if (!crashFile.exists()) return

        runCatching {
            Log.e("MainActivity", "Previous crash detected:\n${crashFile.readText().take(3000)}")
        }.onFailure { error ->
            Log.e("MainActivity", "Unable to read previous crash report", error)
        }

        runCatching {
            crashFile.delete()
        }.onFailure { error ->
            Log.e("MainActivity", "Unable to delete previous crash report", error)
        }
    }

    override fun onResume() {
        super.onResume()
        securityManager.onAppResumed()
    }

    override fun onPause() {
        super.onPause()
        securityManager.onAppPaused()
    }
}
