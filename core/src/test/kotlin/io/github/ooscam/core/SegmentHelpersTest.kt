package io.github.ooscam.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SegmentHelpersTest {
    @Test
    fun segmentCapMs_deviceTarget_isTheTimeCapOnly() {
        assertEquals(60 * 60_000L, segmentCapMs(60, StorageTarget.DEVICE, Quality.Q2160P30, audio = true))
    }

    @Test
    fun segmentCapMs_sdTarget_isTheEarlierOfTimeAndSizeCapRoundedUp() {
        val bytesPerMs = 45 * 125L + 16L
        val sizeCap = (3_900_000_000L + bytesPerMs - 1) / bytesPerMs
        assertEquals(sizeCap, segmentCapMs(60, StorageTarget.SD, Quality.Q2160P30, audio = true))
        assertEquals(5 * 60_000L, segmentCapMs(5, StorageTarget.SD, Quality.Q720P30, audio = false))
    }

    @Test
    fun segmentFileName_padsIndexToTwoDigitsAndKeepsLongerOnes() {
        val start = LocalDateTime.of(2031, 2, 3, 4, 5, 6)
        assertEquals("OOSCam_20310203_040506_part07.mp4", segmentFileName(start, 7))
        assertEquals("OOSCam_20310203_040506_part123.mp4", segmentFileName(start, 123))
    }

    @Test
    fun isLowStorage_isStrictlyBelow500Mb() {
        assertTrue(isLowStorage(499))
        assertFalse(isLowStorage(500))
    }

    @Test
    fun wireNames_matchTheSerialNames() {
        assertEquals("low_storage", StopReason.LOW_STORAGE.wireName)
        assertEquals("phone_call", StopReason.PHONE_CALL.wireName)
        assertEquals("permission_revoked", StopReason.PERMISSION_REVOKED.wireName)
        assertEquals("sd", StorageTarget.SD.wireName)
    }
}
