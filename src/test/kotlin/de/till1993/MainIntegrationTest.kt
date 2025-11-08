package de.till1993

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainIntegrationTest {

    @Test
    fun `main quarantines unmatched files`(@TempDir tempDir: Path) {
        // given
        val session = tempDir.subDir("main-quarantine")
        session.sampleFile("lonely.ARW")
        val args = arrayOf(session.toString())

        // when
        val output = captureStdout {
            main(args)
        }

        // then
        assertTrue(output.contains("Using image directory:"), "CLI output should include header")
        assertTrue(
            session.quarantinedFile("lonely.ARW").exists(),
            "Main entry point should move unmatched files into quarantine"
        )
    }

    @Test
    fun `main delete mode removes files`(@TempDir tempDir: Path) {
        // given
        val session = tempDir.subDir("main-delete")
        val lonely = session.sampleFile("lonely.ARW")
        val args = arrayOf("--delete", session.toString())

        // when
        val output = captureStdout {
            main(args)
        }

        // then
        assertTrue(output.contains("Delete mode"), "CLI output should mention delete mode")
        assertTrue(output.contains("Deleted:"), "Delete operations should be logged")
        assertFalse(lonely.exists(), "Delete mode should remove files from disk")
    }

    @Test
    fun `recursive short-flag run processes nested directories`(@TempDir tempDir: Path) {
        // given
        val session = tempDir.subDir("main-recursive")
        val nestedDir = session.subDir("nested")
        val nestedLonely = nestedDir.sampleFile("lonely.ARW")
        val args = arrayOf("-r", session.toString())

        // when
        captureStdout {
            main(args)
        }

        // then
        assertTrue(
            session.quarantinedFile("nested/lonely.ARW").exists(),
            "Recursive run should preserve relative paths when quarantining"
        )
        assertFalse(nestedLonely.exists(), "Nested file should be handled")
    }

    @Test
    fun `short delete flag deletes files`(@TempDir tempDir: Path) {
        // given
        val session = tempDir.subDir("main-short-delete")
        val lonely = session.sampleFile("lonely.ARW")
        val args = arrayOf("-d", session.toString())

        // when
        val output = captureStdout {
            main(args)
        }

        // then
        assertTrue(output.contains("Delete mode"), "Short flag should enable delete mode")
        assertTrue(output.contains("Deleted:"), "Deletion should be logged")
        assertFalse(lonely.exists(), "Short delete flag should remove files")
    }
}
