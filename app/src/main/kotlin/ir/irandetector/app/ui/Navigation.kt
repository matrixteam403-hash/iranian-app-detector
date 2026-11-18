package ir.irandetector.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ir.irandetector.app.presentation.AppViewModel
import ir.irandetector.app.ui.dialogs.UninstallConfirmDialog
import ir.irandetector.app.ui.dialogs.InstallHelpDialog
import ir.irandetector.app.ui.screens.DashboardScreen
import ir.irandetector.app.ui.screens.ResultsScreen
import ir.irandetector.app.ui.screens.ScannerScreen
import ir.irandetector.app.ui.screens.SettingsScreen
import ir.irandetector.app.ui.screens.UninstallScreen
import ir.irandetector.app.ui.screens.VpnHunterScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val SCANNER = "scanner"
    const val RESULTS = "results"
    const val UNINSTALL = "uninstall"
    const val SETTINGS = "settings"
    const val VPN_HUNTER = "vpn_hunter"
}

@Composable
fun AppNavHost(
    viewModel: AppViewModel,
    navController: NavHostController = rememberNavController(),
    startScanFromDashboard: Boolean = false,
) {
    // The confirmation lives above the NavHost so it survives navigation and can
    // never be skipped: one dialog per queued app, in queue order.
    val state by viewModel.state.collectAsStateWithLifecycle()
    state.uninstallPrompt?.let { prompt ->
        UninstallConfirmDialog(
            packageName = prompt.packageName,
            label = prompt.label,
            positionInQueue = prompt.position,
            queueTotal = prompt.total,
            systemDialogUnavailable = prompt.systemDialogUnavailable,
            isSingleVpnRemoval = prompt.origin == AppViewModel.UninstallOrigin.VPN_SINGLE,
            isWorking = prompt.isWorking,
            onConfirm = viewModel::confirmUninstall,
            onSkip = viewModel::skipUninstall,
            onCancelQueue = viewModel::cancelUninstallQueue,
        )
    }
    if (state.showInstallHelp) {
        InstallHelpDialog(
            packageName = null,
            onDismiss = viewModel::dismissInstallHelp,
        )
    }

    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        enterTransition = { fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 12 } },
        exitTransition = { fadeOut(tween(140)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(140)) + slideOutHorizontally(tween(200)) { it / 12 } },
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = viewModel,
                onScan = { navController.navigate(Routes.SCANNER) },
                onOpenResults = { navController.navigate(Routes.RESULTS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SCANNER) {
            ScannerScreen(
                viewModel = viewModel,
                onFinished = {
                    navController.navigate(Routes.RESULTS) {
                        popUpTo(Routes.DASHBOARD)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.RESULTS) {
            ResultsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenQueue = { navController.navigate(Routes.UNINSTALL) },
                onOpenInstallHelp = viewModel::openInstallHelp,
            )
        }
        composable(Routes.UNINSTALL) {
            UninstallScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenVpnHunter = { navController.navigate(Routes.VPN_HUNTER) },
            )
        }
        composable(Routes.VPN_HUNTER) {
            VpnHunterScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
