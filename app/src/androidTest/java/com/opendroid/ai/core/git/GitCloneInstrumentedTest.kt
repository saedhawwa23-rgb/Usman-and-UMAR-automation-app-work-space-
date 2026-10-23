package com.opendroid.ai.core.git

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class GitCloneInstrumentedTest {
    @Test
    fun jgitFullAndDepthOneCloneRunOnAndroidRuntime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val base = Files.createTempDirectory(context.cacheDir.toPath(), "git-clone-instrumented-").toFile()
        var source: Git? = null
        try {
            val sourceDir = File(base, "source")
            source = Git.init().setDirectory(sourceDir).setInitialBranch("main").call()
            val author = PersonIdent("Android Clone Test", "android-clone@example.invalid")
            repeat(3) { index ->
                File(sourceDir, "README.txt").writeText("android-commit-${index + 1}\n")
                source.add().addFilepattern("README.txt").call()
                source.commit().setMessage("android commit ${index + 1}").setAuthor(author).setCommitter(author).call()
            }
            val remote = sourceDir.toURI().toString()
            val workspace = File(base, "workspace")

            val full = GitCloneEngine(workspace).clone(
                remoteUrl = remote,
                destinationRelativePath = "projects/android/full",
                expectedBranch = "main",
                mode = GitCloneMode.FULL_HISTORY
            )
            Git.open(File(workspace, "projects/android/full")).use { cloned ->
                assertEquals(3, cloned.log().call().count())
                assertTrue(cloned.repository.exactRef("refs/remotes/origin/main") != null)
                assertEquals("main", full.branch)
            }

            val shallow = GitCloneEngine(workspace).clone(
                remoteUrl = remote,
                destinationRelativePath = "projects/android/shallow",
                expectedBranch = "main",
                mode = GitCloneMode.SHALLOW_DEPTH_1
            )
            Git.open(File(workspace, "projects/android/shallow")).use { cloned ->
                assertEquals(1, cloned.log().call().count())
                assertTrue(File(cloned.repository.directory, "shallow").isFile)
                assertEquals("main", shallow.branch)
            }
        } finally {
            source?.close()
            base.deleteRecursively()
        }
    }
}
