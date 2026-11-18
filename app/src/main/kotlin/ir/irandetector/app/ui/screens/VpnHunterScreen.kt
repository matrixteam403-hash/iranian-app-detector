package ir.irandetector.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.irandetector.app.presentation.AppViewModel
import ir.irandetector.app.ui.AppIcons
import ir.irandetector.app.ui.PackageIconBadge
import ir.irandetector.app.ui.components.EmptyState
import ir.irandetector.app.ui.components.SectionCard
import ir.irandetector.app.ui.components.StatTile
import ir.irandetector.app.ui.theme.Dimens
import ir.irandetector.app.ui.theme.LocalSemanticColors
import ir.irandetector.app.vpn.VpnConfidence
import ir.irandetector.app.vpn.VpnFinding
import ir.irandetector.app.vpn.VpnMethod

/** VPN Hunter: a dedicated, explicitly separate scan of VPN / proxy clients. */
@Composable
fun VpnHunterScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    /** Lets the Activity own the removal request for a single VPN client. */
    onRemoveVpn: (packageName: String, label: String) -> Unit = { pkg, label ->
        viewModel.removeFromVpnHunter(pkg, label)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val report = state.vpnReport
    var query by remember { mutableStateOf("") }
    // Free-text filter over the findings, so "which of my 40 apps are VPNs" is one tap.
    val filtered = remember(report, query) {
        report?.findings?.filter { finding ->
            query.isBlank() ||
                finding.label.contains(query, ignoreCase = true) ||
                finding.packageName.contains(query, ignoreCase = true) ||
                finding.evidence.any { it.detail.contains(query, ignoreCase = true) }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("VPN Hunter", style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        state.vpnScanning -> "Scanning installed packages…"
                        report != null -> "${report.findings.size} client(s) · ${report.scannedPackages} packages checked"
                        else -> "Not scanned yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { viewModel.startVpnScan() },
                enabled = !state.vpnScanning,
            ) {
                Icon(AppIcons.Search, contentDescription = "Rescan")
            }
        }

        // Quick controls: the same preferences as Settings, one tap away, because a
        // VPN sweep is something you re-run and re-filter often.
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleChip(
                    label = "Possible",
                    checked = state.vpnShowPossible,
                    onCheckedChange = viewModel::setVpnShowPossible,
                    modifier = Modifier.weight(1f),
                )
                ToggleChip(
                    label = "System apps",
                    checked = state.vpnIncludeSystemApps,
                    onCheckedChange = viewModel::setVpnIncludeSystemApps,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { viewModel.startVpnScan() },
                    enabled = !state.vpnScanning,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) { Text(if (state.vpnScanning) "Scanning" else "Rescan") }
            }
        }

        if (state.vpnScanning) {
            Column(Modifier.fillMaxWidth().padding(Dimens.screenPadding)) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Checking package declarations, certificates of known clients, transport " +
                        "libraries and live system VPN state…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = Dimens.screenPadding,
                end = Dimens.screenPadding,
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.itemSpacing),
        ) {
            state.vpnError?.let { error ->
                item {
                    SectionCard {
                        Text("Scan failed", style = MaterialTheme.typography.titleMedium)
                        Text(error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (report != null && report.findings.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Filter by name, package or evidence") },
                        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(AppIcons.Clear, contentDescription = "Clear filter")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            }

            if (report != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.itemSpacing)) {
                        StatTile(
                            "Confirmed",
                            report.findings.count { it.confidence == VpnConfidence.CONFIRMED }.toString(),
                            Modifier.weight(1f),
                        )
                        StatTile(
                            "Likely",
                            report.findings.count { it.confidence == VpnConfidence.LIKELY }.toString(),
                            Modifier.weight(1f),
                        )
                        StatTile(
                            "Possible",
                            report.findings.count { it.confidence == VpnConfidence.POSSIBLE }.toString(),
                            Modifier.weight(1f),
                        )
                    }
                }

                item {
                    SectionCard {
                        Text("Detection methods", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        VpnMethod.entries.forEach { method ->
                            val count = report.methodSummary[method] ?: 0
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    if (count > 0) "•" else "○",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (count > 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${method.label} ($count)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (count > 0) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    Text(
                                        method.explanation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (report.findings.isEmpty()) {
                    item {
                        SectionCard {
                            EmptyState(
                                icon = AppIcons.Vpn,
                                title = "No VPN clients found",
                                description = "None of the ${report.scannedPackages} installed packages " +
                                    "matched the VPN signatures, declarations or system state checks. " +
                                    "A VPN app installed but never used can still be invisible to " +
                                    "system-level checks — see the notes below.",
                            )
                        }
                    }
                } else {
                    items(filtered.orEmpty(), key = { it.packageName }) { finding ->
                        val removing = state.uninstallPrompt?.packageName == finding.packageName
                        VpnFindingCard(
                            finding = finding,
                            onRemove = { onRemoveVpn(finding.packageName, finding.label) },
                            onOpenInQueue = { context.openAppInfo(finding.packageName) },
                            isRemoving = removing,
                        )
                    }
                    if (filtered.isNullOrEmpty()) {
                        item {
                            SectionCard {
                                Text(
                                    "No finding matches \"$query\".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (report.notes.isNotEmpty()) {
                    item {
                        SectionCard {
                            Text("What could not be inspected", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            report.notes.forEach { note ->
                                Text(
                                    "• $note",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            } else if (!state.vpnScanning) {
                item {
                    SectionCard {
                        EmptyState(
                            icon = AppIcons.Vpn,
                            title = "VPN Hunter is ready",
                            description = "Runs five independent checks: known client ids, VPN " +
                                "service declarations, system VPN accounting, routing profile " +
                                "(including Android's always-on VPN) and name/developer evidence.",
                            action = {
                                Button(
                                    onClick = { viewModel.startVpnScan() },
                                    shape = RoundedCornerShape(14.dp),
                                ) { Text("Run VPN scan") }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VpnFindingCard(
    finding: VpnFinding,
    onRemove: () -> Unit,
    onOpenInQueue: () -> Unit,
    isRemoving: Boolean,
) {
    val palette = LocalSemanticColors.current
    val color = when (finding.confidence) {
        VpnConfidence.CONFIRMED -> palette.verified
        VpnConfidence.LIKELY -> palette.probable
        VpnConfidence.POSSIBLE -> palette.uncertain
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cardRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PackageIconBadge(finding.packageName, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        finding.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        finding.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${finding.score}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    Text(
                        finding.confidence.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            finding.evidence.forEach { evidence ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(99.dp)),
                    )
                    Text(
                        "• ${evidence.detail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (finding.systemVpnInterface != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        AppIcons.Vpn,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Live VPN interface: ${finding.systemVpnInterface}",
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Per-app removal: every card gets its own button, so clients can be removed
            // one by one without building a queue first.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onOpenInQueue,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Icon(AppIcons.PhoneSettings, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("App info", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onRemove,
                    modifier = Modifier.weight(1.25f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isRemoving,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    if (isRemoving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(7.dp))
                        Text("Waiting…", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Remove", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Compact on/off pill used for the VPN Hunter quick filters. */
@Composable
private fun ToggleChip(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (checked) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            if (checked) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
        onClick = { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (checked) AppIcons.Check else AppIcons.Close,
                contentDescription = null,
                tint = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Opens the system app-info page for one package.
 *
 * Needed because a VPN client is sometimes protected (device admin / managed profile);
 * the info page is where the user can still see it, force-stop it or disable it.
 */
private fun android.content.Context.openAppInfo(packageName: String) {
    try {
        startActivity(
            android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$packageName"),
            ),
        )
    } catch (t: Throwable) {
        android.widget.Toast.makeText(this, "No app-info screen on this device", android.widget.Toast.LENGTH_SHORT).show()
    }
}
