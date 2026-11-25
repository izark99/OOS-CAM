package io.github.ooscam.core

import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive

/** Strict JSON settings shared by readers and writers: unknown keys fail, nulls and defaults are written. */
@OptIn(ExperimentalSerializationApi::class)
val PlannerJson: Json = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
    explicitNulls = true
}

/** Speed read from the raw JSON number so 1.5 stays exactly 1.5. */
@OptIn(ExperimentalSerializationApi::class)
object SpeedSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Speed", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BigDecimal {
        val element = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive
        return BigDecimal(element.content)
    }

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.stripTrailingZeros().toPlainString()))
    }
}

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
}

// ---------------------------------------------------------------- export

@Serializable
data class ProjectClip(
    val order: Int,
    val sourceUri: String,
    val sourceDurationMs: Long,
    val sourceHeight: Int,
    val inMs: Long,
    val outMs: Long,
)

@Serializable
data class SpeedRange(
    val clipOrder: Int,
    val startMs: Long,
    val endMs: Long,
    @Serializable(with = SpeedSerializer::class) val speed: BigDecimal,
)

@Serializable
data class EditProject(
    val id: String,
    val muted: Boolean,
    val exportHeight: Int,
    val clips: List<ProjectClip>,
    val speedRanges: List<SpeedRange>,
)

@Serializable
data class PlanPart(
    val clipOrder: Int,
    val srcStartMs: Long,
    val srcEndMs: Long,
    val speed: String,
    val outStartMs: Long,
    val outDurationMs: Long,
)

@Serializable
data class ExportPlan(
    val id: String,
    val ok: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val trimmedSourceMs: Long,
    val outputDurationMs: Long?,
    val parts: List<PlanPart>,
    val audio: Boolean,
    val exportHeight: Int,
    /**
     * Source URI of each clip by clip order, so an exporter needs nothing but the plan.
     * Not part of the JSON contract (TEST_RULES shapes), hence @Transient.
     */
    @Transient val sourceUris: Map<Int, String> = emptyMap(),
)

// ---------------------------------------------------------------- segments

@Serializable
enum class StorageTarget {
    @SerialName("device") DEVICE,
    @SerialName("sd") SD,
}

@Serializable
enum class Quality(val videoMbps: Int) {
    @SerialName("720p30") Q720P30(8),
    @SerialName("1080p30") Q1080P30(16),
    @SerialName("2160p30") Q2160P30(45),
}

@Serializable
enum class StopReason {
    @SerialName("user") USER,
    @SerialName("low_storage") LOW_STORAGE,

    // Live capture only; planSegments never returns these.
    @SerialName("error") ERROR,
    @SerialName("thermal") THERMAL,
    @SerialName("permission_revoked") PERMISSION_REVOKED,
    @SerialName("phone_call") PHONE_CALL,
}

/** The JSON / database name of a stop reason, for example "low_storage". */
val StopReason.wireName: String get() = StopReason.serializer().descriptor.getElementName(ordinal)

/** The JSON / database name of a storage target: "device" or "sd". */
val StorageTarget.wireName: String get() = StorageTarget.serializer().descriptor.getElementName(ordinal)

@Serializable
data class FreeSpaceSample(val tMs: Long, val freeMb: Long)

@Serializable
data class RecordingInput(
    val id: String,
    @Serializable(with = LocalDateTimeSerializer::class) val startLocal: LocalDateTime,
    val segmentMinutes: Int,
    val target: StorageTarget,
    val quality: Quality,
    val audio: Boolean,
    val stopAtMs: Long?,
    val freeSpaceSamples: List<FreeSpaceSample>,
)

@Serializable
data class SegmentPart(
    val index: Int,
    val fileName: String,
    val startOffsetMs: Long,
    val durationMs: Long,
    val estimatedBytes: Long,
)

@Serializable
data class SegmentPlan(
    val id: String,
    val stopReason: StopReason,
    val endMs: Long,
    val bytesPerMs: Long,
    val segments: List<SegmentPart>,
)
