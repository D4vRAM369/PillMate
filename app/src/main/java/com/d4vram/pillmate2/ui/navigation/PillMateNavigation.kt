package com.d4vram.pillmate2.ui.navigation

import androidx.compose.foundation.layout.padding
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
import com.d4vram.pillmate2.ui.theme.ThemeManager
import com.d4vram.pillmate2.ui.security.SecurityManager
import com.d4vram.pillmate2.ui.security.rememberVoiceNoteSecurityManager
import com.d4vram.pillmate2.ui.voicenotes.VoiceNotesViewModel
import com.d4vram.pillmate2.ui.permissions.PermissionsManager
import com.d4vram.pillmate2.ui.components.PermissionsRequestDialog
import kotlinx.coroutines.launch

/**
 * 🧭 Sistema de navegación principal
 * Controla el flujo entre pantallas y el bottom navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PillMateNavigation(themeManager: ThemeManager, securityManager: SecurityManager) {
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
    val shouldShowPermissionsDialog by permissionsManager.shouldShowPermissionsDialog.collectAsState(initial = false)

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
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            screen.icon?.let {
                                Icon(
                                    imageVector = it,
                                    contentDescription = screen.label
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
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
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

            composable(Screen.VoiceNotes.route) {
                VoiceNotesScreen(viewModel = voiceNotesViewModel)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    themeManager = themeManager,
                    repository = repository,
                    securityManager = securityManager
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
                    repository = repository
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
}

private val bottomNavItems = listOf(
    Screen.Home,
    Screen.Medications,
    Screen.History,
    Screen.VoiceNotes,
    Screen.Settings
)