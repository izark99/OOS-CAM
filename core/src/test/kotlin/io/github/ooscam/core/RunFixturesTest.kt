package io.github.ooscam.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RunFixturesTest {
    private fun fixtures(tmp: Path, export: String, segment: String): Path {
        val dir = Files.createDirectory(tmp.resolve("fixtures"))
        dir.resolve("export_cases.json").writeText(export)
        dir.resolve("segment_cases.json").writeText(segment)
        return dir
    }

    private fun json(path: Path) = Json.parseToJsonElement(path.readText())

    @Test
    fun runFixtures_emptyCaseFiles_writeEmptyArrays(@TempDir tmp: Path) {
        val out = tmp.resolve("out")
        runFixtures(fixtures(tmp, "[]", "[]"), out)
        assertEquals("[]", out.resolve("export_actual.json").readText().trim())
        assertEquals("[]", out.resolve("segment_actual.json").readText().trim())
    }

    @Test
    fun runFixtures_exportCase_writesTheComputedPlanWithExactKeys(@TempDir tmp: Path) {
        val export = """[{"id":"A1","rule":"note","muted":true,"exportHeight":1080,
            "clips":[{"order":0,"sourceUri":"u://x","sourceDurationMs":5000,"sourceHeight":1080,"inMs":1000,"outMs":3000}],
            "speedRanges":[{"clipOrder":0,"startMs":1000,"endMs":2000,"speed":2}]}]"""
        val out = tmp.resolve("out")
        runFixtures(fixtures(tmp, export, "[]"), out)
        val expected = """[{"id":"A1","ok":true,"errors":[],"warnings":[],"trimmedSourceMs":2000,"outputDurationMs":1500,
            "parts":[{"clipOrder":0,"srcStartMs":1000,"srcEndMs":2000,"speed":"2","outStartMs":0,"outDurationMs":500},
                     {"clipOrder":0,"srcStartMs":2000,"srcEndMs":3000,"speed":"1","outStartMs":500,"outDurationMs":1000}],
            "audio":false,"exportHeight":1080}]"""
        assertEquals(Json.parseToJsonElement(expected), json(out.resolve("export_actual.json")))
    }

    @Test
    fun runFixtures_failedExportCase_writesNullDuration(@TempDir tmp: Path) {
        val export = """[{"id":"A2","rule":"note","muted":false,"exportHeight":720,
            "clips":[{"order":0,"sourceUri":"u://x","sourceDurationMs":5000,"sourceHeight":720,"inMs":0,"outMs":100}],
            "speedRanges":[]}]"""
        val out = tmp.resolve("out")
        runFixtures(fixtures(tmp, export, "[]"), out)
        val expected = """[{"id":"A2","ok":false,"errors":["E6"],"warnings":[],"trimmedSourceMs":100,
            "outputDurationMs":null,"parts":[],"audio":true,"exportHeight":720}]"""
        assertEquals(Json.parseToJsonElement(expected), json(out.resolve("export_actual.json")))
    }

    @Test
    fun runFixtures_segmentCase_writesTheComputedPlan(@TempDir tmp: Path) {
        val segment = """[{"id":"B1","rule":"note","startLocal":"2033-04-05T06:07:08","segmentMinutes":1,"target":"device",
            "quality":"720p30","audio":false,"stopAtMs":90000,"freeSpaceSamples":[]}]"""
        val out = tmp.resolve("out")
        runFixtures(fixtures(tmp, "[]", segment), out)
        val expected = """[{"id":"B1","stopReason":"user","endMs":90000,"bytesPerMs":1000,"segments":[
            {"index":1,"fileName":"OOSCam_20330405_060708_part01.mp4","startOffsetMs":0,"durationMs":60000,"estimatedBytes":60000000},
            {"index":2,"fileName":"OOSCam_20330405_060708_part02.mp4","startOffsetMs":60000,"durationMs":30000,"estimatedBytes":30000000}]}]"""
        assertEquals(Json.parseToJsonElement(expected), json(out.resolve("segment_actual.json")))
    }

    @Test
    fun runFixtures_unknownKeyInCase_failsInsteadOfBeingIgnored(@TempDir tmp: Path) {
        val export = """[{"id":"A","rule":"r","muted":false,"exportHeight":1080,"clips":[],"speedRanges":[],"extra":1}]"""
        assertThrows(SerializationException::class.java) {
            runFixtures(fixtures(tmp, export, "[]"), tmp.resolve("out"))
        }
    }

    @Test
    fun runFixtures_outDirMissing_isCreatedIncludingParents(@TempDir tmp: Path) {
        val out = tmp.resolve("a").resolve("b")
        runFixtures(fixtures(tmp, "[]", "[]"), out)
        assertEquals(true, Files.isRegularFile(out.resolve("export_actual.json")))
    }

    @Test
    fun runFixtures_inputFileMissing_failsInsteadOfWritingOutput(@TempDir tmp: Path) {
        val dir = Files.createDirectory(tmp.resolve("fixtures"))
        dir.resolve("export_cases.json").writeText("[]")
        val out = tmp.resolve("out")
        assertThrows(java.nio.file.NoSuchFileException::class.java) { runFixtures(dir, out) }
        assertEquals(false, Files.exists(out.resolve("export_actual.json")))
    }

    @Test
    fun parseArgs_needsExactlyTwoPaths() {
        assertThrows(IllegalArgumentException::class.java) { parseArgs(arrayOf("only-one")) }
        val (a, b) = parseArgs(arrayOf("in", "out"))
        assertEquals(Path.of("in"), a)
        assertEquals(Path.of("out"), b)
    }
}
