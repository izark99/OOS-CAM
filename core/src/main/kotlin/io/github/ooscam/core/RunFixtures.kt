package io.github.ooscam.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

private const val EXPORT_IN = "export_cases.json"
private const val SEGMENT_IN = "segment_cases.json"
private const val EXPORT_OUT = "export_actual.json"
private const val SEGMENT_OUT = "segment_actual.json"

/** Splits the two command-line paths: fixtures directory and output directory. */
fun parseArgs(args: Array<String>): Pair<Path, Path> {
    require(args.size == 2) { "usage: runFixtures <fixturesDir> <outDir>" }
    return Path.of(args[0]) to Path.of(args[1])
}

/**
 * Wiring only. Reads both case files (they must exist and be JSON arrays) and writes
 * empty result arrays. Phase 1 replaces the empty arrays with planner output.
 */
fun runFixtures(fixturesDir: Path, outDir: Path) {
    val exportCases = Json.parseToJsonElement(fixturesDir.resolve(EXPORT_IN).readText())
    val segmentCases = Json.parseToJsonElement(fixturesDir.resolve(SEGMENT_IN).readText())
    require(exportCases is JsonArray) { "$EXPORT_IN must be a JSON array" }
    require(segmentCases is JsonArray) { "$SEGMENT_IN must be a JSON array" }

    Files.createDirectories(outDir)
    outDir.resolve(EXPORT_OUT).writeText(JsonArray(emptyList()).toString() + "\n")
    outDir.resolve(SEGMENT_OUT).writeText(JsonArray(emptyList()).toString() + "\n")
}

fun main(args: Array<String>) {
    val (fixturesDir, outDir) = parseArgs(args)
    runFixtures(fixturesDir, outDir)
}
