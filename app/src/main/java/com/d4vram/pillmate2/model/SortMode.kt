package com.d4vram.pillmate2.model

import androidx.annotation.StringRes
import com.d4vram.pillmate2.R

/**
 * 🔢 Modos de ordenamiento de medicamentos
 * Define cómo se ordenarán los medicamentos en la interfaz
 */
enum class SortMode(@StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
    /**
     * Orden manual definido por el usuario mediante drag-and-drop
     * Este orden persiste hasta que el usuario lo cambie manualmente
     */
    MANUAL(
        titleRes = R.string.sort_manual,
        descriptionRes = R.string.sort_manual_desc
    ),

    /**
     * Ordenar por última toma (más reciente primero)
     * Los medicamentos nunca tomados aparecen al final
     */
    LAST_TAKEN(
        titleRes = R.string.sort_last_taken,
        descriptionRes = R.string.sort_last_taken_desc
    ),

    /**
     * Ordenar por más utilizados en los últimos 7 días
     * Se cuenta el número total de tomas en la última semana
     */
    MOST_USED_WEEK(
        titleRes = R.string.sort_most_used,
        descriptionRes = R.string.sort_most_used_desc
    ),

    /**
     * Orden alfabético por nombre del medicamento (A-Z)
     */
    ALPHABETICAL(
        titleRes = R.string.sort_alphabetical,
        descriptionRes = R.string.sort_alphabetical_desc
    ),

    /**
     * Orden por fecha de creación (más antiguos primero)
     * Usa el timestamp de cuando se añadió el medicamento
     */
    CREATION_ORDER(
        titleRes = R.string.sort_creation,
        descriptionRes = R.string.sort_creation_desc
    );
}
