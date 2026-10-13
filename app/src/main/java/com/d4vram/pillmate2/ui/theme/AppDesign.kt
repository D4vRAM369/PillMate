package com.d4vram.pillmate2.ui.theme

import androidx.annotation.StringRes
import com.d4vram.pillmate2.R
import androidx.compose.runtime.staticCompositionLocalOf

// Layout and palette are independent, so Legacy restores layout without losing colors.
enum class AppDesign(val storageValue: String, @StringRes val titleRes: Int) {
    Astra("astra", R.string.design_rhythm),
    Legacy("legacy", R.string.design_classic);

    companion object {
        fun fromStorage(value: String?): AppDesign =
            entries.firstOrNull { it.storageValue == value } ?: Astra
    }
}

val LocalAppDesign = staticCompositionLocalOf { AppDesign.Astra }
