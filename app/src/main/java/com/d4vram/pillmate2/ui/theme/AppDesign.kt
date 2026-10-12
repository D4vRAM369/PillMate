package com.d4vram.pillmate2.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

// Layout and palette are independent, so Legacy restores layout without losing colors.
enum class AppDesign(val storageValue: String, val title: String) {
    Astra("astra", "Diseño Astra"),
    Legacy("legacy", "Diseño Legacy");

    companion object {
        fun fromStorage(value: String?): AppDesign =
            entries.firstOrNull { it.storageValue == value } ?: Astra
    }
}

val LocalAppDesign = staticCompositionLocalOf { AppDesign.Astra }
