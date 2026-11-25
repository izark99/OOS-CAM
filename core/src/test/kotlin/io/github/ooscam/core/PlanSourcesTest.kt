package io.github.ooscam.core

import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PlanSourcesTest {
    private val project = EditProject(
        "p", muted = false, exportHeight = 1080,
        clips = listOf(ProjectClip(1, "content://b", 5_000, 1080, 0, 5_000), ProjectClip(0, "content://a", 5_000, 1080, 0, 5_000)),
        speedRanges = emptyList(),
    )

    @Test
    fun plan_carriesTheSourceUriOfEveryClipByOrder() {
        assertEquals(mapOf(0 to "content://a", 1 to "content://b"), planExport(project).sourceUris)
    }

    @Test
    fun sourceUris_areNotPartOfTheJsonContract() {
        val json = PlannerJson.encodeToJsonElement(ExportPlan.serializer(), planExport(project)).jsonObject
        assertFalse("sourceUris" in json.keys)
        assertEquals(setOf("id", "ok", "errors", "warnings", "trimmedSourceMs", "outputDurationMs", "parts", "audio", "exportHeight"), json.keys)
    }
}
