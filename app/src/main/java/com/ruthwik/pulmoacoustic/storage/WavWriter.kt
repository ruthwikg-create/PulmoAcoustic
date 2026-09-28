package com.ruthwik.pulmoacoustic.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavWriter {
    fun writeMonoPcm16(file: File, pcm: ShortArray, sampleRate: Int) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            val dataSize = pcm.size * 2
            val header = ByteArray(44)
            fun leInt(offset: Int, value: Int) {
                ByteBuffer.wrap(header, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
            }
            fun leShort(offset: Int, value: Int) {
                ByteBuffer.wrap(header, offset, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort())
            }
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            leInt(4, 36 + dataSize)
            header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
            leInt(16, 16); leShort(20, 1); leShort(22, 1); leInt(24, sampleRate); leInt(28, sampleRate * 2); leShort(32, 2); leShort(34, 16)
            header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
            leInt(40, dataSize)
            out.write(header)
            val buffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            pcm.forEach { buffer.putShort(it) }
            out.write(buffer.array())
        }
    }
}