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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.d4vram.pillmate2.model.Medication
import kotlinx.coroutines.Job
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 🔄 Grid reordenable de medicamentos con drag & drop táctil
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReorderableMedicationGrid(
    medications: List<Medication>,
    repository: com.d4vram.pillmate2.data.MedicationRepository,
    chipCountPeriod: com.d4vram.pillmate2.ui.theme.ChipCountPeriod,
    onTakeMedication: (String, Int, LocalDateTime?) -> Unit,
    onEditMedication: (String) -> Unit = {},
    onDeleteMedication: (String) -> Unit = {},
    onAddMedication: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    dragEnabled: Boolean = true,
    parentListState: androidx.compose.foundation.lazy.LazyListState? = null,
    modifier: Modifier = Modifier,
    busy: Boolean = false
) {
    var draggedItem by remember { mutableStateOf<Int?>(null) }
    var draggedItemKey by remember { mutableStateOf<String?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragSessionId by remember { mutableIntStateOf(0) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    val localMedications = remember { mutableStateListOf<Medication>() }
    val coroutineScope = rememberCoroutineScope()

    // Obtener altura de la pantalla
    val configuration = LocalConfiguration.current
    val screenHeight = with(LocalDensity.current) {
        configuration.screenHeightDp.dp.toPx()
    }

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

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.heightIn(min = 0.dp, max = 10000.dp), // Altura flexible grande
        userScrollEnabled = false, // El LazyColumn padre maneja todo el scroll
        state = gridState
    ) {
        itemsIndexed(
            localMedications,
            key = { _, medication -> medication.id }
        ) { index, medication ->
            val isBeingDragged = draggedItemKey == medication.id
            val translation = if (isBeingDragged) dragOffset else Offset.Zero

            val dragModifier = if (dragEnabled) {
                Modifier.pointerInput(medication.id, dragSessionId) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggedItem = index
                            draggedItemKey = medication.id
                            isDragging = true
                            dragOffset = Offset.Zero
                        },
                    onDragEnd = {
                        autoScrollJob?.cancel()  // Detener el auto-scroll
                        autoScrollJob = null
                        draggedItem = null
                        draggedItemKey = null
                        isDragging = false
                        dragOffset = Offset.Zero
                        dragSessionId++ // Reiniciar el gesture detector
                    },
                    onDragCancel = {
                        autoScrollJob?.cancel()  // Detener el auto-scroll
                        autoScrollJob = null
                        draggedItem = null
                        draggedItemKey = null
                        isDragging = false
                        dragOffset = Offset.Zero
                        dragSessionId++ // Reiniciar el gesture detector
                    }
                ) { change, dragAmount ->
                    change.consume()
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

                    // Auto-scroll basado en posición del item en el grid
                    if (parentListState != null) {
                        val autoScrollZone = 200f  // 200px desde cada borde
                        val itemY = itemCenter.y  // Posición Y del item en el grid

                        if (itemY < autoScrollZone) {
                            // Cerca del borde superior: scroll hacia arriba
                            autoScrollJob?.cancel()
                            autoScrollJob = coroutineScope.launch {
                                while (isActive) {
                                    var scrolled = 0f
                                    parentListState.scroll {
                                        scrolled = scrollBy(-20f)
                                    }
                                    dragOffset = dragOffset.copy(y = dragOffset.y - scrolled)
                                    delay(16)
                                }
                            }
                        } else if (itemY > layoutInfo.viewportEndOffset - autoScrollZone) {
                            // Cerca del borde inferior: scroll hacia abajo
                            autoScrollJob?.cancel()
                            autoScrollJob = coroutineScope.launch {
                                while (isActive) {
                                    var scrolled = 0f
                                    parentListState.scroll {
                                        scrolled = scrollBy(20f)
                                    }
                                    dragOffset = dragOffset.copy(y = dragOffset.y - scrolled)
                                    delay(16)
                                }
                            }
                        } else {
                            // Fuera de las zonas: cancelar auto-scroll
                            autoScrollJob?.cancel()
                            autoScrollJob = null
                        }
                    }

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
            } else {
                Modifier
            }

            val cardModifier = Modifier
                .animateItem()
                .then(dragModifier)
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

            MedicationCard(
                medication = medication,
                repository = repository,
                chipCountPeriod = chipCountPeriod,
                onTakeMedication = if (isDragging) { { _, _, _ -> } } else { onTakeMedication },
                onEditMedication = if (isDragging) { {} } else { onEditMedication },
                onDeleteMedication = if (isDragging) { {} } else { onDeleteMedication },
                enabled = !isDragging,
                busy = busy,
                dragModifier = dragModifier,
                modifier = cardModifier
            )
        }
        
        // Card de "Agregar medicamento"
        item {
            AddMedicationCard(
                onClick = if (isDragging) { {} } else { onAddMedication },
                modifier = Modifier.animateItem()
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
