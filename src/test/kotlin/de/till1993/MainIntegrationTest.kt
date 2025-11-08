package de.till1993

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.assertTrue

class MainIntegrationTest {

    @Test
    fun `main quarantines unmatched files`(@TempDir tempDir: Path) {
        val session = tempDir.subDir("main-quarantine")
        session.sampleFile("lonely.ARW")

        val output = captureStdout {
            main(arrayOf(session.toString()))
        }

        assertTrue(output.contains("Using image directory:"), "CLI output should include header")
        assertTrue(
            session.quarantinedFile("lonely.ARW").exists(),
            "Main entry point should move unmatched files into quarantine"
        )
    }

    @Test
    fun `main delete mode removes files`(@TempDir tempDir: Path) {
        val session = tempDir.subDir("main-delete")
        val lonely = session.sampleFile("lonely.ARW")

        val output = captureStdout {
            main(arrayOf("--delete", session.toString()))
        }

        assertTrue(output.contains("Delete mode"), "CLI output should mention delete mode")
        assertTrue(output.contains("Deleted:"), "Delete operations should be logged")
        assertTrue(!lonely.exists(), "Delete mode should remove files from disk")
    }

    @Test
    fun `recursive short-flag run processes nested directories`(@TempDir tempDir: Path) {
        val session = tempDir.subDir("main-recursive")
        val nestedDir = session.subDir("nested")
        val nestedLonely = nestedDir.sampleFile("lonely.ARW")

        captureStdout {
            main(arrayOf("-r", session.toString()))
        }

        assertTrue(
            session.quarantinedFile("nested/lonely.ARW").exists(),
            "Recursive run should preserve relative paths when quarantining"
        )
        assertTrue(!nestedLonely.exists(), "Nested file should be handled")
    }

    @Test
    fun `short delete flag deletes files`(@TempDir tempDir: Path) {
        val session = tempDir.subDir("main-short-delete")
        val lonely = session.sampleFile("lonely.ARW")

        val output = captureStdout {
            main(arrayOf("-d", session.toString()))
        }

        assertTrue(output.contains("Delete mode"), "Short flag should enable delete mode")
        assertTrue(output.contains("Deleted:"), "Deletion should be logged")
        assertTrue(!lonely.exists(), "Short delete flag should remove files")
    }
}
