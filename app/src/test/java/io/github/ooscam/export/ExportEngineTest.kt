package io.github.ooscam.export

import androidx.media3.transformer.Composition
import io.github.ooscam.core.ExportPlan
import io.github.ooscam.core.PlanPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDateTime

class FakeRunner : ExportRunner {
    var composition: Composition? = null
    var path: String? = null
    var listener: ExportRunner.Listener? = null
    var progress: Int? = null
    var cancelled = false

    override fun start(composition: Composition, outputPath: String, listener: ExportRunner.Listener) {
        this.composition = composition
        this.path = outputPath
        this.listener = listener
        File(outputPath).writeText("mp4")
    }

    override fun progress(): Int? = progress

    override fun cancel() {
        cancelled = true
    }
}

class FakePublisher : ExportPublisher {
    val published = mutableListOf<Pair<String, String>>()

    override fun publish(file: File, displayName: String): String {
        published += file.readText() to displayName
        return "content://m/out/$displayName"
    }
}

@RunWith(RobolectricTestRunner::class)
class ExportEngineTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val runner = FakeRunner()
    private val publisher = FakePublisher()
    private val engine by lazy { ExportEngine(runner, publisher, tmp.root) }
    private val start = LocalDateTime.of(2030, 1, 2, 3, 4, 5)
    private val plan = ExportPlan("p", true, emptyList(), emptyList(), 1_000, 1_000, listOf(PlanPart(0, 0, 1_000, "1", 0, 1_000)), false, 1080, sourceUris = mapOf(0 to "content://m/a"))

    @Test
    fun start_runsTheMappedComposition_intoATempFileNamedForTheExportStart() {
        engine.start(plan, start)
        assertEquals(ExportState.Running(0), engine.state.value)
        assertEquals("OOSCam_edit_20300102_030405.mp4", File(runner.path!!).name)
        assertEquals(1, runner.composition!!.sequences.single().editedMediaItems.size)
    }

    @Test
    fun pollProgress_reportsRunnerPercent() {
        engine.start(plan, start)
        runner.progress = 42
        engine.pollProgress()
        assertEquals(ExportState.Running(42), engine.state.value)
        runner.progress = null
        engine.pollProgress()
        assertEquals(ExportState.Running(42), engine.state.value)
    }

    @Test
    fun completion_publishesToTheLibraryFolder_andRemovesTheTempFile() {
        engine.start(plan, start)
        val temp = File(runner.path!!)
        runner.listener!!.onCompleted()
        assertEquals(listOf("mp4" to "OOSCam_edit_20300102_030405.mp4"), publisher.published)
        assertEquals(ExportState.Done("content://m/out/OOSCam_edit_20300102_030405.mp4"), engine.state.value)
        assertFalse(temp.exists())
    }

    @Test
    fun cancel_stopsTheRunner_deletesTheTempFile_andPublishesNothing() {
        engine.start(plan, start)
        val temp = File(runner.path!!)
        engine.cancel()
        assertTrue(runner.cancelled)
        assertFalse(temp.exists())
        assertEquals(ExportState.Cancelled, engine.state.value)
        assertTrue(publisher.published.isEmpty())
    }

    @Test
    fun error_isReported_andTheTempFileRemoved() {
        engine.start(plan, start)
        val temp = File(runner.path!!)
        runner.listener!!.onError("ERROR_CODE_DECODING_FAILED")
        assertEquals(ExportState.Failed("ERROR_CODE_DECODING_FAILED"), engine.state.value)
        assertFalse(temp.exists())
    }

    @Test
    fun secondStartWhileRunning_isIgnored() {
        engine.start(plan, start)
        val first = runner.path
        engine.start(plan, start.plusSeconds(1))
        assertEquals(first, runner.path)
    }

    @Test
    fun planWithErrors_failsWithoutStartingTheRunner() {
        engine.start(plan.copy(ok = false, errors = listOf("E6"), parts = emptyList(), outputDurationMs = null), start)
        assertNull(runner.composition)
        assertEquals(ExportState.Failed("E6"), engine.state.value)
    }

    @Test
    fun reset_returnsToIdleAfterAFinishedExport() {
        engine.start(plan, start)
        runner.listener!!.onCompleted()
        engine.reset()
        assertEquals(ExportState.Idle, engine.state.value)
    }
}
