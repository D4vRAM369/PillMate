package com.d4vram.pillmate2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.d4vram.pillmate2.ui.navigation.PillMateNavigation
import com.d4vram.pillmate2.ui.theme.PillMateTheme
import com.d4vram.pillmate2.ui.theme.rememberThemeManager

/**
 * 🚀 Actividad principal de la aplicación
 * Punto de entrada de la app
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeManager = rememberThemeManager()
            
            PillMateTheme(themeManager = themeManager) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PillMateNavigation(themeManager = themeManager)
                }
            }
        }
    }
}