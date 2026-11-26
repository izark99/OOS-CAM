package io.github.ooscam.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.ooscam.capture.CaptureConfig
import io.github.ooscam.core.Quality
import io.github.ooscam.core.StorageTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppLanguage(val tag: String?) { SYSTEM(null), EN("en"), VI("vi") }

/** User settings (SPEC S1-S3). Defaults: 30 min segments, 1080p30, mic on, device storage, glass on, system language. */
data class AppSettings(
    val segmentMinutes: Int = 30,
    val quality: Quality = Quality.Q1080P30,
    val micDefault: Boolean = true,
    val storage: StorageTarget = StorageTarget.DEVICE,
    val reduceGlass: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
) {
    /** The recording config for one session; [target] is the storage actually usable now. */
    fun captureConfig(micOn: Boolean, target: StorageTarget): CaptureConfig =
        CaptureConfig(quality = quality, micWanted = micOn, segmentMinutes = segmentMinutes, target = target)

    companion object {
        val SEGMENT_MINUTES = listOf(5, 10, 30, 60)
    }
}

/** DataStore-backed settings; unknown or invalid stored values fall back to the defaults. */
class SettingsRepository(private val store: DataStore<Preferences>) {
    val settings: Flow<AppSettings> = store.data.map { p ->
        val d = AppSettings()
        AppSettings(
            segmentMinutes = p[SEGMENT]?.takeIf { it in AppSettings.SEGMENT_MINUTES } ?: d.segmentMinutes,
            quality = p[QUALITY]?.let { q -> Quality.entries.firstOrNull { it.name == q } } ?: d.quality,
            micDefault = p[MIC] ?: d.micDefault,
            storage = p[STORAGE]?.let { s -> StorageTarget.entries.firstOrNull { it.name == s } } ?: d.storage,
            reduceGlass = p[REDUCE_GLASS] ?: d.reduceGlass,
            language = p[LANGUAGE]?.let { l -> AppLanguage.entries.firstOrNull { it.name == l } } ?: d.language,
        )
    }

    suspend fun setSegmentMinutes(minutes: Int) {
        require(minutes in AppSettings.SEGMENT_MINUTES) { "segment length $minutes min is not offered" }
        store.edit { it[SEGMENT] = minutes }
    }

    suspend fun setQuality(quality: Quality) = store.edit { it[QUALITY] = quality.name }
    suspend fun setMicDefault(on: Boolean) = store.edit { it[MIC] = on }
    suspend fun setStorage(target: StorageTarget) = store.edit { it[STORAGE] = target.name }
    suspend fun setReduceGlass(on: Boolean) = store.edit { it[REDUCE_GLASS] = on }
    suspend fun setLanguage(language: AppLanguage) = store.edit { it[LANGUAGE] = language.name }

    companion object {
        private val SEGMENT = intPreferencesKey("segment_minutes")
        private val QUALITY = stringPreferencesKey("quality")
        private val MIC = booleanPreferencesKey("mic_default")
        private val STORAGE = stringPreferencesKey("storage")
        private val REDUCE_GLASS = booleanPreferencesKey("reduce_glass")
        private val LANGUAGE = stringPreferencesKey("language")

        private val Context.settingsStore by preferencesDataStore(name = "settings")

        fun get(context: Context) = SettingsRepository(context.applicationContext.settingsStore)
    }
}

/** Per-app language through the framework LocaleManager (Android 13+, with res/xml locale config). */
object AppLanguages {
    fun supported(sdkInt: Int = Build.VERSION.SDK_INT) = sdkInt >= Build.VERSION_CODES.TIRAMISU

    fun apply(context: Context, language: AppLanguage) {
        if (!supported()) return
        val locales = language.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        context.getSystemService(LocaleManager::class.java)?.applicationLocales = locales
    }

    fun current(context: Context): AppLanguage {
        if (!supported()) return AppLanguage.SYSTEM
        val tag = context.getSystemService(LocaleManager::class.java)?.applicationLocales?.get(0)?.language
        return AppLanguage.entries.firstOrNull { it.tag != null && it.tag == tag } ?: AppLanguage.SYSTEM
    }
}
