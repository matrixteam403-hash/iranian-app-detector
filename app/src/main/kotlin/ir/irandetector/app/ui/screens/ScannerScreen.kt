package ir.irandetector.app.ui.screens

import ir.irandetector.app.ui.AppIcons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.irandetector.app.domain.ScanProgress
import ir.irandetector.app.presentation.AppViewModel
import ir.irandetector.app.ui.components.SectionCard
import ir.irandetector.app.ui.theme.Dimens
import kotlinx.coroutines.delay

@Composable
fun ScannerScreen(
    viewModel: AppViewModel,
    onFinished: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Kick the scan off exactly once when this screen appears.
    LaunchedEffect(Unit) { viewModel.startScan() }

    // Navigate on completion (success, cancellation or error) without blocking.
    LaunchedEffect(state.isScanning, state.hasScanned, state.scanPhase) {
        if (!state.isScanning && state.hasScanned) {
            delay(260)
            onFinished()
        }
    }

    val phase = state.scanPhase
    val total = (phase as? ScanProgress.Analysing)?.total ?: 0
    val index = (phase as? ScanProgress.Analysing)?.index ?: 0
    val discovered = (phase as? ScanProgress.Discovering)?.found ?: 0
    val fraction = if (total > 0) index.toFloat() / total else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Dimens.screenPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Scanning", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.cancelScan(); onBack() }) {
                Icon(AppIcons.Close, contentDescription = "Cancel scan")
            }
        }

        Spacer(Modifier.height(30.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ScanDial(fraction = fraction, label = "${(fraction * 100).toInt()}%")
        }

        Spacer(Modifier.height(34.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when (phase) {
                    is ScanProgress.Discovering -> "Reading installed packages…"
                    is ScanProgress.Analysing -> "Analysing ${phase.label}"
                    ScanProgress.Cancelled -> "Scan cancelled"
                    is ScanProgress.Failed -> "Scan failed"
                    is ScanProgress.Finished -> "Scan complete"
                    null -> "Preparing…"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (phase) {
                    is ScanProgress.Discovering -> "$discovered packages discovered"
                    is ScanProgress.Analysing -> "$index of ${phase.total} packages evaluated"
                    else -> "Detection runs locally on your device"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(28.dp))
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                ScanStageRow(
                    icon = AppIcons.Search,
                    title = "Package identifiers",
                    detail = "Exact & historical ids",
                    done = phase is ScanProgress.Analysing || phase is ScanProgress.Finished,
                )
                ScanStageRow(
                    icon = AppIcons.Search,
                    title = "Certificates & developer identity",
                    detail = "SHA-256 / SHA-1, publisher names",
                    done = phase is ScanProgress.Analysing || phase is ScanProgress.Finished,
                )
                ScanStageRow(
                    icon = AppIcons.Search,
                    title = "Names, aliases & fuzzy matching",
                    detail = "Persian and English labels",
                    done = phase is ScanProgress.Finished,
                )
                ScanStageRow(
                    icon = AppIcons.Search,
                    title = "Install sources & confidence",
                    detail = "Iranian marketplaces, scoring",
                    done = phase is ScanProgress.Finished,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = { viewModel.cancelScan(); onBack() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
        ) {
            Text("Cancel scan")
        }
    }
}

@Composable
private fun ScanStageRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    done: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (done) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(11.dp))
            }
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Animated progress dial. A single Canvas keeps the cost trivial on old devices. */
@Composable
private fun ScanDial(fraction: Float, label: String) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "scanDial",
    )
    Box(contentAlignment = Alignment.Center) {
        val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        val accent = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.size(190.dp)) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(accent.copy(alpha = 0.45f), accent)),
                startAngle = 135f,
                sweepAngle = 270f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "complete",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
