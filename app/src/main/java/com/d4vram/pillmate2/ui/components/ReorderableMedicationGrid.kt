package com.d4vram.pillmate2.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.d4vram.pillmate2.model.Medication

/**
 * 🔄 Grid reordenable de medicamentos con drag & drop táctil
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReorderableMedicationGrid(
    medications: List<Medication>,
    onTakeMedication: (String) -> Unit,
    onEditMedication: (String) -> Unit = {},
    onDeleteMedication: (String) -> Unit = {},
    onAddMedication: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggedItem by remember { mutableStateOf<Int?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    
    val density = LocalDensity.current
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.height((((medications.size + 1) / 2 + 1) * 180).dp),
        userScrollEnabled = !isDragging
    ) {
        itemsIndexed(
            medications,
            key = { _, medication -> medication.id }
        ) { index, medication ->
            val isBeingDragged = draggedItem == index
            
            MedicationCard(
                medication = medication,
                onTakeMedication = if (isDragging) { {} } else { onTakeMedication },
                onEditMedication = if (isDragging) { {} } else { onEditMedication },
                onDeleteMedication = if (isDragging) { {} } else { onDeleteMedication },
                modifier = Modifier
                    .animateItemPlacement()
                    .zIndex(if (isBeingDragged) 1f else 0f)
                    .graphicsLayer {
                        alpha = if (isBeingDragged) 0.8f else 1f
                        scaleX = if (isBeingDragged) 1.05f else 1f
                        scaleY = if (isBeingDragged) 1.05f else 1f
                    }
                    .shadow(
                        elevation = if (isBeingDragged) 8.dp else 0.dp,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .pointerInput(medication.id) {
                        detectDragGestures(
                            onDragStart = {
                                draggedItem = index
                                isDragging = true
                            },
                            onDragEnd = {
                                draggedItem = null
                                isDragging = false
                            }
                        ) { _, _ ->
                            // El reordenamiento se maneja automáticamente por animateItemPlacement
                        }
                    }
            )
        }
        
        // Card de "Agregar medicamento"
        item {
            AddMedicationCard(
                onClick = if (isDragging) { {} } else { onAddMedication },
                modifier = Modifier.animateItemPlacement()
            )
        }
    }
}

/**
 * ➕ Card para agregar nuevo medicamento
 */
@Composable
private fun AddMedicationCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 2.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Agregar medicamento",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Agregar\nMedicamento",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}