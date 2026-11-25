package io.github.ooscam.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class Mp4ProbeTest {
    private fun box(type: String, payload: Int): ByteArray {
        val buf = ByteBuffer.allocate(8 + payload)
        buf.putInt(8 + payload)
        buf.put(type.toByteArray(Charsets.US_ASCII))
        return buf.array()
    }

    private fun largeBox(type: String, payload: Int): ByteArray {
        val buf = ByteBuffer.allocate(16 + payload)
        buf.putInt(1)
        buf.put(type.toByteArray(Charsets.US_ASCII))
        buf.putLong(16L + payload)
        return buf.array()
    }

    private fun file(vararg boxes: ByteArray): ByteArraySource {
        val out = ByteArrayOutputStream()
        boxes.forEach { out.write(it) }
        return ByteArraySource(out.toByteArray())
    }

    @Test
    fun finalizedFile_withMoovAfterMdat_isFinalized() =
        assertTrue(Mp4Probe.isFinalized(file(box("ftyp", 16), box("mdat", 100), box("moov", 40))))

    @Test
    fun recordingCutOff_withoutMoov_isDamaged() =
        assertFalse(Mp4Probe.isFinalized(file(box("ftyp", 16), box("mdat", 100))))

    @Test
    fun largesizeMdat_isSkippedToReachMoov() =
        assertTrue(Mp4Probe.isFinalized(file(box("ftyp", 16), largeBox("mdat", 64), box("moov", 8))))

    @Test
    fun mdatWithSizeZero_runsToEndOfFile_soNoMoov() {
        val mdat = box("mdat", 20).also { ByteBuffer.wrap(it).putInt(0, 0) }
        assertFalse(Mp4Probe.isFinalized(file(box("ftyp", 16), mdat)))
    }

    @Test
    fun truncatedBoxDeclaringMoreThanTheFile_isDamaged() {
        val mdat = box("mdat", 20).also { ByteBuffer.wrap(it).putInt(0, 10_000) }
        assertFalse(Mp4Probe.isFinalized(file(box("ftyp", 16), mdat)))
    }

    @Test
    fun moovThatIsCutShort_isDamaged() {
        val moov = box("moov", 40)
        val cut = moov.copyOf(20)
        assertFalse(Mp4Probe.isFinalized(file(box("ftyp", 16), box("mdat", 10), cut)))
    }

    @Test
    fun emptyOrGarbageFile_isDamaged() {
        assertFalse(Mp4Probe.isFinalized(ByteArraySource(ByteArray(0))))
        assertFalse(Mp4Probe.isFinalized(ByteArraySource(ByteArray(5) { 7 })))
        val bad = box("ftyp", 4).also { ByteBuffer.wrap(it).putInt(0, 3) }
        assertFalse(Mp4Probe.isFinalized(file(bad, box("moov", 4))))
    }
}
