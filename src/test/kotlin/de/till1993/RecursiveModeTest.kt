package de.till1993

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecursiveModeTest {

    @Test
    fun `non recursive run leaves nested files untouched`(@TempDir tempDir: Path) {
        // given
        val session = tempDir.subDir("non-recursive")
        val nestedDir = session.subDir("nested")
        val nestedLonely = nestedDir.sampleFile("nestedLonely.ARW")
        val quarantined = session.quarantinedFile("nested/nestedLonely.ARW")

        // when
        runCleanup(session)

        // then
        assertTrue(nestedLonely.exists(), "Non-recursive run should leave nested files untouched")
        assertFalse(
            quarantined.exists(),
            "Non-recursive run should not move nested files into quarantine"
        )
    }

    @Test
    fun `recursive run processes nested directories`(@TempDir tempDir: Path) {
        // given
        val session = tempDir.subDir("recursive")
        val nestedDir = session.subDir("nested")
        val nestedLonely = nestedDir.sampleFile("nestedLonely.ARW")
        val quarantined = session.quarantinedFile("nested/nestedLonely.ARW")

        // when
        runCleanup(session, "--recursive")

        // then
        assertFalse(nestedLonely.exists(), "Recursive run should move nested unmatched RAW files")
        assertTrue(
            quarantined.exists(),
            "Recursive run should preserve relative paths inside quarantine"
        )
    }

    @Test
    fun `jpg in parent directory does not protect nested raw`(@TempDir tempDir: Path) {
        // given
        val session = tempDir.subDir("per-directory")
        session.sampleFile("shared.JPG")
        val childDir = session.subDir("child")
        val childRaw = childDir.sampleFile("shared.ARW")

        // when
        runCleanup(session, "-r")

        // then
        assertFalse(
            childRaw.exists(),
            "Matching must stay directory-local even when recursion is enabled"
        )
        assertTrue(
            session.quarantinedFile("child/shared.ARW").exists(),
            "Nested unmatched RAW files should still be quarantined with their relative path"
        )
    }
}
