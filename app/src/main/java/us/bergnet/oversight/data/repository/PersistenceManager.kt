package us.bergnet.oversight.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import us.bergnet.oversight.data.model.*
import us.bergnet.oversight.data.store.OverlayStateStore
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "oversight_settings")

@OptIn(kotlinx.coroutines.FlowPreview::class)
class PersistenceManager(private val context: Context) {

    companion object {
        private const val TAG = "PersistenceManager"
        private val KEY_INFO_VALUES = stringPreferencesKey("info_values")
        private val KEY_OVERLAY_CUSTOMIZATION = stringPreferencesKey("overlay_customization")
        private val KEY_CLOCK_TEXT_FORMAT = stringPreferencesKey("clock_text_format")
        private val KEY_FIXED_NOTIFICATIONS = stringPreferencesKey("fixed_notifications")
        private val KEY_LAYOUT_LIST = stringPreferencesKey("layout_list")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * Load all persisted state into OverlayStateStore.
     * Call this before starting auto-save to avoid re-saving loaded data.
     */
    suspend fun loadAll() {
        try {
            val prefs = context.dataStore.data.first()

            prefs[KEY_INFO_VALUES]?.let { value ->
                val infoValues = json.decodeFromString<InfoValues>(value)
                OverlayStateStore.setInfoValues(infoValues)
                Log.d(TAG, "Loaded InfoValues")
            }

            prefs[KEY_OVERLAY_CUSTOMIZATION]?.let { value ->
                val customization = json.decodeFromString<OverlayCustomization>(value)
                OverlayStateStore.setOverlayCustomization(customization)
                Log.d(TAG, "Loaded OverlayCustomization")
            }

            prefs[KEY_CLOCK_TEXT_FORMAT]?.let { value ->
                OverlayStateStore.setClockTextFormat(value)
                Log.d(TAG, "Loaded ClockTextFormat: $value")
            }

            prefs[KEY_FIXED_NOTIFICATIONS]?.let { value ->
                val notifications = json.decodeFromString<List<FixedNotification>>(value)
                val active = notifications.filter { !it.isExpired() }
                OverlayStateStore.setFixedNotifications(active)
                Log.d(TAG, "Loaded ${active.size} fixed notifications (${notifications.size - active.size} expired filtered)")
            }

            prefs[KEY_LAYOUT_LIST]?.let { value ->
                val layoutList = json.decodeFromString<NotificationLayoutList>(value)
                OverlayStateStore.setLayoutList(layoutList)
                Log.d(TAG, "Loaded ${layoutList.list.size} notification layouts")
            }

            val deviceId = prefs[KEY_DEVICE_ID] ?: UUID.randomUUID().toString().also { newId ->
                context.dataStore.edit { it[KEY_DEVICE_ID] = newId }
                Log.d(TAG, "Generated new device ID: $newId")
            }
            OverlayStateStore.setDeviceId(deviceId)

            Log.d(TAG, "All state loaded from DataStore")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading persisted state", e)
        }
    }

    /**
     * Start observing StateFlows and auto-saving changes.
     * Uses drop(1) to skip the initial value (already loaded) and
     * debounce to batch rapid changes.
     */
    fun startAutoSave(scope: CoroutineScope) {
        scope.launch {
            OverlayStateStore.infoValues
                .drop(1)
                .debounce(500)
                .collectLatest { values ->
                    save(KEY_INFO_VALUES, json.encodeToString(values))
                }
        }

        scope.launch {
            OverlayStateStore.overlayCustomization
                .drop(1)
                .debounce(500)
                .collectLatest { customization ->
                    save(KEY_OVERLAY_CUSTOMIZATION, json.encodeToString(customization))
                }
        }

        scope.launch {
            OverlayStateStore.clockTextFormat
                .drop(1)
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
                .drop(1)
                .debounce(500)
                .collectLatest { notifications ->
                    val active = notifications.filter { !it.isExpired() }
                    save(KEY_FIXED_NOTIFICATIONS, json.encodeToString(active))
                }
        }

        scope.launch {
            OverlayStateStore.layoutList
                .drop(1)
                .debounce(500)
                .collectLatest { layoutList ->
                    save(KEY_LAYOUT_LIST, json.encodeToString(layoutList))
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
