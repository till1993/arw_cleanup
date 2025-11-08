package de.till1993

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DryRunModeTest {

    @Test
    fun `dry run leaves filesystem untouched`(@TempDir tempDir: Path) {
        val session = tempDir.subDir("dry-run")
        val lonelyArw = session.sampleFile("lonely.ARW")

        runCleanup(session, "--dry-run")

        assertTrue(lonelyArw.exists(), "Dry run must not delete or move files")
        assertFalse(
            session.quarantineDir().exists(),
            "Dry run must not create quarantine directories"
        )
    }

    @Test
    fun `short dry run flag also preserves files`(@TempDir tempDir: Path) {
        val session = tempDir.subDir("dry-run-short")
        val lonelyArw = session.sampleFile("lonely.ARW")

        runCleanup(session, "-n")

        assertTrue(lonelyArw.exists(), "Short flag -n must not delete or move files")
        assertFalse(
            session.quarantineDir().exists(),
            "Dry run short flag must not create quarantine directories"
        )
    }
}
