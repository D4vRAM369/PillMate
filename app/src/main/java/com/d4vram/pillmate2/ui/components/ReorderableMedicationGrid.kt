package com.d4vram.pillmate2.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
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
    onTakeMedication: (String, Int) -> Unit,
    onEditMedication: (String) -> Unit = {},
    onDeleteMedication: (String) -> Unit = {},
    onAddMedication: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggedItem by remember { mutableStateOf<Int?>(null) }
    var draggedItemKey by remember { mutableStateOf<String?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val localMedications = remember { mutableStateListOf<Medication>() }

    LaunchedEffect(medications, isDragging) {
        val incomingById = medications.associateBy { it.id }
        if (!isDragging || localMedications.isEmpty()) {
            localMedications.clear()
            localMedications.addAll(medications)
        } else {
            for (index in localMedications.indices) {
                val current = localMedications[index]
                val updated = incomingById[current.id]
                if (updated != null && updated != current) {
                    localMedications[index] = updated
                }
            }
        }
    }

    val gridState = rememberLazyGridState()
    val totalItems = localMedications.size + 1 // +1 por card de agregar
    val rows = (totalItems + 1) / 2
    val gridHeight = (rows * 180).dp

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.height(gridHeight),
        userScrollEnabled = !isDragging,
        state = gridState
    ) {
        itemsIndexed(
            localMedications,
            key = { _, medication -> medication.id }
        ) { index, medication ->
            val isBeingDragged = draggedItemKey == medication.id
            val translation = if (isBeingDragged) dragOffset else Offset.Zero

            val dragModifier = Modifier.pointerInput(medication.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        draggedItem = index
                        draggedItemKey = medication.id
                        isDragging = true
                        dragOffset = Offset.Zero
                    },
                    onDragEnd = {
                        draggedItem = null
                        draggedItemKey = null
                        isDragging = false
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = {
                        draggedItem = null
                        draggedItemKey = null
                        isDragging = false
                        dragOffset = Offset.Zero
                    }
                ) { change, dragAmount ->
                    change.consumePositionChange()
                    dragOffset += dragAmount

                    val currentIndex = draggedItem ?: return@detectDragGesturesAfterLongPress
                    val layoutInfo = gridState.layoutInfo
                    if (currentIndex !in localMedications.indices) return@detectDragGesturesAfterLongPress
                    val draggedInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == medication.id }
                        ?: return@detectDragGesturesAfterLongPress

                    val itemCenter = Offset(
                        x = draggedInfo.offset.x + draggedInfo.size.width / 2f + dragOffset.x,
                        y = draggedInfo.offset.y + draggedInfo.size.height / 2f + dragOffset.y
                    )

                    val targetInfo = layoutInfo.visibleItemsInfo.firstOrNull { info ->
                        itemCenter.x in info.offset.x.toFloat()..(info.offset.x + info.size.width).toFloat() &&
                        itemCenter.y in info.offset.y.toFloat()..(info.offset.y + info.size.height).toFloat()
                    }

                    val targetIndex = targetInfo?.index
                    if (targetIndex != null && targetIndex != currentIndex) {
                        val movedMedication = localMedications.removeAt(currentIndex)
                        val insertionIndex = if (targetIndex > currentIndex) {
                            targetIndex.coerceAtMost(localMedications.size)
                        } else {
                            targetIndex.coerceIn(0, localMedications.size)
                        }
                        localMedications.add(insertionIndex, movedMedication)
                        val delta = Offset(
                            (targetInfo.offset.x - draggedInfo.offset.x).toFloat(),
                            (targetInfo.offset.y - draggedInfo.offset.y).toFloat()
                        )
                        dragOffset -= delta
                        draggedItem = insertionIndex
                        draggedItemKey = movedMedication.id
                        onReorder(currentIndex, insertionIndex)
                    }
                }
            }

            MedicationCard(
                medication = medication,
                onTakeMedication = if (isDragging) { { _, _ -> } } else { onTakeMedication },
                onEditMedication = if (isDragging) { {} } else { onEditMedication },
                onDeleteMedication = if (isDragging) { {} } else { onDeleteMedication },
                enabled = !isDragging,
                dragModifier = dragModifier,
                modifier = Modifier
                    .animateItemPlacement()
                    .zIndex(if (isBeingDragged) 1f else 0f)
                    .graphicsLayer {
                        alpha = if (isBeingDragged) 0.8f else 1f
                        scaleX = if (isBeingDragged) 1.05f else 1f
                        scaleY = if (isBeingDragged) 1.05f else 1f
                        translationX = translation.x
                        translationY = translation.y
                    }
                    .shadow(
                        elevation = if (isBeingDragged) 8.dp else 0.dp,
                        shape = RoundedCornerShape(16.dp)
                    )
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
