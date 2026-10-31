package us.bergnet.oversight.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import us.bergnet.oversight.data.model.*
import us.bergnet.oversight.data.store.OverlayStateStore
import java.util.UUID

private const val TAG = "PersistenceManager"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "oversight_settings",
    // A corrupt file would otherwise make every read throw; start fresh instead (device ID is regenerated)
    corruptionHandler = ReplaceFileCorruptionHandler { e ->
        Log.e(TAG, "DataStore corrupted, resetting", e)
        emptyPreferences()
    }
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class PersistenceManager(private val context: Context) {

    companion object {
        private val KEY_INFO_VALUES = stringPreferencesKey("info_values")
        private val KEY_OVERLAY_CUSTOMIZATION = stringPreferencesKey("overlay_customization")
        private val KEY_CLOCK_TEXT_FORMAT = stringPreferencesKey("clock_text_format")
        private val KEY_FIXED_NOTIFICATIONS = stringPreferencesKey("fixed_notifications")
        private val KEY_LAYOUT_LIST = stringPreferencesKey("layout_list")
        private val KEY_BUTTON_MAPPINGS = stringPreferencesKey("button_mappings")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val KEY_SEEN_FEATURE_INTROS = stringSetPreferencesKey("seen_feature_intros")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * Load all persisted state into OverlayStateStore. Each key loads independently so one bad
     * value doesn't prevent the rest (including the device ID) from loading.
     */
    suspend fun loadAll() {
        val prefs = try {
            context.dataStore.data.first()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading persisted state", e)
            emptyPreferences()
        }

        loadKey(prefs, KEY_INFO_VALUES) { value ->
            OverlayStateStore.setInfoValues(json.decodeFromString<InfoValues>(value))
        }

        loadKey(prefs, KEY_OVERLAY_CUSTOMIZATION) { value ->
            OverlayStateStore.setOverlayCustomization(json.decodeFromString<OverlayCustomization>(value))
        }

        loadKey(prefs, KEY_CLOCK_TEXT_FORMAT) { value ->
            OverlayStateStore.setClockTextFormat(value)
        }

        loadKey(prefs, KEY_FIXED_NOTIFICATIONS) { value ->
            val notifications = json.decodeFromString<List<FixedNotification>>(value)
            val active = notifications.filter { !it.isExpired() }
            OverlayStateStore.setFixedNotifications(active)
            Log.d(TAG, "Loaded ${active.size} fixed notifications (${notifications.size - active.size} expired filtered)")
        }

        loadKey(prefs, KEY_LAYOUT_LIST) { value ->
            val persistedList = json.decodeFromString<NotificationLayoutList>(value)
            // Always use current code definitions for built-in layouts so that
            // changes to defaults take effect without clearing app data.
            val builtInNames = NotificationLayout.ALL_DEFAULTS.map { it.name }.toSet()
            val customLayouts = persistedList.list.filter { it.name !in builtInNames }
            val mergedList = persistedList.copy(list = NotificationLayout.ALL_DEFAULTS + customLayouts)
            OverlayStateStore.setLayoutList(mergedList)
            Log.d(TAG, "Loaded ${mergedList.list.size} notification layouts (${customLayouts.size} custom)")
        }

        loadKey(prefs, KEY_BUTTON_MAPPINGS) { value ->
            OverlayStateStore.setButtonMappings(json.decodeFromString<List<ButtonMapping>>(value))
        }

        val deviceId = prefs[KEY_DEVICE_ID] ?: UUID.randomUUID().toString().also { newId ->
            try {
                context.dataStore.edit { it[KEY_DEVICE_ID] = newId }
                Log.d(TAG, "Generated new device ID: $newId")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving generated device ID", e)
            }
        }
        OverlayStateStore.setDeviceId(deviceId)

        Log.d(TAG, "State loaded from DataStore")
    }

    private inline fun loadKey(prefs: Preferences, key: Preferences.Key<String>, apply: (String) -> Unit) {
        val value = prefs[key] ?: return
        try {
            apply(value)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ${key.name}, keeping defaults", e)
        }
    }

    suspend fun isOnboardingComplete(): Boolean {
        return try {
            val prefs = context.dataStore.data.first()
            prefs[KEY_ONBOARDING_COMPLETE] ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error reading onboarding state", e)
            false
        }
    }

    suspend fun setOnboardingComplete() {
        try {
            context.dataStore.edit { prefs ->
                prefs[KEY_ONBOARDING_COMPLETE] = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving onboarding state", e)
        }
    }

    /** Names of features whose intro the user has already seen (in onboarding or as a "what's new" step). */
    suspend fun seenFeatureIntros(): Set<String> {
        return try {
            context.dataStore.data.first()[KEY_SEEN_FEATURE_INTROS] ?: emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading seen feature intros", e)
            emptySet()
        }
    }

    suspend fun markFeatureIntrosSeen(features: Collection<String>) {
        try {
            context.dataStore.edit { prefs ->
                prefs[KEY_SEEN_FEATURE_INTROS] = (prefs[KEY_SEEN_FEATURE_INTROS] ?: emptySet()) + features
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving seen feature intros", e)
        }
    }

    /**
     * Start observing StateFlows and auto-saving changes, debounced to batch rapid changes.
     * The current value is saved too (no drop(1)): it costs one redundant write at startup but
     * guarantees a change made before the collectors subscribe is never skipped.
     */
    fun startAutoSave(scope: CoroutineScope) {
        scope.launch {
            OverlayStateStore.infoValues
                .debounce(500)
                .collectLatest { values ->
                    save(KEY_INFO_VALUES, json.encodeToString(values))
                }
        }

        scope.launch {
            OverlayStateStore.overlayCustomization
                .debounce(500)
                .collectLatest { customization ->
                    save(KEY_OVERLAY_CUSTOMIZATION, json.encodeToString(customization))
                }
        }

        scope.launch {
            OverlayStateStore.clockTextFormat
                .debounce(500)
                .collectLatest { format ->
                    if (format != null) {
                        save(KEY_CLOCK_TEXT_FORMAT, format)
                    } else {
                        remove(KEY_CLOCK_TEXT_FORMAT)
                    }
                }
        }

        scope.launch {
            OverlayStateStore.fixedNotifications
                .debounce(500)
                .collectLatest { notifications ->
                    val active = notifications.filter { !it.isExpired() }
                    save(KEY_FIXED_NOTIFICATIONS, json.encodeToString(active))
                }
        }

        scope.launch {
            OverlayStateStore.layoutList
                .debounce(500)
                .collectLatest { layoutList ->
                    save(KEY_LAYOUT_LIST, json.encodeToString(layoutList))
                }
        }

        scope.launch {
            OverlayStateStore.buttonMappings
                .debounce(500)
                .collectLatest { mappings ->
                    save(KEY_BUTTON_MAPPINGS, json.encodeToString(mappings))
                }
        }

        Log.d(TAG, "Auto-save observers started")
    }

    private suspend fun save(key: Preferences.Key<String>, value: String) {
        try {
            context.dataStore.edit { prefs ->
                prefs[key] = value
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving ${key.name}", e)
        }
    }

    private suspend fun remove(key: Preferences.Key<String>) {
        try {
            context.dataStore.edit { prefs ->
                prefs.remove(key)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing ${key.name}", e)
        }
    }
}
