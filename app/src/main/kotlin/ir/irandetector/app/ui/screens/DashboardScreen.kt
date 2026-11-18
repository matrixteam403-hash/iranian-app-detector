package ir.irandetector.app.ui.screens

import ir.irandetector.app.ui.AppIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.irandetector.app.domain.ConfidenceTier
import ir.irandetector.app.presentation.AppViewModel
import ir.irandetector.app.ui.PackageIconBadge
import ir.irandetector.app.ui.components.ConfidenceBar
import ir.irandetector.app.ui.components.EmptyState
import ir.irandetector.app.ui.components.SectionCard
import ir.irandetector.app.ui.components.StatTile
import ir.irandetector.app.ui.components.TierPill
import ir.irandetector.app.ui.theme.Dimens
import ir.irandetector.app.ui.theme.LocalSemanticColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onScan: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalSemanticColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.itemSpacing),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Iranian App Detector",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = if (state.hasScanned) {
                            "Last scan ${formatTimestamp(state.lastScanAt)}"
                        } else {
                            "Offline detection · no data leaves your device"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(AppIcons.Settings, contentDescription = "Settings")
                }
            }
        }

        item { HeroCard(hasScanned = state.hasScanned, detected = state.stats.detectedCount, onScan = onScan) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.itemSpacing)) {
                StatTile(
                    label = "Installed apps",
                    value = if (state.hasScanned) state.stats.installedCount.toString() else "—",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Detected",
                    value = if (state.hasScanned) state.stats.detectedCount.toString() else "—",
                    modifier = Modifier.weight(1f),
                    emphasised = true,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.itemSpacing)) {
                StatTile(
                    label = "Verified",
                    value = if (state.hasScanned) state.stats.verifiedCount.toString() else "—",
                    modifier = Modifier.weight(1f),
                    accent = palette.verified,
                )
                StatTile(
                    label = "Probable",
                    value = if (state.hasScanned) state.stats.probableCount.toString() else "—",
                    modifier = Modifier.weight(1f),
                    accent = palette.probable,
                )
                StatTile(
                    label = "Uncertain",
                    value = if (state.hasScanned) state.stats.uncertainCount.toString() else "—",
                    modifier = Modifier.weight(1f),
                    accent = palette.uncertain,
                )
            }
        }

        if (!state.hasScanned) {
            item {
                SectionCard {
                    EmptyState(
                        icon = AppIcons.Search,
                        title = "No scan yet",
                        description = "Run a scan to see which installed applications are developed by " +
                            "Iranian companies. Every result explains exactly why it was flagged.",
                    )
                }
            }
        } else if (state.results.isEmpty()) {
            item {
                SectionCard {
                    EmptyState(
                        icon = AppIcons.Search,
                        title = "No Iranian applications found",
                        description = "None of the ${state.stats.installedCount} installed applications matched the " +
                            "detection database. That is a valid result — nothing is flagged speculatively.",
                    )
                }
            }
        } else {
            item {
                SectionCard(onClick = onOpenResults) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Detected applications",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "${state.results.size} applications match the database",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(AppIcons.ChevronRight, contentDescription = null)
                    }
                    Spacer(Modifier.height(14.dp))
                    state.results
                        .sortedByDescending { it.confidence }
                        .take(3)
                        .forEach { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Dimens.smallRadius))
                                    .clickable(onClick = onOpenResults)
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PackageIconBadge(result.app.packageName, size = 34.dp)
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        result.app.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                    )
                                    Text(
                                        result.app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "${result.confidence}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.forTier(result.tier),
                                )
                            }
                        }
                }
            }
        }

        item {
            SectionCard {
                Text("Detection database", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.stats.databaseRecords} records",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    TierPill(tier = ConfidenceTier.VERIFIED, compact = true)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.stats.databaseUpdatedAt > 0) {
                        "Database dated ${formatDate(state.stats.databaseUpdatedAt)} · works fully offline"
                    } else {
                        "Bundled database · works fully offline"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun HeroCard(hasScanned: Boolean, detected: Int, onScan: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "pulseScale",
    )
    SectionCard {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .scale(if (hasScanned) 1f else pulse)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        if (hasScanned) "Rescan your device" else "Ready to scan",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        if (hasScanned && detected > 0) {
                            "$detected Iranian applications detected"
                        } else {
                            "Package, certificate, developer & name analysis"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onScan,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    if (hasScanned) "Scan again" else "Scan installed applications",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            AnimatedVisibility(visible = !hasScanned) {
                Text(
                    "Scans run entirely on-device. Nothing is uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

internal fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return "never"
    return SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
}

internal fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "unknown"
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
}
