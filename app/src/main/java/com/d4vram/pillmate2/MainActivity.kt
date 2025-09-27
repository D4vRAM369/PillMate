package com.d4vram.pillmate2

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.d4vram.pillmate2.ui.navigation.PillMateNavigation
import com.d4vram.pillmate2.ui.theme.PillMateTheme
import com.d4vram.pillmate2.ui.theme.rememberThemeManager
import com.d4vram.pillmate2.ui.security.rememberSecurityManager
import androidx.fragment.app.FragmentActivity
import com.d4vram.pillmate2.ui.security.SecuritySettings
import com.d4vram.pillmate2.ui.security.AppLockScreen
import kotlinx.coroutines.launch

/**
 * 🚀 Actividad principal de la aplicación
 * Punto de entrada de la app
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeManager = rememberThemeManager()
            val securityManager = rememberSecurityManager()
            val lifecycleOwner = LocalLifecycleOwner.current
            val scope = rememberCoroutineScope()
            val securitySettings by securityManager.settings.collectAsState(initial = SecuritySettings())
            var isLocked by remember { mutableStateOf(false) }
            var lockErrorMessage by remember { mutableStateOf<String?>(null) }
            var requestBiometric by remember { mutableStateOf(false) }
            var autoBiometricAttempted by remember { mutableStateOf(false) }

            val context = LocalContext.current
            val executor = remember(context) { ContextCompat.getMainExecutor(context) }
            val activity = this@MainActivity

            val promptInfo = remember {
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Desbloquear PillMate")
                    .setSubtitle("Usa tu huella para continuar")
                    .setNegativeButtonText("Usar PIN")
                    .build()
            }

            val biometricPrompt = remember(activity, executor) {
                BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        scope.launch {
                            securityManager.recordUnlock()
                            lockErrorMessage = null
                            isLocked = false
                            autoBiometricAttempted = false
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        autoBiometricAttempted = true
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            lockErrorMessage = errString.toString()
                        }
                    }

                    override fun onAuthenticationFailed() {
                        autoBiometricAttempted = true
                        lockErrorMessage = "Huella no reconocida"
                    }
                })
            }

            LaunchedEffect(securitySettings.lockEnabled, securitySettings.autoLockDurationMs, securitySettings.lastUnlockTimestamp) {
                if (securitySettings.lockEnabled && securitySettings.isPinConfigured) {
                    val shouldLock = securityManager.isLockActive()
                    isLocked = shouldLock
                    if (!shouldLock) {
                        lockErrorMessage = null
                        autoBiometricAttempted = false
                    }
                } else {
                    isLocked = false
                    lockErrorMessage = null
                    autoBiometricAttempted = false
                }
            }

            DisposableEffect(lifecycleOwner, securitySettings.lockEnabled) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> {
                            if (securitySettings.lockEnabled && securitySettings.isPinConfigured) {
                                scope.launch {
                                    if (securityManager.isLockActive()) {
                                        isLocked = true
                                    }
                                }
                            }
                        }
                        Lifecycle.Event.ON_STOP -> {
                            if (securitySettings.lockEnabled && securitySettings.isPinConfigured) {
                                scope.launch {
                                    securityManager.recordUnlock()
                                }
                            }
                        }
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(isLocked, securitySettings.biometricEnabled) {
                if (!isLocked) {
                    autoBiometricAttempted = false
                } else if (securitySettings.lockEnabled && securitySettings.biometricEnabled && !autoBiometricAttempted) {
                    autoBiometricAttempted = true
                    requestBiometric = true
                }
            }

            LaunchedEffect(requestBiometric) {
                if (requestBiometric) {
                    biometricPrompt.authenticate(promptInfo)
                    requestBiometric = false
                }
            }

            PillMateTheme(themeManager = themeManager) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PillMateNavigation(
                            themeManager = themeManager,
                            securityManager = securityManager
                        )

                        if (isLocked && securitySettings.lockEnabled && securitySettings.isPinConfigured) {
                            AppLockScreen(
                                isBiometricEnabled = securitySettings.biometricEnabled,
                                onSubmitPin = { pin ->
                                    scope.launch {
                                        val valid = securityManager.verifyPin(pin)
                                        if (valid) {
                                            securityManager.recordUnlock()
                                            lockErrorMessage = null
                                            isLocked = false
                                            autoBiometricAttempted = false
                                        } else {
                                            lockErrorMessage = "PIN incorrecto"
                                        }
                                    }
                                },
                                onUseBiometric = {
                                    lockErrorMessage = null
                                    autoBiometricAttempted = true
                                    requestBiometric = true
                                },
                                errorMessage = lockErrorMessage
                            )
                        }
                    }
                }
            }
        }
    }
}
