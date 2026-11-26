package io.github.ooscam.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ooscam.capture.CaptureConfig
import io.github.ooscam.core.Quality
import io.github.ooscam.core.StorageTarget
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scopes = mutableListOf<CoroutineScope>()

    private fun repo(file: File): SettingsRepository {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also(scopes::add)
        return SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @After
    fun close() = scopes.forEach { it.cancel() }

    @Test
    fun defaults_followSpec() = runBlocking {
        val s = repo(tmp.newFolder().resolve("s.preferences_pb")).settings.first()
        assertEquals(AppSettings(), s)
        assertEquals(30, s.segmentMinutes)
        assertEquals(Quality.Q1080P30, s.quality)
        assertTrue(s.micDefault)
        assertEquals(StorageTarget.DEVICE, s.storage)
        assertFalse(s.reduceGlass)
        assertEquals(AppLanguage.SYSTEM, s.language)
    }

    @Test
    fun everySetting_persistsAcrossRepositories() {
        val file = tmp.newFolder().resolve("s.preferences_pb")
        runBlocking {
            val r = repo(file)
            r.setSegmentMinutes(10)
            r.setQuality(Quality.Q720P30)
            r.setMicDefault(false)
            r.setStorage(StorageTarget.SD)
            r.setReduceGlass(true)
            r.setLanguage(AppLanguage.VI)
        }
        close()
        val again = runBlocking { repo(file).settings.first() }
        assertEquals(AppSettings(10, Quality.Q720P30, false, StorageTarget.SD, true, AppLanguage.VI), again)
    }

    @Test
    fun segmentLength_acceptsOnly5_10_30_60() {
        assertEquals(listOf(5, 10, 30, 60), AppSettings.SEGMENT_MINUTES)
        val r = repo(tmp.newFolder().resolve("s.preferences_pb"))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { r.setSegmentMinutes(15) } }
    }

    @Test
    fun captureConfig_takesSegmentLengthQualityAndTarget_fromSettings() {
        val s = AppSettings(segmentMinutes = 5, quality = Quality.Q2160P30, storage = StorageTarget.SD)
        val c = s.captureConfig(micOn = false, target = StorageTarget.DEVICE)
        assertEquals(CaptureConfig(quality = Quality.Q2160P30, micWanted = false, segmentMinutes = 5, target = StorageTarget.DEVICE), c)
    }

    @Test
    fun language_appliesPerAppLocale_onAndroid13Plus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locales = context.getSystemService(LocaleManager::class.java)
        AppLanguages.apply(context, AppLanguage.VI)
        assertEquals(LocaleList.forLanguageTags("vi"), locales.applicationLocales)
        AppLanguages.apply(context, AppLanguage.SYSTEM)
        assertEquals(LocaleList.getEmptyLocaleList(), locales.applicationLocales)
        assertTrue(AppLanguages.supported(33))
        assertFalse(AppLanguages.supported(32))
    }
}
