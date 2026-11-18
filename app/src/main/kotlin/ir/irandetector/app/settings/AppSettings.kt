package ir.irandetector.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** All user preferences, persisted with DataStore (no SharedPreferences). */
@Singleton
class AppSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromKey(prefs[KEY_THEME])
    }

    val hideExcluded: Flow<Boolean> = context.dataStore.data.map { it[KEY_HIDE_EXCLUDED] ?: true }
    val includeSystemApps: Flow<Boolean> = context.dataStore.data.map { it[KEY_INCLUDE_SYSTEM] ?: true }
    val minimumConfidence: Flow<Int> = context.dataStore.data.map { (it[KEY_MIN_CONFIDENCE] ?: 30L).toInt() }
    val sortMode: Flow<SortMode> = context.dataStore.data.map { SortMode.fromKey(it[KEY_SORT]) }
    val databaseUrl: Flow<String?> = context.dataStore.data.map { it[KEY_DB_URL] }
    /** VPN Hunter is opt-in: it is an extra scan, never part of the normal flow. */
    val vpnHunterEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_VPN_HUNTER] ?: false }
    val lastScanAt: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_SCAN] ?: 0L }

    // ---- onboarding / helper flags ---------------------------------------
    /** Shown once: the browser/package-installer walkthrough. */
    val installHelpSeen: Flow<Boolean> = context.dataStore.data.map { it[KEY_INSTALL_HELP_SEEN] ?: false }
    /** Shown once: what the uninstall dialog looks like and why it must be accepted. */
    val uninstallHelpSeen: Flow<Boolean> = context.dataStore.data.map { it[KEY_UNINSTALL_HELP_SEEN] ?: false }
    /** Uninstall pacing: gives the package manager time to settle between requests. */
    val uninstallDelayMs: Flow<Int> = context.dataStore.data.map { (it[KEY_UNINSTALL_DELAY] ?: 350L).toInt() }
    /** Interactive (custom) uninstaller instead of the OEM system screen. */
    val useCustomUninstaller: Flow<Boolean> = context.dataStore.data.map { it[KEY_CUSTOM_UNINSTALLER] ?: true }
    /** VPN Hunter: also list apps that only look like a VPN (Possible tier). */
    val vpnShowPossible: Flow<Boolean> = context.dataStore.data.map { it[KEY_VPN_SHOW_POSSIBLE] ?: true }
    /** VPN Hunter: include system apps in the scan. */
    val vpnIncludeSystemApps: Flow<Boolean> = context.dataStore.data.map { it[KEY_VPN_SYSTEM] ?: true }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[KEY_THEME] = mode.key }
    suspend fun setHideExcluded(value: Boolean) = edit { it[KEY_HIDE_EXCLUDED] = value }
    suspend fun setIncludeSystemApps(value: Boolean) = edit { it[KEY_INCLUDE_SYSTEM] = value }
    suspend fun setMinimumConfidence(value: Int) = edit { it[KEY_MIN_CONFIDENCE] = value.toLong() }
    suspend fun setSortMode(mode: SortMode) = edit { it[KEY_SORT] = mode.key }
    suspend fun setVpnHunterEnabled(enabled: Boolean) = edit { it[KEY_VPN_HUNTER] = enabled }
    suspend fun setDatabaseUrl(url: String?) = edit {
        if (url.isNullOrBlank()) it.remove(KEY_DB_URL) else it[KEY_DB_URL] = url.trim()
    }
    suspend fun setLastScanAt(timestamp: Long) = edit { it[KEY_LAST_SCAN] = timestamp }
    suspend fun setInstallHelpSeen(seen: Boolean) = edit { it[KEY_INSTALL_HELP_SEEN] = seen }
    suspend fun setUninstallHelpSeen(seen: Boolean) = edit { it[KEY_UNINSTALL_HELP_SEEN] = seen }
    suspend fun setUninstallDelayMs(value: Int) = edit { it[KEY_UNINSTALL_DELAY] = value.toLong() }
    suspend fun setUseCustomUninstaller(value: Boolean) = edit { it[KEY_CUSTOM_UNINSTALLER] = value }
    suspend fun setVpnShowPossible(value: Boolean) = edit { it[KEY_VPN_SHOW_POSSIBLE] = value }
    suspend fun setVpnIncludeSystemApps(value: Boolean) = edit { it[KEY_VPN_SYSTEM] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { prefs -> block(prefs) }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_HIDE_EXCLUDED = booleanPreferencesKey("hide_excluded")
        val KEY_INCLUDE_SYSTEM = booleanPreferencesKey("include_system")
        val KEY_MIN_CONFIDENCE = longPreferencesKey("min_confidence")
        val KEY_SORT = stringPreferencesKey("sort_mode")
        val KEY_DB_URL = stringPreferencesKey("database_url")
        val KEY_VPN_HUNTER = booleanPreferencesKey("vpn_hunter_enabled")
        val KEY_LAST_SCAN = longPreferencesKey("last_scan_at")
        val KEY_INSTALL_HELP_SEEN = booleanPreferencesKey("install_help_seen")
        val KEY_UNINSTALL_HELP_SEEN = booleanPreferencesKey("uninstall_help_seen")
        val KEY_UNINSTALL_DELAY = longPreferencesKey("uninstall_delay_ms")
        val KEY_CUSTOM_UNINSTALLER = booleanPreferencesKey("custom_uninstaller")
        val KEY_VPN_SHOW_POSSIBLE = booleanPreferencesKey("vpn_show_possible")
        val KEY_VPN_SYSTEM = booleanPreferencesKey("vpn_include_system")
    }
}

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Follow system"),
    DARK("dark", "Dark"),
    LIGHT("light", "Light");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: DARK
    }
}

enum class SortMode(val key: String, val label: String) {
    CONFIDENCE("confidence", "Confidence"),
    NAME("name", "Name"),
    RECENT("recent", "Install date");

    companion object {
        fun fromKey(key: String?): SortMode = entries.firstOrNull { it.key == key } ?: CONFIDENCE
    }
}
