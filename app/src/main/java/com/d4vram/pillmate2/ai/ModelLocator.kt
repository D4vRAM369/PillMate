package com.d4vram.pillmate2.ai

fun interface ModelLocator {
    fun locate(): LocalModelStatus
}
