package de.till1993

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.assertFalse

class DeleteModeTest {

    @Test
    fun `delete mode removes unmatched RAW files`(@TempDir tempDir: Path) {
        val session = tempDir.subDir("delete-mode")
        val lonelyArw = session.sampleFile("lonely.ARW")

        runCleanup(session, "--delete")

        assertFalse(lonelyArw.exists(), "Delete mode should remove unmatched RAW files")
        assertFalse(
            session.quarantineDir().exists(),
            "Delete mode should not create a quarantine directory"
        )
    }

    @Test
    fun `recursive delete removes nested RAW files without creating quarantine`(@TempDir tempDir: Path) {
        val session = tempDir.subDir("delete-recursive")
        val nestedRaw = session.subDir("nested").sampleFile("nestedLonely.ARW")

        runCleanup(session, "-d", "-r")

        assertFalse(nestedRaw.exists(), "Recursive delete should remove nested unmatched RAW files")
        assertFalse(
            session.quarantineDir().exists(),
            "Delete mode should not create a quarantine directory even in recursive runs"
        )
    }
}
