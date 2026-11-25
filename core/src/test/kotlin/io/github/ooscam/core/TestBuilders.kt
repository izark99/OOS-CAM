package io.github.ooscam.core

import java.math.BigDecimal

fun clip(
    order: Int,
    durationMs: Long = 100_000,
    inMs: Long = 0,
    outMs: Long = durationMs,
    height: Int = 1080,
) = ProjectClip(order, "test://clip$order", durationMs, height, inMs, outMs)

fun range(clipOrder: Int, startMs: Long, endMs: Long, speed: String) =
    SpeedRange(clipOrder, startMs, endMs, BigDecimal(speed))

fun project(
    clips: List<ProjectClip>,
    ranges: List<SpeedRange> = emptyList(),
    muted: Boolean = false,
    height: Int = 1080,
) = EditProject("T", muted, height, clips, ranges)
