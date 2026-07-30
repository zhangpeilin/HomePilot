package com.homepilot.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.homepilot.app.ui.screens.DashboardScreen
import com.homepilot.app.ui.screens.HomeScreen
import com.homepilot.app.ui.screens.ScenesScreen
import com.homepilot.app.ui.screens.ServerConfigScreen
import com.homepilot.app.viewmodel.DashboardViewModel
import com.homepilot.app.viewmodel.HomeViewModel
import com.homepilot.app.viewmodel.ScenesViewModel
import com.homepilot.app.viewmodel.ServerConfigViewModel

object Routes {
    const val HOME = "home"
    const val DASHBOARD = "dashboard"
    const val SERVER_CONFIG = "server_config"
    const val SCENES = "scenes"
}

@Composable
fun HomePilotNavGraph(
    navController: NavHostController,
    homeViewModel: HomeViewModel = viewModel(),
    serverConfigViewModel: ServerConfigViewModel = viewModel(),
    dashboardViewModel: DashboardViewModel = viewModel(),
    scenesViewModel: ScenesViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToConfig = {
                    navController.navigate(Routes.SERVER_CONFIG)
                }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToConfig = {
                    navController.navigate(Routes.SERVER_CONFIG)
                }
            )
        }

        composable(Routes.SERVER_CONFIG) {
            val groupingEnabled by homeViewModel.homeGroupingEnabled.collectAsState()
            ServerConfigScreen(
                viewModel = serverConfigViewModel,
                homeGroupingEnabled = groupingEnabled,
                onGroupingToggle = { enabled -> homeViewModel.setGroupingEnabled(enabled) },
                onConfigSaved = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SCENES) {
            ScenesScreen(
                viewModel = scenesViewModel
            )
        }
    }
}
