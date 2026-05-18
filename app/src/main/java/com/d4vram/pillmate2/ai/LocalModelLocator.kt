package com.d4vram.pillmate2.ai

import android.content.Context
import java.io.File

class LocalModelLocator(context: Context) : ModelLocator {

    private val appContext = context.applicationContext

    override fun locate(): LocalModelStatus {
        val internalDir = File(appContext.filesDir, "llm")
        val activePointer = File(internalDir, "active-model-path.txt")
        val candidates = buildList {
            add(File(internalDir, "model.task"))
            add(File(internalDir, "model_version.task"))
            internalDir.listFiles { file -> file.isFile && file.extension.equals("task", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { add(it) }
            appContext.getExternalFilesDir("llm")?.let { dir ->
                add(File(dir, "model.task"))
                add(File(dir, "model_version.task"))
                dir.listFiles { file -> file.isFile && file.extension.equals("task", ignoreCase = true) }
                    ?.sortedByDescending { it.lastModified() }
                    ?.forEach { add(it) }
            }
        }.distinctBy { it.absolutePath }

        val pointedModel = activePointer
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() && it.isFile }

        val activeModel = pointedModel ?: candidates.firstOrNull { it.exists() && it.isFile }

        return LocalModelStatus(
            activeModelPath = activeModel?.absolutePath,
            candidatePaths = buildList {
                pointedModel?.absolutePath?.let { add(it) }
                addAll(candidates.map { it.absolutePath })
            }.distinct()
        )
    }
}
