package ph.edu.asiatech.goananas.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ph.edu.asiatech.goananas.ui.screens.CaptureScreen
import ph.edu.asiatech.goananas.ui.screens.DashboardScreen
import ph.edu.asiatech.goananas.ui.screens.GuideDetailScreen
import ph.edu.asiatech.goananas.ui.screens.GuideScreen
import ph.edu.asiatech.goananas.ui.screens.HistoryScreen
import ph.edu.asiatech.goananas.ui.screens.HomeScreen
import ph.edu.asiatech.goananas.ui.screens.OnboardingScreen
import ph.edu.asiatech.goananas.ui.screens.ResultScreen
import ph.edu.asiatech.goananas.ui.screens.ScanDetailScreen
import ph.edu.asiatech.goananas.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val CAPTURE = "capture"
    const val RESULT = "result"
    const val HISTORY = "history"
    const val SCAN = "scan/{id}"
    const val DASHBOARD = "dashboard"
    const val GUIDE = "guide"
    const val GUIDE_DETAIL = "guide/{label}"
    const val SETTINGS = "settings"
    fun scan(id: Long) = "scan/$id"
    fun guide(label: String) = "guide/$label"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.HISTORY, "History", Icons.Filled.History),
    Tab(Routes.DASHBOARD, "Dashboard", Icons.Filled.BarChart),
    Tab(Routes.GUIDE, "Guide", Icons.AutoMirrored.Filled.MenuBook),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun GoAnanasRoot(openScanId: Long?, onOpenedScan: () -> Unit, vm: AppViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings
    when {
        s == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        !s.onboarded -> OnboardingScreen(vm)
        else -> MainNav(vm, openScanId, onOpenedScan)
    }
}

@Composable
private fun MainNav(vm: AppViewModel, openScanId: Long?, onOpenedScan: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(openScanId) {
        if (openScanId != null) { nav.navigate(Routes.scan(openScanId)); onOpenedScan() }
    }

    Scaffold(
        bottomBar = {
            if (route in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.goTab(t.route) },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(pad)) {
            composable(Routes.HOME) {
                HomeScreen(vm, onScan = { nav.navigate(Routes.CAPTURE) }, onOpenScan = { nav.navigate(Routes.scan(it)) },
                    onSeeHistory = { nav.goTab(Routes.HISTORY) })
            }
            composable(Routes.CAPTURE) {
                CaptureScreen(vm, onBack = { nav.popBackStack() }, onAnalyzed = { nav.navigate(Routes.RESULT) })
            }
            composable(Routes.RESULT) {
                ResultScreen(vm, onBack = { vm.resetAnalysis(); nav.popBackStack() },
                    onSaved = { id -> nav.popBackStack(Routes.HOME, inclusive = false); nav.navigate(Routes.scan(id)) },
                    onGuide = { nav.navigate(Routes.guide(it)) })
            }
            composable(Routes.HISTORY) { HistoryScreen(vm, onOpenScan = { nav.navigate(Routes.scan(it)) }) }
            composable(Routes.SCAN, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                ScanDetailScreen(vm, e.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() }, onGuide = { nav.navigate(Routes.guide(it)) })
            }
            composable(Routes.DASHBOARD) { DashboardScreen(vm, onOpenScan = { nav.navigate(Routes.scan(it)) }) }
            composable(Routes.GUIDE) { GuideScreen(onOpen = { nav.navigate(Routes.guide(it)) }) }
            composable(Routes.GUIDE_DETAIL, arguments = listOf(navArgument("label") { type = NavType.StringType })) { e ->
                GuideDetailScreen(e.arguments?.getString("label") ?: "", onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) { SettingsScreen(vm) }
        }
    }
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
