package io.github.ooscam.acceptance

import io.github.ooscam.core.ExportPlan
import io.github.ooscam.core.PlannerJson
import io.github.ooscam.editor.ClipSource
import io.github.ooscam.editor.EditorViewModel
import io.github.ooscam.export.CompositionMapper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.math.BigDecimal

/**
 * SPEC acceptance use case on the JVM: builds fixture X01 through the real editor view model, the
 * way a user does (pick clips, trim, add a speed range, mute, choose the height), and writes the
 * resulting plan to app/build/acceptance/X01_plan.json for `run_acceptance.py --suite all`.
 * Every number comes from the fixture file at test time.
 */
@RunWith(RobolectricTestRunner::class)
class AcceptanceUseCaseTest {
    private fun dir(property: String) = File(requireNotNull(System.getProperty(property)) { "Gradle must set -D$property" })

    private fun case(id: String): JsonObject {
        val file = File(dir("ooscam.fixturesDir"), "export_cases.json")
        return Json.parseToJsonElement(file.readText()).jsonArray.map { it.jsonObject }.single { it["id"]!!.jsonPrimitive.content == id }
    }

    @Test
    fun x01_builtThroughTheEditor_givesThePlanAndAComposition() {
        val case = case("X01")
        val clips = case["clips"]!!.jsonArray.map { it.jsonObject }.sortedBy { it["order"]!!.jsonPrimitive.int }
        val vm = EditorViewModel(projectId = case["id"]!!.jsonPrimitive.content)

        // Library: select the recorded clips, "Edit selected".
        vm.addClips(
            clips.map {
                val uri = it["sourceUri"]!!.jsonPrimitive.content
                ClipSource(uri, uri.substringAfterLast('/'), it["sourceDurationMs"]!!.jsonPrimitive.long, it["sourceHeight"]!!.jsonPrimitive.int)
            },
        )
        // Editor: trim each clip, add the speed ranges, mute, export height.
        clips.forEach { vm.setTrim(it["order"]!!.jsonPrimitive.int, it["inMs"]!!.jsonPrimitive.long, it["outMs"]!!.jsonPrimitive.long) }
        case["speedRanges"]!!.jsonArray.map { it.jsonObject }.forEach {
            vm.addSpeedRange(
                it["clipOrder"]!!.jsonPrimitive.int,
                it["startMs"]!!.jsonPrimitive.long,
                it["endMs"]!!.jsonPrimitive.long,
                BigDecimal(it["speed"]!!.jsonPrimitive.content),
            )
        }
        vm.setMuted(case["muted"]!!.jsonPrimitive.boolean)
        vm.setExportHeight(case["exportHeight"]!!.jsonPrimitive.int)

        val state = vm.state.value
        val plan: ExportPlan = state.plan
        assertTrue("editor allows export: ${state.messages}", state.canExport)

        // The export path accepts exactly this plan: one Composition item per plan part.
        val composition = CompositionMapper.map(plan, CompositionMapper.sourceUris(state.project))
        assertEquals(plan.parts.size, composition.sequences.single().editedMediaItems.size)

        val out = dir("ooscam.acceptanceOut").also { it.mkdirs() }
        File(out, "${plan.id}_plan.json").writeText(PlannerJson.encodeToString(ExportPlan.serializer(), plan))
    }
}
