// kotlin
package com.d4vram.pillmate2.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.d4vram.pillmate2.R
import androidx.compose.ui.unit.dp
import com.d4vram.pillmate2.model.Medication

/**
 * Representa un medicamento en el dropdown con información de su jerarquía
 */
data class MedicationDropdownItem(
    val medication: Medication,
    val isChild: Boolean,
    val isLastChild: Boolean
)

@Composable
fun MedicationFilterDropdown(
    medications: List<Medication>,
    selectedId: String?,
    onMedicationSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedMed = medications.find { it.id == selectedId }

    // Organizar medicamentos en familias (padres con hijos indentados)
    val organizedMedications = remember(medications) {
        organizeMedicationsWithFamilies(medications)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            onClick = { expanded = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedMed != null) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = selectedMed.color,
                                    shape = CircleShape
                                )
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Mostrar nombre con dosis si está seleccionado
                    val displayText = if (selectedMed != null) {
                        "${selectedMed.name} ${selectedMed.dose}"
                    } else {
                        stringResource(R.string.all_medications)
                    }
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(400.dp),
            scrollState = rememberScrollState()
        ) {
            // Opción "Todos"
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(stringResource(R.string.all_medications))
                    }
                },
                onClick = {
                    onMedicationSelected(null)
                    expanded = false
                },
                leadingIcon = if (selectedId == null) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )

            HorizontalDivider()

            // Medicamentos organizados por familias
            organizedMedications.forEach { item ->
                val medication = item.medication
                val isSelected = selectedId == medication.id

                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Indentación visual para hijos
                            if (item.isChild) {
                                Text(
                                    text = if (item.isLastChild) "└─" else "├─",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        color = medication.color,
                                        shape = CircleShape
                                    )
                            )

                            Column {
                                Text(
                                    text = medication.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (item.isChild) FontWeight.Normal else FontWeight.Medium
                                )
                                Text(
                                    text = medication.dose,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onMedicationSelected(medication.id)
                        expanded = false
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    modifier = if (item.isChild) {
                        Modifier.padding(start = 16.dp)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

/**
 * Organiza los medicamentos en una lista plana con información de jerarquía
 * Los padres aparecen primero, seguidos de sus hijos indentados
 */
private fun organizeMedicationsWithFamilies(medications: List<Medication>): List<MedicationDropdownItem> {
    val result = mutableListOf<MedicationDropdownItem>()

    // Separar padres (sin parentMedicationId) e hijos
    val parents = medications.filter { it.parentMedicationId == null }
    val childrenByParent = medications
        .filter { it.parentMedicationId != null }
        .groupBy { it.parentMedicationId }

    // Ordenar padres por nombre
    val sortedParents = parents.sortedBy { it.name.lowercase() }

    for (parent in sortedParents) {
        // Añadir el padre
        result.add(MedicationDropdownItem(
            medication = parent,
            isChild = false,
            isLastChild = false
        ))

        // Añadir sus hijos (ordenados por dosis descendente)
        val children = childrenByParent[parent.id] ?: emptyList()
        val sortedChildren = children.sortedByDescending {
            extractNumericDose(it.dose)
        }

        sortedChildren.forEachIndexed { index, child ->
            result.add(MedicationDropdownItem(
                medication = child,
                isChild = true,
                isLastChild = index == sortedChildren.lastIndex
            ))
        }
    }

    return result
}

/**
 * Extrae el valor numérico de una dosis para ordenar
 */
private fun extractNumericDose(dose: String): Float {
    val regex = Regex("([0-9]+[.,]?[0-9]*)")
    val match = regex.find(dose)
    return match?.groupValues?.get(1)?.replace(",", ".")?.toFloatOrNull() ?: 0f
}
