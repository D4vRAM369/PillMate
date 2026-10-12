package com.d4vram.pillmate2.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.data.local.PillMateDatabase
import com.d4vram.pillmate2.data.voice.VoiceNoteRepository
import com.d4vram.pillmate2.ui.screens.*
import com.d4vram.pillmate2.ui.stats.StatisticsScreen
import com.d4vram.pillmate2.ui.export.PdfExportScreen
import com.d4vram.pillmate2.ui.theme.ThemeManager
import com.d4vram.pillmate2.ui.security.SecurityManager
import com.d4vram.pillmate2.ui.security.rememberVoiceNoteSecurityManager
import com.d4vram.pillmate2.ui.voicenotes.VoiceNotesViewModel
import com.d4vram.pillmate2.ui.permissions.PermissionsManager
import com.d4vram.pillmate2.ui.components.PermissionsRequestDialog
import com.d4vram.pillmate2.ui.onboarding.MainOnboardingScreen
import com.d4vram.pillmate2.ui.onboarding.OnboardingManager
import kotlinx.coroutines.launch

/**
 * 🧭 Sistema de navegación principal
 * Controla el flujo entre pantallas y el bottom navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PillMateNavigation(
    themeManager: ThemeManager,
    securityManager: SecurityManager,
    onboardingManager: OnboardingManager
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val repository = remember {
        val database = PillMateDatabase.getInstance(context)
        MedicationRepository(
            database = database,
            medicationDao = database.medicationDao(),
            historyDao = database.historyDao()
        )
    }

    val voiceNoteRepository = remember {
        val database = PillMateDatabase.getInstance(context)
        VoiceNoteRepository(
            context = context,
            voiceNoteDao = database.voiceNoteDao()
        )
    }

    val voiceNoteSecurityManager = rememberVoiceNoteSecurityManager()
    val voiceNotesViewModel: VoiceNotesViewModel = viewModel(
        factory = VoiceNotesViewModel.provideFactory(
            application = context.applicationContext as android.app.Application,
            repository = voiceNoteRepository,
            securityManager = voiceNoteSecurityManager
        )
    )

    // 📸 Gestor de permisos para fotografías de medicamentos
    val permissionsManager = remember { PermissionsManager(context) }
    val isMainOnboardingCompleted by onboardingManager.isMainOnboardingCompleted.collectAsState(initial = false)
    val shouldShowPermissionsDialog by permissionsManager.shouldShowPermissionsDialog.collectAsState(initial = false)

    if (!isMainOnboardingCompleted) {
        MainOnboardingScreen(
            onFinish = {
                coroutineScope.launch {
                    onboardingManager.completeMainOnboarding()
                }
            }
        )
        return
    }

    // Mostrar diálogo de permisos si es necesario
    if (shouldShowPermissionsDialog) {
        PermissionsRequestDialog(
            onPermissionsGranted = {
                coroutineScope.launch {
                    permissionsManager.markPermissionsRequested()
                }
            },
            onDismiss = {
                coroutineScope.launch {
                    permissionsManager.markPermissionsDialogShown()
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val currentRoute = currentDestination?.route

            // Solo mostrar la barra de navegación en las pantallas principales
            val showBottomBar = bottomNavItems.any { it.route == currentRoute }

            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                screen.icon?.let {
                                    Icon(
                                        imageVector = it,
                                        contentDescription = null
                                    )
                                }
                            },
                            label = {
                                screen.label?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigateToTab(screen.route)
                            }
                        )
                    }
                }
            }
        }

    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    repository = repository,
                    navController = navController,
                    themeManager = themeManager,
                    onAddMedication = {
                        navController.navigate("add_medication")
                    },
                    onEditMedication = { medicationId ->
                        navController.navigate("edit_medication/$medicationId")
                    },
                    onDeleteMedication = { medicationId ->
                        repository.deleteMedication(medicationId)
                    }
                )
            }

            composable(Screen.Medications.route) {
                MedicationsScreen(
                    repository = repository,
                    themeManager = themeManager,
                    onAddMedication = {
                        navController.navigate("add_medication")
                    },
                    onEditMedication = { medicationId ->
                        navController.navigate("edit_medication/$medicationId")
                    },
                    onDeleteMedication = { medicationId ->
                        repository.deleteMedication(medicationId)
                    }
                )
            }

            composable(Screen.History.route) {
                HistoryScreen(repository = repository, navController = navController)
            }

            // Historial abierto en un día concreto (desde el detalle de un día en Estadísticas).
            // Va apilado encima: "atrás" vuelve a Estadísticas.
            composable("history_day/{epochDay}") { entry ->
                HistoryScreen(
                    repository = repository,
                    navController = navController,
                    initialDay = entry.arguments?.getString("epochDay")?.toLongOrNull()?.let(java.time.LocalDate::ofEpochDay)
                )
            }

            composable(Screen.VoiceNotes.route) {
                VoiceNotesScreen(viewModel = voiceNotesViewModel)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    themeManager = themeManager,
                    repository = repository,
                    securityManager = securityManager,
                    onNavigateToPdfExport = {
                        navController.navigate(Screen.PdfExport.route)
                    }
                )
            }

            // Pantalla de exportación PDF (accesible desde Ajustes)
            composable(Screen.PdfExport.route) {
                PdfExportScreen(
                    repository = repository,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            // Pantalla para agregar medicamento
            composable("add_medication") {
                AddEditMedicationScreen(
                    repository = repository,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            // Pantalla para editar medicamento
            composable("edit_medication/{medicationId}") { backStackEntry ->
                val medicationId = backStackEntry.arguments?.getString("medicationId")
                AddEditMedicationScreen(
                    repository = repository,
                    medicationId = medicationId,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            // Pantalla para ver estadísticas de un medicamento
            composable(Screen.MedicationStats.route) { backStackEntry ->
                val medicationId = backStackEntry.arguments?.getString("medicationId")
                MedicationStatsScreen(
                    medicationId = medicationId,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    repository = repository
                )
            }

            // Pantalla de estadísticas globales con gráficos
            composable(Screen.GlobalStats.route) {
                StatisticsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    repository = repository,
                    navController = navController
                )
            }
        }
    }
}

/**
 * 📱 Definición de las pantallas de navegación
 */
sealed class Screen(val route: String, val label: String? = null, val icon: ImageVector? = null) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Medications : Screen("medications", "Meds", Icons.Default.Add)
    object History : Screen("history", "History", Icons.AutoMirrored.Filled.List)
    object VoiceNotes : Screen("voice_notes", "Notes", Icons.Default.Mic)
    object Settings : Screen("settings", "Ajustes", Icons.Default.Settings)
    object MedicationStats : Screen("medication_stats/{medicationId}")
    object GlobalStats : Screen("global_stats")
    object PdfExport : Screen("pdf_export")
}

private val bottomNavItems = listOf(
    Screen.Home,
    Screen.Medications,
    Screen.History,
    Screen.VoiceNotes,
    Screen.Settings
)

/**
 * Ir a una pantalla de la barra inferior. TODA navegación a una pestaña pasa por aquí, también
 * la que sale de un botón dentro de otra pantalla.
 *
 * Un `navigate("history")` a secas apila Historial encima de Home. Al pulsar luego la pestaña Home,
 * Navigation guarda ese Historial como "el estado de Home" y lo restaura acto seguido: la pestaña
 * parece no reaccionar.
 */
fun androidx.navigation.NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
