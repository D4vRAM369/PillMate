package com.d4vram.pillmate2.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4vram.pillmate2.model.Medication
import kotlinx.coroutines.delay

/**
 * 💊 Card de medicamento clickeable
 * El componente más importante: muestra cada medicamento como un tile
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationCard(
    medication: Medication,
    onTakeMedication: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Estado para la animación al hacer click
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_scale"
    )

    // Determinar si ya se tomó hoy
    val isTakenToday = medication.takenToday > 0

    Card(
        modifier = modifier
            .scale(scale)
            .clickable {
                isPressed = true
                onTakeMedication(medication.id)
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTakenToday) {
                medication.color.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isTakenToday) 8.dp else 4.dp
        ),
        border = if (isTakenToday) {
            CardDefaults.outlinedCardBorder().copy(
                width = 2.dp
            )
        } else null
    ) {
        // Efecto de reset de animación
        LaunchedEffect(isPressed) {
            if (isPressed) {
                delay(150)
                isPressed = false
            }
        }

        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Contador en la esquina superior derecha
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CountBadge(
                        current = medication.takenToday,
                        goal = medication.dailyGoal,
                        isPrn = medication.isPrn,
                        isTaken = isTakenToday,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Icono de la pastilla
                PillIcon(
                    emoji = medication.emoji,
                    color = medication.color,
                    isTaken = isTakenToday
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Nombre del medicamento
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Dosis y tipo
                Text(
                    text = "${medication.dose} • ${medication.type}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Última toma (solo visible si ya se tomó)
                if (isTakenToday && medication.lastTaken != null) {
                    Text(
                        text = "Última: ${medication.lastTaken}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = medication.color
                    )
                }
            }
        }
    }
}

/**
 * 🏷️ Badge que muestra el conteo de dosis tomadas
 */
@Composable
private fun CountBadge(
    current: Int,
    goal: Int,
    isPrn: Boolean,
    isTaken: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isPrn -> Color(0xFFEF4444)
        isTaken -> Color(0xFF10B981)
        else -> Color(0xFFF3F4F6)
    }

    val textColor = when {
        isPrn || isTaken -> Color.White
        else -> Color(0xFF4B5563)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Text(
            text = if (isPrn) "PRN" else "$current/$goal",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = textColor
        )
    }
}

/**
 * 💊 Icono visual de la pastilla con check si se tomó
 */
@Composable
private fun PillIcon(
    emoji: String,
    color: Color,
    isTaken: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        // Círculo de fondo
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 24.sp
            )
        }

        // Check verde si se tomó
        if (isTaken) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}