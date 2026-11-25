package io.github.ooscam.library

/** Random access to the bytes of a file. */
interface ByteSource {
    val size: Long

    /** Reads up to [length] bytes at [position]; fewer at end of file. */
    fun read(position: Long, length: Int): ByteArray
}

class ByteArraySource(private val bytes: ByteArray) : ByteSource {
    override val size: Long get() = bytes.size.toLong()

    override fun read(position: Long, length: Int): ByteArray {
        if (position >= bytes.size) return ByteArray(0)
        val end = minOf(bytes.size.toLong(), position + length).toInt()
        return bytes.copyOfRange(position.toInt(), end)
    }
}

/** SPEC data rule 5: an MP4 is valid only when its moov atom was written. */
object Mp4Probe {
    private const val HEADER = 8
    private const val LARGE_HEADER = 16

    /** Walks the top-level boxes; true only when a complete moov box is found. */
    fun isFinalized(source: ByteSource): Boolean {
        val fileSize = source.size
        var pos = 0L
        while (pos + HEADER <= fileSize) {
            val header = source.read(pos, LARGE_HEADER)
            if (header.size < HEADER) return false
            val size32 = readUInt(header, 0)
            val type = String(header, 4, 4, Charsets.US_ASCII)
            val (boxSize, headerLength) = when (size32) {
                0L -> (fileSize - pos) to HEADER
                1L -> {
                    if (header.size < LARGE_HEADER) return false
                    (readUInt(header, 8) shl 32 or readUInt(header, 12)) to LARGE_HEADER
                }
                else -> size32 to HEADER
            }
            if (boxSize < headerLength || boxSize > fileSize - pos) return false
            if (type == "moov") return true
            pos += boxSize
        }
        return false
    }

    private fun readUInt(bytes: ByteArray, at: Int): Long =
        (bytes[at].toLong() and 0xFF shl 24) or (bytes[at + 1].toLong() and 0xFF shl 16) or
            (bytes[at + 2].toLong() and 0xFF shl 8) or (bytes[at + 3].toLong() and 0xFF)
}
