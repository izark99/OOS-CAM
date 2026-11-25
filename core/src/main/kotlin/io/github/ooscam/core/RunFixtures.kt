package io.github.ooscam.core

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

private const val EXPORT_IN = "export_cases.json"
private const val SEGMENT_IN = "segment_cases.json"
private const val EXPORT_OUT = "export_actual.json"
private const val SEGMENT_OUT = "segment_actual.json"

/** File shape of one export case: a project plus a free-text `rule` note that the planner ignores. */
@Serializable
private data class ExportCaseFile(
    val id: String,
    val rule: String,
    val muted: Boolean,
    val exportHeight: Int,
    val clips: List<ProjectClip>,
    val speedRanges: List<SpeedRange>,
) {
    fun toProject() = EditProject(id, muted, exportHeight, clips, speedRanges)
}

/** File shape of one segment case: a recording plus a free-text `rule` note that the planner ignores. */
@Serializable
private data class SegmentCaseFile(
    val id: String,
    val rule: String,
    @Serializable(with = LocalDateTimeSerializer::class) val startLocal: LocalDateTime,
    val segmentMinutes: Int,
    val target: StorageTarget,
    val quality: Quality,
    val audio: Boolean,
    val stopAtMs: Long?,
    val freeSpaceSamples: List<FreeSpaceSample>,
) {
    fun toRecording() =
        RecordingInput(id, startLocal, segmentMinutes, target, quality, audio, stopAtMs, freeSpaceSamples)
}

/** Splits the two command-line paths: fixtures directory and output directory. */
fun parseArgs(args: Array<String>): Pair<Path, Path> {
    require(args.size == 2) { "usage: runFixtures <fixturesDir> <outDir>" }
    return Path.of(args[0]) to Path.of(args[1])
}

/**
 * Reads both case files, runs the planners and writes `export_actual.json` and
 * `segment_actual.json`. Both inputs are decoded before anything is written.
 */
fun runFixtures(fixturesDir: Path, outDir: Path) {
    val exportCases = PlannerJson.decodeFromString(ListSerializer(ExportCaseFile.serializer()), fixturesDir.resolve(EXPORT_IN).readText())
    val segmentCases = PlannerJson.decodeFromString(ListSerializer(SegmentCaseFile.serializer()), fixturesDir.resolve(SEGMENT_IN).readText())

    val exportPlans = exportCases.map { planExport(it.toProject()) }
    val segmentPlans = segmentCases.map { planSegments(it.toRecording()) }

    Files.createDirectories(outDir)
    outDir.resolve(EXPORT_OUT).writeText(PlannerJson.encodeToString(ListSerializer(ExportPlan.serializer()), exportPlans) + "\n")
    outDir.resolve(SEGMENT_OUT).writeText(PlannerJson.encodeToString(ListSerializer(SegmentPlan.serializer()), segmentPlans) + "\n")
}

fun main(args: Array<String>) {
    val (fixturesDir, outDir) = parseArgs(args)
    runFixtures(fixturesDir, outDir)
}
