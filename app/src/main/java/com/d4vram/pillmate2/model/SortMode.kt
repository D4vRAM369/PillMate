package com.d4vram.pillmate2.model

/**
 * 🔢 Modos de ordenamiento de medicamentos
 * Define cómo se ordenarán los medicamentos en la interfaz
 */
enum class SortMode(val title: String, val description: String) {
    /**
     * Orden manual definido por el usuario mediante drag-and-drop
     * Este orden persiste hasta que el usuario lo cambie manualmente
     */
    MANUAL(
        title = "Orden Manual",
        description = "Arrastra para reordenar. El orden se guardará automáticamente"
    ),

    /**
     * Ordenar por última toma (más reciente primero)
     * Los medicamentos nunca tomados aparecen al final
     */
    LAST_TAKEN(
        title = "Por Última Toma",
        description = "Los tomados recientemente aparecen primero"
    ),

    /**
     * Ordenar por más utilizados en los últimos 7 días
     * Se cuenta el número total de tomas en la última semana
     */
    MOST_USED_WEEK(
        title = "Más Usados (Semana)",
        description = "Los más utilizados en los últimos 7 días primero"
    ),

    /**
     * Orden alfabético por nombre del medicamento (A-Z)
     */
    ALPHABETICAL(
        title = "Orden Alfabético",
        description = "Ordenados de A a Z por nombre"
    ),

    /**
     * Orden por fecha de creación (más antiguos primero)
     * Usa el timestamp de cuando se añadió el medicamento
     */
    CREATION_ORDER(
        title = "Por Orden de Creación",
        description = "Los medicamentos más antiguos aparecen primero"
    );
}
