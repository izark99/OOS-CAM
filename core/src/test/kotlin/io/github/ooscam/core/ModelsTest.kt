package io.github.ooscam.core

import java.math.BigDecimal
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ModelsTest {
    private val projectJson = """
        {"id":"P1","muted":true,"exportHeight":1080,
         "clips":[{"order":0,"sourceUri":"u://a","sourceDurationMs":9000,"sourceHeight":720,"inMs":100,"outMs":900}],
         "speedRanges":[{"clipOrder":0,"startMs":200,"endMs":300,"speed":1.5}]}
    """.trimIndent()

    @Test
    fun editProject_decodesSpeedAsExactDecimal() {
        val p = PlannerJson.decodeFromString(EditProject.serializer(), projectJson)
        assertEquals(BigDecimal("1.5"), p.speedRanges.single().speed)
        assertEquals(true, p.muted)
        assertEquals(720, p.clips.single().sourceHeight)
    }

    @Test
    fun editProject_integerSpeedLiteral_decodes() {
        val p = PlannerJson.decodeFromString(EditProject.serializer(), projectJson.replace("1.5", "2"))
        assertEquals(0, BigDecimal(2).compareTo(p.speedRanges.single().speed))
    }

    @Test
    fun editProject_unknownKey_isRejected() {
        assertThrows(SerializationException::class.java) {
            PlannerJson.decodeFromString(EditProject.serializer(), projectJson.replace("\"muted\"", "\"mutd\":1,\"muted\""))
        }
    }

    @Test
    fun exportPlan_writesNullDurationAndStringSpeed() {
        val plan = ExportPlan(
            id = "P1", ok = false, errors = listOf("E6"), warnings = emptyList(),
            trimmedSourceMs = 499, outputDurationMs = null, parts = emptyList(),
            audio = true, exportHeight = 1080,
        )
        assertEquals(
            """{"id":"P1","ok":false,"errors":["E6"],"warnings":[],"trimmedSourceMs":499,"outputDurationMs":null,"parts":[],"audio":true,"exportHeight":1080}""",
            PlannerJson.encodeToString(ExportPlan.serializer(), plan),
        )
        val part = PlanPart(clipOrder = 1, srcStartMs = 5, srcEndMs = 9, speed = "1.5", outStartMs = 0, outDurationMs = 3)
        assertEquals(
            """{"clipOrder":1,"srcStartMs":5,"srcEndMs":9,"speed":"1.5","outStartMs":0,"outDurationMs":3}""",
            PlannerJson.encodeToString(PlanPart.serializer(), part),
        )
    }

    @Test
    fun recordingInput_decodesEnumsAndOptionalStop() {
        val json = """
            {"id":"R1","startLocal":"2030-01-02T03:04:05","segmentMinutes":10,"target":"sd","quality":"2160p30",
             "audio":false,"stopAtMs":null,"freeSpaceSamples":[{"tMs":0,"freeMb":900}]}
        """.trimIndent()
        val r = PlannerJson.decodeFromString(RecordingInput.serializer(), json)
        assertEquals(StorageTarget.SD, r.target)
        assertEquals(Quality.Q2160P30, r.quality)
        assertEquals(null, r.stopAtMs)
        assertEquals(900L, r.freeSpaceSamples.single().freeMb)
    }

    @Test
    fun segmentPlan_writesStopReasonAsLowerSnake() {
        val plan = SegmentPlan(
            id = "R1", stopReason = StopReason.LOW_STORAGE, endMs = 0, bytesPerMs = 1016, segments = emptyList(),
        )
        assertEquals(
            """{"id":"R1","stopReason":"low_storage","endMs":0,"bytesPerMs":1016,"segments":[]}""",
            PlannerJson.encodeToString(SegmentPlan.serializer(), plan),
        )
    }
}
