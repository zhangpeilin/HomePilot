package com.homepilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.homepilot.app.navigation.HomePilotNavGraph
import com.homepilot.app.navigation.Routes
import com.homepilot.app.ui.theme.HomePilotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomePilotTheme {
                HomePilotMainScreen()
            }
        }
    }
}

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
) {
    data object Home : BottomNavItem(
        Routes.HOME, "首页", { Icon(Icons.Default.Home, contentDescription = "首页") }
    )
    data object Dashboard : BottomNavItem(
        Routes.DASHBOARD, "设备", { Icon(Icons.Default.PowerSettingsNew, contentDescription = "设备") }
    )
    data object Scenes : BottomNavItem(
        Routes.SCENES, "场景", { Icon(Icons.Default.PlaylistPlay, contentDescription = "场景") }
    )
    data object Settings : BottomNavItem(
        Routes.SERVER_CONFIG, "设置", { Icon(Icons.Default.Settings, contentDescription = "设置") }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePilotMainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Dashboard,
        BottomNavItem.Scenes,
        BottomNavItem.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { item.icon() },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HomePilotNavGraph(navController = navController)
        }
    }
}
