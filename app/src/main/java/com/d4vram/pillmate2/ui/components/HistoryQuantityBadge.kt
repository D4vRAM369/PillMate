package com.d4vram.pillmate2.ui.components

import com.d4vram.pillmate2.utils.formatQuantity

internal fun historyQuantityBadgeText(quantity: Double, isDrops: Boolean): String =
    if (isDrops) "${formatQuantity(quantity)} 💧" else "x${formatQuantity(quantity)}"
