package ir.irandetector.app.ui.screens

import ir.irandetector.app.ui.AppIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.irandetector.app.domain.ConfidenceTier
import ir.irandetector.app.domain.DetectionResult
import ir.irandetector.app.domain.SignalType
import ir.irandetector.app.presentation.AppViewModel
import ir.irandetector.app.ui.PackageIconBadge
import ir.irandetector.app.settings.SortMode
import ir.irandetector.app.ui.components.ConfidenceBar
import ir.irandetector.app.ui.components.DetailRow
import ir.irandetector.app.ui.components.EmptyState
import ir.irandetector.app.ui.components.SectionCard
import ir.irandetector.app.ui.components.TierPill
import ir.irandetector.app.ui.theme.Dimens
import ir.irandetector.app.ui.theme.Emerald
import ir.irandetector.app.ui.theme.LocalSemanticColors

@Composable
fun ResultsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenInstallHelp: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var detailPackage by remember { mutableStateOf<String?>(null) }
    var confirmUninstall by remember { mutableStateOf(false) }

    val visible = state.visibleResults
    val detail = state.results.firstOrNull { it.app.packageName == detailPackage }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // ---------------------------------------------------------- top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Detected applications", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${state.results.size} matches · ${state.selected.size} selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.selected.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearSelection() }) { Text("Clear") }
            }
            IconButton(onClick = onOpenInstallHelp) {
                Icon(AppIcons.Help, contentDescription = "Install help")
            }
        }

        // -------------------------------------------------- search + filters
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding),
            placeholder = { Text("Search name, package or developer") },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(AppIcons.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            textStyle = MaterialTheme.typography.bodyMedium,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConfidenceTier.entries.filter { it != ConfidenceTier.NONE }.forEach { tier ->
                val selected = tier in state.tierFilter
                val palette = LocalSemanticColors.current
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.toggleTier(tier) },
                    label = { Text(tier.label, style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.forTier(tier).copy(alpha = 0.18f),
                        selectedLabelColor = palette.forTier(tier),
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = palette.forTier(tier).copy(alpha = 0.6f),
                    ),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sort", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            SortMode.entries.forEach { mode ->
                TextButton(
                    onClick = { viewModel.setSort(mode) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        mode.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.sortMode == mode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (state.sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    if (state.allVisibleSelected) viewModel.clearSelection() else viewModel.selectAllVisible()
                },
            ) {
                Text(if (state.allVisibleSelected) "Deselect all" else "Select all")
            }
        }

        // ------------------------------------------------------------ list
        if (visible.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = AppIcons.Search,
                    title = if (state.results.isEmpty()) "No detections" else "Nothing matches the filters",
                    description = if (state.results.isEmpty()) {
                        "The scan found no applications that match the Iranian-app database."
                    } else {
                        "Try clearing the search box or enabling more confidence tiers."
                    },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Dimens.screenPadding,
                    end = Dimens.screenPadding,
                    top = 6.dp,
                    bottom = 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.itemSpacing),
            ) {
                items(
                    items = visible,
                    key = { it.app.packageName },
                    contentType = { "detection" },
                ) { result ->
                    DetectionCard(
                        result = result,
                        selected = result.app.packageName in state.selected,
                        excluded = result.app.packageName in state.exclusions,
                        onToggle = { viewModel.toggleSelection(result.app.packageName) },
                        onDetails = { detailPackage = result.app.packageName },
                        onExclude = { viewModel.exclude(result.app.packageName) },
                    )
                }
            }
        }

        // ------------------------------------------------- bottom action bar
        AnimatedVisibility(
            visible = state.selected.isNotEmpty(),
            enter = fadeIn(tween(150)) + expandVertically(tween(180)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
        ) {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Dimens.screenPadding, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${state.selected.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "System confirmation for each app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { confirmUninstall = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(AppIcons.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Uninstall", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    detail?.let { result ->
        DetectionDetailSheet(
            result = result,
            excluded = result.app.packageName in state.exclusions,
            onDismiss = { detailPackage = null },
            onExclude = {
                viewModel.exclude(result.app.packageName)
                detailPackage = null
            },
            onUnexclude = {
                viewModel.unexclude(result.app.packageName)
                detailPackage = null
            },
        )
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            icon = { Icon(AppIcons.Security, contentDescription = null) },
            title = { Text("Uninstall ${state.selected.size} application" + if (state.selected.size == 1) "" else "s" + "?") },
            text = {
                Text(
                    "You will confirm each application yourself, one at a time — a dialog " +
                        "appears here in the app, then Android asks for the final confirmation. " +
                        "Nothing is removed without your approval.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    viewModel.beginUninstall()
                    onOpenQueue()
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetectionCard(
    result: DetectionResult,
    selected: Boolean,
    excluded: Boolean,
    onToggle: () -> Unit,
    onDetails: () -> Unit,
    onExclude: () -> Unit,
) {
    val palette = LocalSemanticColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cardRadius),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = { onToggle() })
                PackageIconBadge(result.app.packageName, size = 44.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        result.app.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        result.matchedRecord?.developer ?: result.app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${result.confidence}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.forTier(result.tier),
                    )
                    TierPill(tier = result.tier, compact = true)
                }
            }

            Spacer(Modifier.height(10.dp))
            ConfidenceBar(confidence = result.confidence, tier = result.tier)
            Spacer(Modifier.height(9.dp))

            Text(
                result.reasons.firstOrNull() ?: "Matched the Iranian application database",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    result.app.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (excluded) {
                    Text(
                        "Excluded",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDetails, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Details", style = MaterialTheme.typography.labelMedium)
                }
                if (!excluded) {
                    TextButton(onClick = onExclude, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(
                            "Keep",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Full-screen details dialog explaining every signal behind a detection. */
@Composable
private fun DetectionDetailSheet(
    result: DetectionResult,
    excluded: Boolean,
    onDismiss: () -> Unit,
    onExclude: () -> Unit,
    onUnexclude: () -> Unit,
) {
    val palette = LocalSemanticColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PackageIconBadge(result.app.packageName, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(result.app.label, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Text(
                        "${result.confidence}% confidence",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.forTier(result.tier),
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SectionCard(contentPadding = PaddingValues(12.dp)) {
                        Column {
                            DetailRow("Package", result.app.packageName, monospace = true)
                            DetailRow("Version", result.app.versionName ?: "unknown")
                            DetailRow("Installer", result.app.installerPackage ?: "unknown (sideloaded)")
                            DetailRow("System app", if (result.app.isSystemApp) "yes" else "no")
                            DetailRow(
                                "Database record",
                                result.matchedRecord?.let { "${it.nameEn} · ${it.verification.name}" } ?: "—",
                            )
                            result.matchedRecord?.website?.let { DetailRow("Website", it) }
                        }
                    }
                }
                item {
                    Text("Why it was flagged", style = MaterialTheme.typography.titleMedium)
                }
                items(result.signals.sortedByDescending { it.points }.size) { index ->
                    val signal = result.signals.sortedByDescending { it.points }[index]
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    signal.type.weightLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "+${signal.points}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(signal.explanation, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Text(
                        "Signals are evidence, not proof. Only the official Android uninstall dialog " +
                            "can remove an application, and it always asks you first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = { if (excluded) onUnexclude() else onExclude() }) {
                Text(if (excluded) "Remove exclusion" else "Keep this app")
            }
        },
    )
}
