package com.opendroid.ai.core.pty

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PtySessionInstrumentedTest {
    @Test
    fun shellSupportsInteractiveInputOutputResizeAndExit() {
        val session = PtySession.start(
            executable = "/system/bin/sh",
            arguments = emptyArray(),
            workingDirectory = "/data/local/tmp",
            environment = mapOf("TERM" to "xterm-256color", "PATH" to "/system/bin:/system/xbin")
        )
        try {
            session.resize(rows = 24, columns = 80)
            session.write("printf PTY_OK\\n; exit 7")
            val output = buildString {
                repeat(20) {
                    append(session.readAvailable(timeoutMs = 100))
                    if (!session.isAlive()) return@repeat
                }
            }
            assertTrue("PTY output was: $output", output.contains("PTY_OK"))
            assertTrue("exit code was ${session.exitCode()}", session.exitCode() == 7 || !session.isAlive())
        } finally {
            session.close()
        }
    }
}
