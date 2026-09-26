package com.resq.ui

import android.Manifest
import android.app.Application
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.resq.ui.home.HomeScreen
import com.resq.ui.messages.PacketHistoryScreen
import com.resq.ui.placeholder.ComingSoonScreen
import com.resq.ui.report.ReportScreen
import com.resq.ui.settings.SettingsScreen
import com.resq.ui.sos.SosConfirmationScreen
import com.resq.ui.theme.ResQTheme
import com.resq.ui.theme.ThemeMode

private object Routes {
    const val HOME = "home"
    const val REPORT = "report"
    const val SOS = "sos"
    const val MESSAGES = "messages"
    const val MAP = "map"
    const val SETTINGS = "settings"
}

private data class BottomDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun ResQApp() {
    val context = LocalContext.current
    val viewModel: ResQViewModel = viewModel(
        factory = ResQViewModel.factory(context.applicationContext as Application)
    )
    val location by viewModel.location.collectAsState()
    val packets by viewModel.packets.collectAsState()
    var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) viewModel.refreshLocation()
        else Toast.makeText(context, "Location permission was denied", Toast.LENGTH_LONG).show()
    }
    val getLocation = {
        if (viewModel.hasLocationPermission()) viewModel.refreshLocation()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val locationText = when {
        location.loading -> "Finding location…"
        location.fix != null -> "${"%.5f".format(location.fix!!.latitude)}, ${"%.5f".format(location.fix!!.longitude)}"
        location.error != null -> location.error!!
        else -> "Location not captured"
    }

    LaunchedEffect(Unit) {
        if (viewModel.hasLocationPermission()) viewModel.refreshLocation()
        viewModel.events.collect { event ->
            Toast.makeText(context, event.message, if (event.success) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }

    ResQTheme(themeMode) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val current = backStackEntry?.destination
        val bottomDestinations = listOf(
            BottomDestination(Routes.HOME, "Home", Icons.Default.Home),
            BottomDestination(Routes.MESSAGES, "Messages", Icons.Default.Message),
            BottomDestination(Routes.MAP, "Map", Icons.Default.Map),
            BottomDestination(Routes.SETTINGS, "Settings", Icons.Default.Settings)
        )
        val showBottomBar = current?.route in bottomDestinations.map { it.route }

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        bottomDestinations.forEach { item ->
                            NavigationBarItem(
                                selected = current?.hierarchy?.any { it.route == item.route } == true,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, item.label) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        deviceId = viewModel.deviceId,
                        storedPacketCount = packets.size,
                        locationText = locationText,
                        onGetLocation = getLocation,
                        onSos = { navController.navigate(Routes.SOS) },
                        onReport = { navController.navigate(Routes.REPORT) },
                        onMap = { navController.navigate(Routes.MAP) }
                    )
                }
                composable(Routes.REPORT) {
                    ReportScreen(
                        locationText = locationText,
                        hasLocation = location.fix != null,
                        onGetLocation = getLocation,
                        onBack = { navController.popBackStack() },
                        onContinue = {
                            viewModel.createReport(it)
                            navController.popBackStack()
                        }
                    )
                }
                composable(Routes.SOS) {
                    SosConfirmationScreen(
                        deviceId = viewModel.deviceId,
                        locationText = locationText,
                        hasLocation = location.fix != null,
                        onGetLocation = getLocation,
                        onBack = { navController.popBackStack() },
                        onConfirmed = {
                            viewModel.createSos()
                            navController.popBackStack()
                        }
                    )
                }
                composable(Routes.MESSAGES) { PacketHistoryScreen(packets) }
                composable(Routes.MAP) { ComingSoonScreen("Offline Map", "Milestone 7") }
                composable(Routes.SETTINGS) {
                    SettingsScreen(viewModel.deviceId, themeMode) { themeMode = it }
                }
            }
        }
    }
}
