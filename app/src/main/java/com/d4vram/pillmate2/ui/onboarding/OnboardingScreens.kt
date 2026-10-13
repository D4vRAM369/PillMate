package com.d4vram.pillmate2.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.ui.timeline.RhythmClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

private const val PAGE_COUNT = 4

/**
 * Introducción de la app: cuatro páginas deslizables. Cada una enseña una idea
 * con una demo viva (se puede tocar) en vez de con una ilustración estática.
 */
@Composable
fun MainOnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == PAGE_COUNT - 1

    // "Atrás" retrocede una página en vez de cerrar la app.
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PageDots(current = pagerState.currentPage)
                // En la última página "Saltar" no aporta nada, pero se conserva su hueco
                // para que la cabecera no salte.
                TextButton(onClick = onFinish, enabled = !isLast, modifier = Modifier.alpha(if (isLast) 0f else 1f)) {
                    Text("Saltar")
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                pageSpacing = 24.dp
            ) { page ->
                // isCurrent arranca las animaciones solo cuando la página está delante.
                val isCurrent = pagerState.currentPage == page
                when (page) {
                    0 -> OnboardingPage(
                        eyebrow = "REGISTRAR",
                        title = "Apunta la toma cuando la tomas",
                        body = "PillMate es para la medicación a demanda: la que tomas cuando la necesitas. " +
                            "No hay horarios que cumplir ni alarmas que te persigan.",
                        note = "Un toque basta. También desde el widget y desde la notificación, sin abrir la app."
                    ) { side -> TapDemo(side) }

                    1 -> OnboardingPage(
                        eyebrow = "EL RELOJ",
                        title = "Tu día en un reloj de 24 horas",
                        body = "Cada punto es una toma, colocada en su hora y con el color de su medicamento. " +
                            "Las 00 están arriba y gira como un reloj normal.",
                        note = "Pruébalo: toca un punto para ver qué fue."
                    ) { side -> DayClockDemo(side, isCurrent) }

                    2 -> OnboardingPage(
                        eyebrow = "TU RITMO",
                        title = "Con más días aparece tu patrón",
                        body = "Al juntar varios días, los puntos se convierten en barras: cuanto más larga, " +
                            "más tomas a esa hora. PillMate calcula tu hora más activa y cada cuánto sueles tomar.",
                        note = "Describe, no juzga: sin puntuaciones, sin objetivos y sin consejo médico."
                    ) { side -> WeekClockDemo(side, isCurrent) }

                    else -> OnboardingPage(
                        eyebrow = "PRIVACIDAD",
                        title = "Lo que apuntas no sale de tu móvil",
                        body = "Sin cuentas, sin anuncios y sin analítica. Tus datos son tuyos y se quedan contigo.",
                        note = null
                    ) { PrivacyDemo(isCurrent) }
                }
            }

            Button(
                onClick = {
                    if (isLast) onFinish()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(if (isLast) "Empezar" else "Siguiente")
            }
        }
    }
}

@Composable
private fun PageDots(current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(PAGE_COUNT) { index ->
            val width by animateDpAsState(if (index == current) 24.dp else 8.dp, label = "dot")
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

/**
 * Esqueleto común: demo + texto. En vertical van apilados; en horizontal, lado a lado.
 * [demo] recibe el lado disponible para que cada demo se dimensione sola.
 */
@Composable
private fun OnboardingPage(
    eyebrow: String,
    title: String,
    body: String,
    note: String?,
    demo: @Composable (side: Dp) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pageWidth = maxWidth
        val pageHeight = maxHeight
        val text: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .padding(12.dp)
                    )
                }
            }
        }

        if (pageWidth > pageHeight) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DemoStage(Modifier.weight(1f).fillMaxHeight()) {
                    demo(minOf(pageWidth / 2, pageHeight))
                }
                text(Modifier.weight(1f).verticalScroll(rememberScrollState()))
            }
        } else {
            // La demo ocupa algo menos de media página, con tope por arriba y por abajo;
            // si el texto no cabe (letra grande, pantalla pequeña) la página hace scroll.
            val stageHeight = (pageHeight * 0.46f).coerceIn(190.dp, 360.dp)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                DemoStage(Modifier.fillMaxWidth().heightIn(min = stageHeight)) {
                    demo(minOf(pageWidth, stageHeight))
                }
                text(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DemoStage(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

// ── Página 1: una casilla de acceso rápido que se puede tocar ────────────────

@Composable
private fun TapDemo(side: Dp) {
    var count by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(1f) }
    val halo by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "halo"
    )
    val undoAlpha by animateFloatAsState(if (count > 0) 1f else 0f, label = "undo")
    val tile = (side * 0.42f).coerceIn(96.dp, 136.dp)
    val medication = SampleMeds[0]

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // El margen deja sitio al halo, que crece más allá de la casilla.
        Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
            // El halo que late invita a tocar; desaparece tras la primera toma.
            if (count == 0) {
                Box(
                    Modifier
                        .size(tile)
                        .scale(halo)
                        .clip(RoundedCornerShape(26.dp))
                        .background(medication.color.copy(alpha = 0.25f))
                )
            }
            Column(
                modifier = Modifier
                    .size(tile)
                    .scale(press.value)
                    .clip(RoundedCornerShape(24.dp))
                    .background(medication.color)
                    .clickable {
                        count++
                        scope.launch {
                            press.snapTo(0.88f)
                            press.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(medication.emoji, fontSize = 30.sp)
                Text(
                    medication.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
        Text(
            text = when (count) {
                0 -> "Toca la casilla"
                1 -> "Hoy: 1 toma"
                else -> "Hoy: $count tomas"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        // Siempre ocupa su sitio (solo cambia la opacidad) para que nada salte al aparecer.
        Row(
            modifier = Modifier
                .alpha(undoAlpha)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.inverseSurface)
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Toma registrada",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface
            )
            TextButton(onClick = { count-- }, enabled = count > 0) {
                Text("Deshacer", color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}

// ── Páginas 2 y 3: el reloj real de la app con datos de ejemplo ──────────────

@Composable
private fun DayClockDemo(side: Dp, isCurrent: Boolean) {
    val entries = remember { sampleDay() }
    val shown = revealedCount(total = entries.size, isCurrent = isCurrent, stepMillis = 340)
    RhythmClock(entries = entries.take(shown), diameter = clockDiameter(side))
}

@Composable
private fun WeekClockDemo(side: Dp, isCurrent: Boolean) {
    val entries = remember { sampleWeek() }
    val today = remember { LocalDate.now() }
    // Día a día: con un solo día el reloj pinta puntos; desde el segundo, barras.
    val days = revealedCount(total = SAMPLE_DAYS, isCurrent = isCurrent, stepMillis = 520)
    val visible = entries.filter { it.timestamp.toLocalDate() > today.minusDays(days.toLong()) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (days <= 1) "1 día" else "$days días",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        RhythmClock(entries = visible, diameter = clockDiameter(side) - 20.dp)
    }
}

/** El reloj necesita sitio debajo para su texto de ayuda y los chips al tocar. */
private fun clockDiameter(side: Dp): Dp = (side - 84.dp).coerceIn(132.dp, 260.dp)

/** Cuenta de 0 a [total], un paso cada [stepMillis], cada vez que la página pasa a primer plano. */
@Composable
private fun revealedCount(total: Int, isCurrent: Boolean, stepMillis: Long): Int {
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        shown = 0
        while (shown < total) {
            delay(stepMillis)
            shown++
        }
    }
    return shown
}

// ── Página 4: tres hechos que entran uno a uno ───────────────────────────────

private data class PrivacyFact(val icon: ImageVector, val title: String, val text: String)

private val PrivacyFacts = listOf(
    PrivacyFact(Icons.Default.WifiOff, "Sin internet", "La app no tiene permiso de red: no puede enviar nada."),
    PrivacyFact(Icons.Default.Inventory2, "Copias cuando tú quieras", "Copia completa en ZIP y exportación a CSV o PDF."),
    PrivacyFact(Icons.Default.Fingerprint, "Bloqueo opcional", "Protege la app con tu huella.")
)

@Composable
private fun PrivacyDemo(isCurrent: Boolean) {
    val shown = revealedCount(total = PrivacyFacts.size, isCurrent = isCurrent, stepMillis = 380)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrivacyFacts.forEachIndexed { index, fact ->
            // El hueco se reserva siempre; solo el contenido entra animado.
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = shown > index,
                    enter = fadeIn() + slideInVertically { it / 2 }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(fact.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column {
                            Text(
                                fact.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                fact.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Datos de ejemplo (nombres genéricos, nunca medicación real) ──────────────

private data class SampleMed(val name: String, val emoji: String, val color: Color)

private val SampleMeds = listOf(
    SampleMed("Analgésico", "💊", Color(0xFF0F9D8A)),
    SampleMed("Antihistamínico", "🌿", Color(0xFF6C63D9)),
    SampleMed("Vitamina", "🍊", Color(0xFFE08A2E))
)

private const val SAMPLE_DAYS = 7

private fun sampleEntry(at: LocalDateTime, medication: SampleMed) = HistoryEntry(
    id = "$at-${medication.name}",
    medicationId = medication.name,
    medicationName = medication.name,
    time = "",
    date = "",
    color = medication.color,
    emoji = medication.emoji,
    timestamp = at
)

private fun sampleDay(): List<HistoryEntry> {
    val today = LocalDate.now()
    return listOf(
        sampleEntry(today.atTime(8, 10), SampleMeds[2]),
        sampleEntry(today.atTime(11, 40), SampleMeds[1]),
        sampleEntry(today.atTime(15, 5), SampleMeds[0]),
        sampleEntry(today.atTime(19, 30), SampleMeds[1]),
        sampleEntry(today.atTime(22, 50), SampleMeds[0])
    )
}

/** Una semana con un patrón reconocible: mañana, sobremesa y, sobre todo, noche. */
private fun sampleWeek(): List<HistoryEntry> {
    val today = LocalDate.now()
    val pattern = listOf(7 to 2, 8 to 2, 9 to 1, 13 to 0, 14 to 0, 16 to 1, 21 to 0, 22 to 0, 22 to 1, 23 to 0)
    return (0 until SAMPLE_DAYS).flatMap { day ->
        pattern
            .filterIndexed { index, _ -> index >= 6 || (index + day) % 2 == 0 }
            .map { (hour, med) -> sampleEntry(today.minusDays(day.toLong()).atTime(hour, (day * 7) % 60), SampleMeds[med]) }
    }
}
