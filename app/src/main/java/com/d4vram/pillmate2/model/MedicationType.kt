package com.d4vram.pillmate2.model

import androidx.annotation.StringRes
import com.d4vram.pillmate2.R

/**
 * Tipos de medicamento que la app reconoce.
 *
 * [stored] es el texto que se guarda en la base de datos, en el CSV y en los backups, y con el que
 * Estadísticas y el PDF deciden la unidad. No cambiarlo: rompería los datos ya guardados.
 * Con el idioma solo cambia cómo se enseña ([labelRes]).
 */
enum class MedicationType(val stored: String, @StringRes val labelRes: Int, private vararg val aliases: String) {
    TABLET("Comprimido", R.string.med_type_tablet),
    PILL("Pastilla", R.string.med_type_pill),
    CAPSULE("Cápsula", R.string.med_type_capsule, "Capsula"),
    SYRUP("Jarabe", R.string.med_type_syrup),
    DROPS("Gotas", R.string.med_type_drops, "Gota");

    private fun matchesStored(text: String) =
        stored.equals(text, ignoreCase = true) || aliases.any { it.equals(text, ignoreCase = true) }

    companion object {
        val DEFAULT = TABLET

        fun fromStored(text: String): MedicationType? {
            val trimmed = text.trim()
            return entries.firstOrNull { it.matchesStored(trimmed) }
        }

        /** Texto que ve el usuario. Un tipo que no se reconoce (escrito a mano) se enseña tal cual. */
        fun label(stored: String, getString: (Int) -> String): String {
            val type = fromStored(stored) ?: return stored
            val label = getString(type.labelRes)
            // En español la etiqueta ya es lo guardado: se respeta lo que hay ("Gota" no pasa a "Gotas").
            return if (label.equals(type.stored, ignoreCase = true)) stored else label
        }

        /**
         * Texto que se guarda a partir de lo escrito en el formulario. [original] es lo que había
         * guardado al abrir el formulario (null si el medicamento es nuevo).
         */
        fun toStored(input: String, original: String?, getString: (Int) -> String): String {
            val text = input.trim()
            val type = entries.firstOrNull { getString(it.labelRes).equals(text, ignoreCase = true) } ?: return text
            if (getString(type.labelRes).equals(type.stored, ignoreCase = true)) return text
            return if (original != null && fromStored(original) == type) original else type.stored
        }
    }
}
