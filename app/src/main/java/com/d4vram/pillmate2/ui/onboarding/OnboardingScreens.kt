package com.d4vram.pillmate2.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class OnboardingPageModel(
    val eyebrow: String,
    val title: String,
    val description: String,
    val accent: Color,
    val icon: ImageVector,
    val bullets: List<String>
)

@Composable
fun MainOnboardingScreen(
    onFinish: () -> Unit
) {
    val pages = listOf(
        OnboardingPageModel(
            eyebrow = "PILLMATE AI",
            title = "Tu tratamiento con una experiencia mucho más clara",
            description = "Organiza medicación, registra tomas y entiende tu progreso con una interfaz más limpia, visual y útil desde el primer minuto.",
            accent = Color(0xFF14B8A6),
            icon = Icons.Default.MonitorHeart,
            bullets = listOf(
                "Seguimiento rápido de medicación y tomas",
                "Vista global del tratamiento sin perder contexto",
                "Base preparada para un modo IA privado y local"
            )
        ),
        OnboardingPageModel(
            eyebrow = "ESTADÍSTICAS",
            title = "Patrones, tendencia y horas activas sin ruido",
            description = "Las estadísticas están pensadas para leer el tratamiento de un vistazo y detectar cambios relevantes del periodo visible.",
            accent = Color(0xFF6366F1),
            icon = Icons.Default.AutoGraph,
            bullets = listOf(
                "Comparación por semana, quincena o histórico",
                "Distribución horaria y consistencia del registro",
                "Contexto real para el futuro chat con IA"
            )
        ),
        OnboardingPageModel(
            eyebrow = "PRIVACIDAD",
            title = "Privacidad local, permisos visibles y controlables",
            description = "PillMate AI trata datos sensibles con una filosofía local-first: control explícito, permisos comprensibles y funciones separadas.",
            accent = Color(0xFF0EA5E9),
            icon = Icons.Default.Lock,
            bullets = listOf(
                "Fotos y datos tratados en el dispositivo",
                "Sin telemetría en el modo IA local",
                "Flujos de permisos más claros y menos agresivos"
            )
        )
    )

    var pageIndex by remember { mutableIntStateOf(0) }
    val page = pages[pageIndex]
    val isLast = pageIndex == pages.lastIndex

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF4FBFF)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFF8FCFF), Color(0xFFEAF8F4))
                    )
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = page.eyebrow,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = page.accent
                    )
                    OutlinedButton(onClick = onFinish) {
                        Text("Saltar")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 340.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(page.accent.copy(alpha = 0.18f), Color.White)
                            )
                        )
                        .padding(28.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(page.accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = null,
                                tint = page.accent,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        Card(
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.92f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = page.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF10243D)
                                )
                                Text(
                                    text = page.description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFF45607A)
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    page.bullets.forEach { bullet ->
                        OnboardingBullet(text = bullet, accent = page.accent)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pages.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .size(width = if (index == pageIndex) 28.dp else 10.dp, height = 10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pageIndex) page.accent else page.accent.copy(alpha = 0.22f)
                                )
                        )
                    }
                }

                Button(
                    onClick = {
                        if (isLast) onFinish() else pageIndex++
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(if (isLast) "Comenzar" else "Continuar")
                }
            }
        }
    }
}

@Composable
private fun OnboardingBullet(
    text: String,
    accent: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF193650)
        )
    }
}
