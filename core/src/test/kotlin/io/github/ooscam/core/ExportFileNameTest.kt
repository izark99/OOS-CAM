package io.github.ooscam.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ExportFileNameTest {
    @Test
    fun exportFileName_usesTheExportStartInLocalTime() {
        assertEquals("OOSCam_edit_20310203_040506.mp4", exportFileName(LocalDateTime.of(2031, 2, 3, 4, 5, 6)))
        assertEquals("OOSCam_edit_20291231_235959.mp4", exportFileName(LocalDateTime.of(2029, 12, 31, 23, 59, 59, 999_000_000)))
    }
}
