package ir.irandetector.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import ir.irandetector.app.presentation.AppViewModel
import ir.irandetector.app.ui.AppNavHost
import ir.irandetector.app.ui.theme.IranDetectorTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The single Activity.
 *
 * Responsibilities beyond hosting Compose:
 *  1. Turning [AppViewModel.UiEvent.UninstallRequested] into a removal attempt,
 *     exactly once per request, one app at a time.
 *  2. Deciding *how* that request reaches the user, because the OEM uninstaller is
 *     not reliable across devices:
 *
 *       Stage 1 - `ACTION_UNINSTALL_PACKAGE` / `ACTION_DELETE` with
 *                 `EXTRA_RETURN_RESULT`. Fastest path, keeps the OEM look.
 *       Stage 2 - if that produced no dialog at all (no `onPause`, no result, and
 *                 the package still installed) we fall back to the app's own
 *                 Settings page, where an "Uninstall" button always exists.
 *
 *  3. Verifying the outcome with `PackageManager` instead of trusting
 *     `resultCode`, which several OEM skins get wrong.
 *
 * The user-visible confirmation is the in-app dialog owned by
 * [AppViewModel.UninstallPrompt] — that is what guarantees a prompt appears for
 * every single app, even on builds that swallow the system dialog.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    /** Package whose removal is being attempted right now. */
    private var pendingPackage: String? = null

    /** Guards the result from being reported twice (result callback + verification). */
    private var resultResolved = true

    /** Set true when the system UI was actually shown for [pendingPackage]. */
    private var sawSystemUi = false

    /** How many staged verification attempts already ran for [pendingPackage]. */
    private var verifyAttempts = 0

    /** True once we had to hand the user to app settings instead of the OEM dialog. */
    private var usedSettingsFallback = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val uninstallLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val packageName = pendingPackage ?: return@registerForActivityResult
        val reportedSuccess = result.resultCode == Activity.RESULT_OK
        Log.i(TAG, "Uninstall dialog returned for $packageName: resultCode=${result.resultCode}")
        if (reportedSuccess) {
            finishUninstall(packageName, success = true)
        } else {
            // Never trust a negative result: several OEM settings apps return
            // RESULT_CANCELED after a perfectly successful uninstall.
            scheduleVerification(packageName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            IranDetectorTheme(mode = state.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost(viewModel = viewModel)
                }
            }
        }
        observeEvents()
        restorePendingUninstall(savedInstanceState)
    }

    /**
     * If the process was recreated while a dialog was open, the activity result is
     * lost. The queue still believes it awaits a result, so we re-verify instead of
     * dead-locking the queue.
     */
    private fun restorePendingUninstall(state: Bundle?) {
        val packageName = state?.getString(STATE_PENDING_PACKAGE)
        if (packageName == null) {
            lifecycleScope.launch {
                if (viewModel.uninstallState.value.awaitingResult) {
                    viewModel.uninstallState.value.current?.let { item ->
                        pendingPackage = item.packageName
                        resultResolved = false
                        scheduleVerification(item.packageName)
                    }
                }
            }
            return
        }
        pendingPackage = packageName
        resultResolved = false
        scheduleVerification(packageName)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingPackage?.let {
            outState.putString(STATE_PENDING_PACKAGE, it)
            outState.putBoolean(STATE_SAW_SYSTEM_UI, sawSystemUi)
        }
    }

    override fun onPause() {
        super.onPause()
        // Proof that *something* was shown: the system dialog always pauses us.
        if (pendingPackage != null && !resultResolved) sawSystemUi = true
    }

    override fun onResume() {
        super.onResume()
        val packageName = pendingPackage ?: return
        if (!resultResolved) scheduleVerification(packageName)
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            // `collect` (not `collectLatest`): cancelling the collector mid-launch is
            // exactly what used to drop uninstall requests and freeze the queue.
            viewModel.events.collect { event ->
                when (event) {
                    is AppViewModel.UiEvent.UninstallRequested -> launchUninstall(event.packageName)
                    is AppViewModel.UiEvent.Toast ->
                        toast(event.message, Toast.LENGTH_SHORT)
                    is AppViewModel.UiEvent.DatabaseUpdated ->
                        toast("Database updated (${event.count} records)", Toast.LENGTH_SHORT)
                    is AppViewModel.UiEvent.VpnScanFinished ->
                        toast(event.message, Toast.LENGTH_SHORT)
                }
            }
        }
    }

    /**
     * Starts the removal attempt for exactly one package.
     *
     * The in-app dialog belongs to the ViewModel; this method only runs after the
     * user tapped "Uninstall" there, so there is always exactly one request per app.
     */
    private fun launchUninstall(packageName: String) {
        pendingPackage = packageName
        resultResolved = false
        sawSystemUi = false
        verifyAttempts = 0
        usedSettingsFallback = false

        val intents = buildList {
            add(
                Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$packageName"))
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true),
            )
            add(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true),
            )
        }

        for (intent in intents) {
            if (intent.resolveActivity(packageManager) == null) {
                Log.w(TAG, "No handler for ${intent.action} / $packageName")
                continue
            }
            try {
                uninstallLauncher.launch(intent)
                Log.i(TAG, "Launched ${intent.action} for $packageName")
                // If nothing was ever displayed we fall back to app settings.
                scheduleVerification(packageName, requireSystemUi = true)
                return
            } catch (t: ActivityNotFoundException) {
                Log.w(TAG, "ActivityNotFoundException for ${intent.action} / $packageName", t)
            } catch (t: SecurityException) {
                Log.w(TAG, "SecurityException for ${intent.action} / $packageName", t)
            }
        }

        // No uninstaller reachable at all: go straight to the settings fallback.
        Log.w(TAG, "No uninstall activity resolved for $packageName; using settings fallback")
        openAppSettings(packageName)
    }

    /**
     * Staged verification.
     *
     * @param requireSystemUi when true and no system UI has been observed after the
     *   delay, the OEM uninstaller silently failed and we switch to the settings
     *   fallback so the user always gets *somewhere* they can press Uninstall.
     */
    private fun scheduleVerification(packageName: String, requireSystemUi: Boolean = false) {
        // The user-tunable pacing (Settings -> Uninstall safety) scales the whole probe
        // schedule: slow OEM builds need more time between requests.
        val base = viewModel.state.value.uninstallDelayMs.coerceIn(150, 2000).toLong()
        val delays = longArrayOf(base, base * 2, base * 4, base * 6, base * 8)
        val delayMs = delays.getOrElse(verifyAttempts) { 3000L }
        mainHandler.postDelayed({
            if (resultResolved || pendingPackage != packageName) return@postDelayed
            val installed = isPackageInstalled(packageName)
            Log.i(
                TAG,
                "Verify #$verifyAttempts for $packageName: installed=$installed " +
                    "sawSystemUi=$sawSystemUi attempts=$verifyAttempts",
            )
            if (!installed) {
                finishUninstall(packageName, success = true)
                return@postDelayed
            }
            if (requireSystemUi && !sawSystemUi && verifyAttempts >= 1 && !usedSettingsFallback) {
                usedSettingsFallback = true
                viewModel.onSystemDialogUnavailable()
                toast("Opening app settings — press Uninstall there", Toast.LENGTH_LONG)
                openAppSettings(packageName)
                return@postDelayed
            }
            if (verifyAttempts >= delays.size - 1) {
                // Still installed and no answer: report honestly, never claim success.
                finishUninstall(packageName, success = false)
                return@postDelayed
            }
            verifyAttempts += 1
            if (usedSettingsFallback && !activityReachable()) {
                // The user cancelled out of settings; verify once more then stop.
                finishUninstall(packageName, success = false)
                return@postDelayed
            }
            scheduleVerification(packageName, requireSystemUi = false)
        }, delayMs)
    }

    private fun activityReachable(): Boolean = !isFinishing && !isDestroyed

    /** Opens the system app-info screen, which always offers an Uninstall button. */
    private fun openAppSettings(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
            sawSystemUi = true
            verifyAttempts = 0
            mainHandler.postDelayed({ scheduleVerification(packageName) }, 800)
        } catch (t: ActivityNotFoundException) {
            Log.w(TAG, "No app-details screen for $packageName", t)
            finishUninstall(packageName, success = false)
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (t: Throwable) {
        false
    }

    private fun finishUninstall(packageName: String, success: Boolean) {
        if (resultResolved) return
        resultResolved = true
        pendingPackage = null
        if (success) toast("Application removed", Toast.LENGTH_SHORT)
        viewModel.onUninstallResult(succeeded = success, packageName = packageName)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun toast(message: String, duration: Int) =
        Toast.makeText(this, message, duration).show()

    private companion object {
        const val TAG = "MainActivity"
        const val STATE_PENDING_PACKAGE = "pending_uninstall_package"
        const val STATE_SAW_SYSTEM_UI = "pending_uninstall_saw_ui"
    }
}

/** Kept for call sites that used to build a package uri by hand. */
internal fun Context.packageUri(packageName: String): Uri = Uri.parse("package:$packageName")

/** True when this Android build still ships the deprecated but reliable action. */
internal val supportsUninstallPackageAction: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
