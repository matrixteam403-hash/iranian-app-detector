package ir.irandetector.app.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.irandetector.app.ui.AppIcons
import ir.irandetector.app.ui.PackageIconBadge
import ir.irandetector.app.ui.theme.Dimens
import ir.irandetector.app.ui.theme.Emerald
import kotlinx.coroutines.delay

/**
 * The in-app confirmation that used to come from the OEM package installer.
 *
 * Why this exists: several Android builds (and some OEM skins) never show the
 * system confirmation for `ACTION_DELETE` / `ACTION_UNINSTALL_PACKAGE`, return no
 * result, or briefly show it and close it immediately. The user reported exactly
 * that — "درخواست تایید نیامد". This dialog asks for **one** app at a time, always
 * waits for an explicit tap, and is the place where the user chooses how the app
 * should be removed.
 */
@Composable
fun UninstallConfirmDialog(
    packageName: String,
    label: String,
    positionInQueue: Int,
    queueTotal: Int,
    /** True when a previous request came back without any dialog / result. */
    systemDialogUnavailable: Boolean,
    /** True for a single VPN-client removal started from the VPN Hunter list. */
    isSingleVpnRemoval: Boolean = false,
    isWorking: Boolean,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onCancelQueue: () -> Unit,
) {
    // A short "hold" countdown prevents accidental double taps from cascading into
    // several system requests at once on slow OEM builds.
    var countdown by remember(packageName) { mutableIntStateOf(2) }
    LaunchedEffect(packageName) {
        while (countdown > 0) {
            delay(400)
            countdown -= 1
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (countdown > 0) 1f - (countdown / 2f) else 1f,
        label = "confirmProgress",
    )

    Dialog(
        onDismissRequest = { /* never dismiss silently: the queue must stay in control */ },
        properties = DialogProperties(
            dismissOnBackPress = !isWorking,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = true,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Emerald.copy(alpha = 0.28f)),
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Emerald.copy(alpha = 0.22f), Emerald.copy(alpha = 0.06f)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            AppIcons.DeleteSweep,
                            contentDescription = null,
                            tint = Emerald,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Remove this app?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            when {
                                isSingleVpnRemoval -> "VPN client · removes only this app"
                                queueTotal > 1 -> "Request $positionInQueue of $queueTotal · one at a time"
                                else -> "One request · you stay in control"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PackageIconBadge(packageName, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    if (isSingleVpnRemoval) {
                        "Android will ask you to confirm the removal of this VPN client. " +
                            "Every other app on your phone stays untouched."
                    } else {
                        "Android will ask you to confirm the removal. Tap Uninstall there and the " +
                            "next app in the queue appears here."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                AnimatedVisibility(
                    visible = systemDialogUnavailable,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                            ),
                        ) {
                            Text(
                                "The system uninstaller did not respond last time. Use " +
                                    "\"Open app settings\" below and press Uninstall there — same " +
                                    "result, always available.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(11.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Progress bar doubles as the anti-double-tap guard.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = Emerald,
                        trackColor = androidx.compose.ui.graphics.Color.Transparent,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f),
                        enabled = !isWorking,
                    ) { Text("Skip") }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1.35f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isWorking && countdown == 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(9.dp))
                            Text("Waiting…")
                        } else {
                            Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Uninstall")
                        }
                    }
                }

                if (!isSingleVpnRemoval && queueTotal - positionInQueue > 0) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = onCancelQueue,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isWorking,
                    ) {
                        Text(
                            "Cancel ${queueTotal - positionInQueue} remaining request" +
                                if (queueTotal - positionInQueue > 1) "s" else "",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                Text(
                    "Nothing is removed without a confirmation from you.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.itemSpacing / 2),
                )
            }
        }
    }
}
