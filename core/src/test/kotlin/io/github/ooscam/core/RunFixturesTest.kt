package io.github.ooscam.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RunFixturesTest {
    @Test
    fun runFixtures_withBothInputFiles_writesEmptyArrays(@TempDir tmp: Path) {
        val fixtures = Files.createDirectory(tmp.resolve("fixtures"))
        fixtures.resolve("export_cases.json").writeText("[{\"id\":\"A\"}]")
        fixtures.resolve("segment_cases.json").writeText("[]")
        val out = tmp.resolve("out")

        runFixtures(fixtures, out)

        assertEquals("[]", out.resolve("export_actual.json").readText().trim())
        assertEquals("[]", out.resolve("segment_actual.json").readText().trim())
    }

    @Test
    fun runFixtures_outDirMissing_isCreatedIncludingParents(@TempDir tmp: Path) {
        val fixtures = Files.createDirectory(tmp.resolve("fixtures"))
        fixtures.resolve("export_cases.json").writeText("[]")
        fixtures.resolve("segment_cases.json").writeText("[]")
        val out = tmp.resolve("a").resolve("b")

        runFixtures(fixtures, out)

        assertEquals(true, Files.isRegularFile(out.resolve("export_actual.json")))
    }

    @Test
    fun runFixtures_inputFileMissing_failsInsteadOfWritingOutput(@TempDir tmp: Path) {
        val fixtures = Files.createDirectory(tmp.resolve("fixtures"))
        fixtures.resolve("export_cases.json").writeText("[]")
        val out = tmp.resolve("out")

        assertThrows(java.nio.file.NoSuchFileException::class.java) { runFixtures(fixtures, out) }
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
