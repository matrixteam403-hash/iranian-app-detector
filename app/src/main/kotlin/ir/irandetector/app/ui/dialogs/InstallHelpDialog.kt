package ir.irandetector.app.ui.dialogs

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.irandetector.app.ui.AppIcons
import ir.irandetector.app.ui.theme.Emerald

/**
 * Fixes the second reported problem: after downloading an APK, tapping it in the
 * browser only offers the terminal apps — the "Package installer" entry is missing
 * and nothing installs.
 *
 * Why it happens (and why nothing in the APK can patch the browser itself):
 *  1. Chrome and most browsers cannot hand an `application/vnd.android.package-archive`
 *     file to Android unless they hold `REQUEST_INSTALL_PACKAGES`, and sending an
 *     install intent at all additionally requires "Install unknown apps" for the
 *     *browser*. Both are user grants; no app can toggle them.
 *  2. A browser that was launched as a Custom Tab or from a messaging app sometimes
 *     resolves the download into a `content://` URI the installer cannot read, so it
 *     offers a terminal app instead.
 *
 * What this dialog does is therefore the honest, working path: it explains the two
 * switches, opens each settings screen directly, and — when we know a downloaded
 * file path — asks the platform installer to open that exact APK.
 */
@Composable
fun InstallHelpDialog(
    packageName: String?,
    onDismiss: () -> Unit,
    /** Absolute path of a downloaded .apk in shared storage, when known. */
    apkPath: String? = null,
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Emerald.copy(alpha = 0.25f)),
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Emerald.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.OpenInNew, contentDescription = null, tint = Emerald)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Installing the APK",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Why the browser offers the wrong app",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    "If a downloaded APK only offers \"Termux\" or a file manager instead of " +
                        "\"Package installer\", the browser is missing permission to hand the file " +
                        "to Android. This is a browser setting, not a problem with the APK.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.height(16.dp))

                StepRow(
                    number = 1,
                    title = "Allow installs from your browser",
                    body = "Enable \"Allow from this source\" for the browser you downloaded " +
                        "with (Chrome, Firefox, Samsung Internet, …).",
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { context.openUnknownSourcesSettings() },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Icon(AppIcons.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open \"Install unknown apps\"")
                }

                Spacer(Modifier.height(18.dp))

                StepRow(
                    number = 2,
                    title = "Open the file with the package installer",
                    body = "Use a file manager's \"Open with → Package installer\", or tap the " +
                        "download notification instead of the browser's inline viewer.",
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { context.openDownloads() },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Icon(AppIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Downloads")
                }

                if (apkPath != null) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { context.installApk(apkPath) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        Icon(AppIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Hand this APK to the installer")
                    }
                }

                Spacer(Modifier.height(18.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Row(Modifier.padding(11.dp)) {
                        Icon(
                            AppIcons.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "This app never installs anything by itself and does not request " +
                                "the INSTALL_PACKAGES permission — Android only grants that to " +
                                "system apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (packageName != null) {
                        TextButton(
                            onClick = { context.openAppInfo(packageName) },
                            modifier = Modifier.weight(1f),
                        ) { Text("App info") }
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(13.dp),
                    ) { Text("Got it") }
                }
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Emerald.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Emerald,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- platform glue

private fun Context.openUnknownSourcesSettings() {
    // Android 8+: per-app "install unknown apps" screen. Older builds used a single
    // global switch; both intents below are public SDK APIs.
    val candidates = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            add(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
        }
        add(Intent(Settings.ACTION_SECURITY_SETTINGS))
    }
    launchFirst(candidates, "No install-sources settings screen on this device")
}

private fun Context.openDownloads() {
    val candidates = listOf(
        Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS),
        Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://downloads"), "*/*"),
        Intent(Settings.ACTION_APPLICATION_SETTINGS),
    )
    launchFirst(candidates, "No downloads screen on this device")
}

private fun Context.openAppInfo(packageName: String) {
    launchFirst(
        listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        ),
        "No app-info screen on this device",
    )
}

/**
 * Hands an .apk file to the platform package installer.
 *
 * This only works when the APK lives in a location a FileProvider/`file://` URI can
 * point at and the *calling* app is allowed to request installs. Since this app does
 * not request REQUEST_INSTALL_PACKAGES on purpose (it is not an installer), the
 * intent is used purely as "open with", which the system installer accepts.
 */
private fun Context.installApk(path: String) {
    val uri = Uri.parse("file://$path")
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    launchFirst(
        listOf(intent, Intent(Settings.ACTION_APPLICATION_SETTINGS)),
        "Cannot open the installer for this file",
    )
}

private fun Context.launchFirst(intents: List<Intent>, failureMessage: String) {
    for (intent in intents) {
        try {
            startActivity(intent)
            return
        } catch (t: ActivityNotFoundException) {
            Log.d("InstallHelp", "No handler for ${intent.action}")
        } catch (t: SecurityException) {
            Log.d("InstallHelp", "Blocked: ${intent.action}")
        }
    }
    Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
}
