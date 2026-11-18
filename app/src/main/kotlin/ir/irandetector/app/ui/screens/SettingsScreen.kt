package ir.irandetector.app.ui.screens

import ir.irandetector.app.ui.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.irandetector.app.BuildConfig
import ir.irandetector.app.presentation.AppViewModel
import ir.irandetector.app.settings.ThemeMode
import ir.irandetector.app.ui.components.DetailRow
import ir.irandetector.app.ui.components.SectionCard
import ir.irandetector.app.ui.components.SettingsRow
import ir.irandetector.app.ui.components.StatTile
import ir.irandetector.app.ui.theme.Dimens
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenVpnHunter: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showUninstallHelpDialog by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = Dimens.screenPadding,
                end = Dimens.screenPadding,
                top = 8.dp,
                bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.itemSpacing),
        ) {
            item {
                SectionCard {
                    Column {
                        Text("Appearance", style = MaterialTheme.typography.titleMedium)
                        SettingsRow(
                            icon = AppIcons.Palette,
                            title = "Theme",
                            subtitle = state.themeMode.label,
                            onClick = { showThemeDialog = true },
                        )
                    }
                }
            }

            item {
                SectionCard {
                    Column {
                        Text("Scan preferences", style = MaterialTheme.typography.titleMedium)
                        SettingsRow(
                            icon = AppIcons.Tune,
                            title = "Include system apps",
                            subtitle = "System applications are analysed too",
                            trailing = {
                                Switch(
                                    checked = state.includeSystemApps,
                                    onCheckedChange = viewModel::setIncludeSystemApps,
                                )
                            },
                        )
                        SettingsRow(
                            icon = AppIcons.Tune,
                            title = "Hide excluded apps",
                            subtitle = "Keep excluded packages out of results",
                            trailing = {
                                Switch(
                                    checked = state.hideExcluded,
                                    onCheckedChange = viewModel::setHideExcluded,
                                )
                            },
                        )
                        Column(Modifier.padding(top = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Minimum confidence",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${state.minimumConfidence}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Slider(
                                value = state.minimumConfidence.toFloat(),
                                onValueChange = { viewModel.setMinimumConfidence(it.roundToInt()) },
                                valueRange = 10f..80f,
                                steps = 13,
                            )
                            Text(
                                "Lower values show more uncertain matches. The default (30%) " +
                                    "already requires at least one real signal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                SectionCard {
                    Column {
                        Text("VPN Hunter", style = MaterialTheme.typography.titleMedium)
                        SettingsRow(
                            icon = AppIcons.Vpn,
                            title = "Enable VPN Hunter",
                            subtitle = "Find every VPN / proxy client with five independent methods",
                            trailing = {
                                Switch(
                                    checked = state.vpnHunterEnabled,
                                    onCheckedChange = viewModel::setVpnHunterEnabled,
                                )
                            },
                        )
                        if (state.vpnHunterEnabled) {
                            SettingsRow(
                                icon = AppIcons.Shield,
                                title = "Show possible matches",
                                subtitle = "Include apps with a single weak signal",
                                trailing = {
                                    Switch(
                                        checked = state.vpnShowPossible,
                                        onCheckedChange = viewModel::setVpnShowPossible,
                                    )
                                },
                            )
                            SettingsRow(
                                icon = AppIcons.List,
                                title = "Scan system apps",
                                subtitle = "Include pre-installed VPN components",
                                trailing = {
                                    Switch(
                                        checked = state.vpnIncludeSystemApps,
                                        onCheckedChange = viewModel::setVpnIncludeSystemApps,
                                    )
                                },
                            )
                            Text(
                                if (state.vpnScanning) {
                                    "Scanning…"
                                } else {
                                    state.vpnReport?.let { report ->
                                        "${report.findings.size} client(s) found in ${report.scannedPackages} packages"
                                    } ?: "Enabled — run the scan from here"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = onOpenVpnHunter,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("Open VPN Hunter") }
                        }
                        Text(
                            "VPN Hunter inspects VPN service declarations, transport engines, known " +
                                "client ids and live system VPN state. Some results are inferred: Android " +
                                "does not reveal which app owns the active VPN, so findings always show " +
                                "the evidence behind them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            item {
                SectionCard {
                    Column {
                        Text("Uninstall safety", style = MaterialTheme.typography.titleMedium)
                        SettingsRow(
                            icon = AppIcons.DeleteSweep,
                            title = "Confirm every app in-app",
                            subtitle = "A dialog appears here before Android asks — always on",
                            trailing = {
                                Switch(checked = true, onCheckedChange = {}, enabled = false)
                            },
                        )
                        Column(Modifier.padding(top = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Pause between requests",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${state.uninstallDelayMs} ms",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Slider(
                                value = state.uninstallDelayMs.toFloat(),
                                onValueChange = { viewModel.setUninstallDelayMs(it.roundToInt()) },
                                valueRange = 150f..1200f,
                                steps = 6,
                            )
                            Text(
                                "Some OEM builds need a moment between uninstall dialogs. Raise this " +
                                    "if a request is ever skipped or the system screen does not appear.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SettingsRow(
                            icon = AppIcons.PhoneSettings,
                            title = "Uninstall help",
                            subtitle = "What to do if no confirmation appears",
                            onClick = { showUninstallHelpDialog = true },
                        )
                        Text(
                            "Each app is requested separately: confirm here, accept Android's own " +
                                "dialog, and the next app appears. If the system dialog never shows " +
                                "up, the app falls back to the app-settings screen where Uninstall " +
                                "is always available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            item {
                SectionCard {
                    Column {
                        Text("Install aid", style = MaterialTheme.typography.titleMedium)
                        SettingsRow(
                            icon = AppIcons.OpenInNew,
                            title = "APK / package installer help",
                            subtitle = "When the browser offers Termux instead of Package installer",
                            onClick = { viewModel.openInstallHelp() },
                        )
                        Text(
                            "Android only lets a browser hand an APK to the package installer when " +
                                "\"Install unknown apps\" is allowed for that browser. The guide can " +
                                "open both settings screens for you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            item {
                SectionCard {
                    Column {
                        Text("Detection database", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        DetailRow("Records", state.stats.databaseRecords.toString())
                        DetailRow(
                            label = "Updated",
                            value = if (state.stats.databaseUpdatedAt > 0) {
                                formatDate(state.stats.databaseUpdatedAt)
                            } else {
                                "bundled"
                            },
                        )
                        DetailRow(
                            label = "Source",
                            value = if (state.databaseUrl != null) "remote + bundled" else "bundled only",
                        )
                        Spacer(Modifier.height(8.dp))
                        SettingsRow(
                            icon = AppIcons.CloudDownload,
                            title = "Update source URL",
                            subtitle = state.databaseUrl ?: "Not configured",
                            onClick = { showUrlDialog = true },
                        )
                        if (updating) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Downloading…", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Button(
                                onClick = {
                                    updating = true
                                    updateStatus = null
                                    viewModel.updateDatabase { message ->
                                        updating = false
                                        updateStatus = message
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("Check for database update") }
                        }
                        updateStatus?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Database updates are optional. The app is fully functional offline " +
                                "and never uploads your installed-app list.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SectionCard {
                    Column {
                        Text("Privacy & safety", style = MaterialTheme.typography.titleMedium)
                        SettingsRow(
                            icon = AppIcons.Lock,
                            title = "Privacy information",
                            subtitle = "What this app can and cannot do",
                            onClick = { showPrivacyDialog = true },
                        )
                        SettingsRow(
                            icon = AppIcons.Info,
                            title = "About",
                            subtitle = "Version ${BuildConfig.VERSION_NAME}",
                            onClick = { showAboutDialog = true },
                        )
                    }
                }
            }
        }
    }

    if (showUninstallHelpDialog) {
        AlertDialog(
            onDismissRequest = { showUninstallHelpDialog = false },
            icon = { Icon(AppIcons.PhoneSettings, contentDescription = null) },
            title = { Text("If no confirmation appears") },
            text = {
                Column {
                    Text(
                        "Every app is requested separately. This is the normal flow:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "1. Tap Uninstall in the dialog this app shows.",
                        "2. Android shows its own confirmation — tap OK / Uninstall.",
                        "3. The next app appears automatically.",
                    ).forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "If step 2 never appears, the app opens that app's settings page instead — " +
                            "use the Uninstall button there. Some launchers also hide the system " +
                            "uninstaller behind a device-administrator or \"managed profile\" policy; " +
                            "in that case Android itself blocks removal.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showUninstallHelpDialog = false }) { Text("Close") }
            },
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.themeMode == mode,
                                onClick = {
                                    viewModel.setTheme(mode)
                                    showThemeDialog = false
                                },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(mode.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
            },
        )
    }

    if (showUrlDialog) {
        var url by remember { mutableStateOf(state.databaseUrl.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Database update URL") },
            text = {
                Column {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = { Text("https://example.com/detection_db.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Point this at a JSON file using the same schema as the bundled " +
                            "database. Leave empty to disable remote updates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDatabaseUrl(url)
                    showUrlDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setDatabaseUrl(null)
                    showUrlDialog = false
                }) { Text("Clear") }
            },
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text(
                            "This app reads the list of installed packages, their labels, " +
                                "versions, installer package and signing certificate digests. " +
                                "All of that stays on your device.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    item {
                        Text(
                            "No analytics, no advertising SDK, no crash reporting service and no " +
                                "account. The only network request the app can make is the optional database " +
                                "update you trigger yourself, which sends nothing but a plain GET.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    item {
                        Text(
                            "Uninstalling always goes through the official Android confirmation " +
                                "dialog. The app cannot remove anything silently, cannot use root, " +
                                "and does not request device-admin rights or accessibility access.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("QUERY_ALL_PACKAGES is requested to enumerate apps.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Close") }
            },
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Iranian App Detector") },
            text = {
                Column {
                    Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "A lightweight, offline-first tool that identifies applications published by " +
                            "Iranian companies and helps you remove the ones you do not want.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Detection methods", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "Package identifiers · signing certificates · developer identity · " +
                            "Persian/English names and aliases · fuzzy matching · installation source · " +
                            "developer domains · icon fingerprinting (when verified hashes exist) · " +
                            "weighted confidence scoring.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Detection is probabilistic. A missing detection means the app is not in the " +
                            "database, not that it is safe.",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.SansSerif),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("Close") } },
        )
    }
}
