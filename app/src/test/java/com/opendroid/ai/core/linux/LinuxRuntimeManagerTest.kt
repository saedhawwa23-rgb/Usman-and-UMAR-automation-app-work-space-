package com.opendroid.ai.core.linux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxRuntimeManagerTest {
    @Test
    fun `archive paths cannot escape app-private rootfs`() {
        assertTrue(SafeArchivePath.isSafe("bin/sh"))
        assertTrue(SafeArchivePath.isSafe("usr/lib/ld-musl-armhf.so.1"))
        assertFalse(SafeArchivePath.isSafe("../outside"))
        assertFalse(SafeArchivePath.isSafe("etc/../../outside"))
        assertFalse(SafeArchivePath.isSafe("/absolute/path"))
        assertFalse(SafeArchivePath.isSafe("etc/secret\u0000file"))
    }

    @Test
    fun `runtime pins ARMv7 Alpine and link2symlink PRoot`() {
        assertTrue(LinuxRuntimeManager.ROOTFS_URL.contains("armv7"))
        assertTrue(LinuxRuntimeManager.ROOTFS_SHA256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(LinuxRuntimeManager.PROOT_COMMIT.matches(Regex("[0-9a-f]{40}")))
        assertTrue(LinuxRuntimeManager.PROOT_REQUIRED_OPTION == "--link2symlink")
    }
}
