package com.d4vram.pillmate2

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.FragmentActivity
import com.d4vram.pillmate2.ui.navigation.PillMateNavigation
import com.d4vram.pillmate2.ui.security.AppLockScreen
import com.d4vram.pillmate2.ui.security.SecurityManager
import com.d4vram.pillmate2.ui.security.SecuritySettings
import com.d4vram.pillmate2.ui.theme.PillMateTheme
import com.d4vram.pillmate2.ui.theme.ThemeManager
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private lateinit var themeManager: ThemeManager
    private lateinit var securityManager: SecurityManager
    private var isLoading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { isLoading }
        super.onCreate(savedInstanceState)

        themeManager = ThemeManager(this)
        securityManager = SecurityManager(this)

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
                    PillMateNavigation(themeManager, securityManager)
                }
            }
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
