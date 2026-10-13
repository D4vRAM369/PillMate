package com.d4vram.pillmate2.ui.components

internal fun historyQuantityBadgeText(quantity: Int, isDrops: Boolean): String =
    if (isDrops) "$quantity 💧" else "x$quantity"
