package com.opendroid.ai.core.needle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NeedleNativeRuntimePackagingTest {
    private fun runtime(relativePath: String): ByteArray {
        val candidates = listOf(
            File("src/main/jniLibs/$relativePath"),
            File("app/src/main/jniLibs/$relativePath")
        )
        val file = candidates.firstOrNull(File::isFile)
            ?: error("Packaged native runtime not found: $relativePath")
        return file.readBytes().also { assertTrue("ELF file too short", it.size >= 20) }
    }

    private fun assertElf(bytes: ByteArray, elfClass: Int, machine: Int) {
        assertEquals(0x7f, bytes[0].toInt() and 0xff)
        assertEquals('E'.code, bytes[1].toInt() and 0xff)
        assertEquals('L'.code, bytes[2].toInt() and 0xff)
        assertEquals('F'.code, bytes[3].toInt() and 0xff)
        assertEquals(elfClass, bytes[4].toInt() and 0xff)
        assertEquals(1, bytes[5].toInt() and 0xff) // little-endian
        val eMachine = (bytes[18].toInt() and 0xff) or
            ((bytes[19].toInt() and 0xff) shl 8)
        assertEquals(machine, eMachine)
    }

    @Test
    fun `ARMv7 libc++ shared runtime is packaged`() {
        assertElf(runtime("armeabi-v7a/libc++_shared.so"), elfClass = 1, machine = 40)
    }

    @Test
    fun `ARM64 libc++ shared runtime is packaged`() {
        assertElf(runtime("arm64-v8a/libc++_shared.so"), elfClass = 2, machine = 183)
    }
}
