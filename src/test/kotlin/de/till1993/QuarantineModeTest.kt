package de.till1993

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuarantineModeTest {

    @Test
    fun `moves only unmatched RAW files`(@TempDir tempDir: Path) {
        // given
        val session = tempDir.subDir("session")
        val pairedRaw = session.sampleFile("paired.ARW")
        session.sampleFile("paired.JPG")
        val lonelyRaw = session.sampleFile("lonely.ARW")

        // when
        runCleanup(session)

        // then
        assertTrue(pairedRaw.exists(), "Paired RAW should remain in place")
        assertFalse(lonelyRaw.exists(), "Unmatched RAW should be moved out of the source directory")
        assertTrue(
            session.quarantinedFile("lonely.ARW").exists(),
            "Unmatched RAW should appear under the quarantine directory"
        )
    }

    @Test
    fun `jpg matching remains case insensitive`(@TempDir tempDir: Path) {
        // given
        val session = tempDir.subDir("case-match")
        val mixedCaseArw = session.sampleFile("MiXeD.ArW")
        session.sampleFile("mixed.JPG")

        // when
        runCleanup(session)

        // then
        assertTrue(
            mixedCaseArw.exists(),
            "RAW files with a JPG twin must remain even when extensions differ in case"
        )
        assertFalse(
            session.quarantinedFile("MiXeD.ArW").exists(),
            "Matched files must never be moved into the quarantine directory"
        )
    }

    @Test
    fun `existing quarantine contents stay untouched`(@TempDir tempDir: Path) {
        // given
        val session = tempDir.subDir("existing-quarantine")
        val quarantineDir = session.quarantineDir().ensureDir()
        val preexistingQuarantined = quarantineDir.sampleFile("alreadyQuarantined.ARW")
        val newLonely = session.sampleFile("freshLonely.ARW")

        // when
        runCleanup(session, "--recursive")

        // then
        assertTrue(
            preexistingQuarantined.exists(),
            "Files already under _arw_quarantine must never be processed again"
        )
        assertFalse(newLonely.exists(), "Fresh unmatched RAW files should still be moved")
        assertTrue(
            session.quarantinedFile("freshLonely.ARW").exists(),
            "New unmatched RAW files should be moved into the quarantine directory"
        )
    }
}
