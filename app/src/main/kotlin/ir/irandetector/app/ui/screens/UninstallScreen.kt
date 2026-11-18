package ir.irandetector.app.ui.screens

import ir.irandetector.app.ui.AppIcons
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.irandetector.app.presentation.AppViewModel
import ir.irandetector.app.ui.PackageIconBadge
import ir.irandetector.app.ui.components.EmptyState
import ir.irandetector.app.ui.components.SectionCard
import ir.irandetector.app.ui.components.StatTile
import ir.irandetector.app.ui.theme.Dimens
import ir.irandetector.app.uninstall.UninstallManager

@Composable
fun UninstallScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val queue by viewModel.uninstallState.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Uninstall queue", style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        queue.total == 0 -> "No apps queued"
                        state.uninstallPrompt?.isWorking == true -> "Waiting for Android confirmation"
                        queue.finished -> "Finished · ${queue.completed.size} removed"
                        else -> "${queue.remaining} of ${queue.total} remaining · confirm one at a time"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (queue.total == 0) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = AppIcons.DoneAll,
                    title = "Queue is empty",
                    description = "Select applications on the results screen to build an uninstall queue.",
                )
            }
            return@Column
        }

        Column(Modifier.padding(horizontal = Dimens.screenPadding)) {
            Spacer(Modifier.height(8.dp))
            val animated by animateFloatAsState(queue.progressFraction, label = "queueProgress")
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.weight(1f).height(7.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "${(animated * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.itemSpacing)) {
                StatTile("Removed", queue.completed.size.toString(), Modifier.weight(1f))
                StatTile("Cancelled", queue.cancelled.size.toString(), Modifier.weight(1f))
                StatTile("Failed", queue.failed.size.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(queue.queue, key = { it.packageName }) { item ->
                val isCurrent = queue.current?.packageName == item.packageName && !queue.finished
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PackageIconBadge(item.packageName, size = 36.dp)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutcomeBadge(outcome = item.outcome, isCurrent = isCurrent)
                    }
                }
            }
            item { Spacer(Modifier.height(110.dp)) }
        }

        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 12.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(
                    horizontal = Dimens.screenPadding,
                    vertical = 12.dp,
                ),
            ) {
                if (queue.finished) {
                    SectionCard(contentPadding = PaddingValues(12.dp)) {
                        Column {
                            Text(
                                if (queue.failed.isEmpty() && queue.cancelled.isEmpty()) {
                                    "All selected applications were removed."
                                } else {
                                    "Finished — ${queue.completed.size} removed, " +
                                        "${queue.cancelled.size} cancelled, ${queue.failed.size} failed."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (queue.failed.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Failed applications are usually protected system apps or " +
                                        "managed by a device administrator.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (queue.cancelled.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Cancelled apps are still installed. Tap Done and select them " +
                                        "again to retry — you will get one confirmation per app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            viewModel.resetUninstallQueue()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Done") }
                } else {
                    // The primary action is the in-app confirmation dialog; this button
                    // re-opens it if the user closed it.
                    Button(
                        onClick = { viewModel.requestNextUninstall() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = state.uninstallPrompt == null && !queue.awaitingResult,
                    ) {
                        Text(
                            when {
                                state.uninstallPrompt != null -> "Confirmation is open"
                                queue.awaitingResult -> "Waiting for Android confirmation…"
                                else -> "Confirm next app"
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.cancelUninstallQueue() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Cancel remaining") }
                }
            }
        }
    }
}

@Composable
private fun OutcomeBadge(outcome: UninstallManager.Outcome?, isCurrent: Boolean) {
    val (color, icon, label) = when (outcome) {
        UninstallManager.Outcome.SUCCEEDED -> Triple(
            MaterialTheme.colorScheme.primary, AppIcons.Check, "Removed",
        )
        UninstallManager.Outcome.CANCELLED -> Triple(
            MaterialTheme.colorScheme.tertiary, AppIcons.Close, "Cancelled",
        )
        UninstallManager.Outcome.FAILED -> Triple(
            MaterialTheme.colorScheme.error, AppIcons.ErrorOutline, "Failed",
        )
        null -> if (isCurrent) {
            Triple(MaterialTheme.colorScheme.primary, AppIcons.HourglassEmpty, "Now")
        } else {
            Triple(MaterialTheme.colorScheme.onSurfaceVariant, AppIcons.HourglassEmpty, "Queued")
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isCurrent) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = color,
            )
        } else {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Suppress("unused")
private val Unused: Color? = null
