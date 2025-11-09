package de.till1993

import de.till1993.core.CleanupConfig
import de.till1993.core.CleanupEvent
import de.till1993.core.CleanupReporter
import de.till1993.core.CleanupRunner
import de.till1993.core.HandlingMode
import org.junit.jupiter.api.Test
import kotlin.io.path.Path as pathOf
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CleanupRunnerTest {

    @Test
    fun `quarantine run requests move via filesystem`() {
        // given
        val imageDir = pathOf("C:/images/session")
        val pairedRaw = imageDir.resolve("paired.ARW")
        val pairedJpg = imageDir.resolve("paired.JPG")
        val lonely = imageDir.resolve("lonely.ARW")

        val fakeFs = FakeFileSystem()
            .stubDirectory(imageDir, pairedRaw, pairedJpg, lonely)
        val console = RecordingConsole()
        val reporter = RecordingCleanupReporter()
        val runner = CleanupRunner(console, fakeFs, reporter)

        // when
        runner.run(CleanupConfig(imageDir, recursive = false, dryRun = false, mode = HandlingMode.QUARANTINE))

        // then
        assertTrue(fakeFs.createdDirs.any { it.toString().endsWith("_arw_quarantine") })
        val move = fakeFs.moved.single()
        assertEquals(lonely, move.first)
        assertTrue(move.second.toString().contains("_arw_quarantine"))
        assertTrue(reporter.events.any { it is CleanupEvent.MoveSucceeded && it.source == lonely })
    }

    @Test
    fun `dry run logs intended actions without filesystem writes`() {
        // given
        val imageDir = pathOf("C:/images/dry-run")
        val lonely = imageDir.resolve("lonely.ARW")

        val fakeFs = FakeFileSystem()
            .stubDirectory(imageDir, lonely)
        val console = RecordingConsole()
        val reporter = RecordingCleanupReporter()
        val runner = CleanupRunner(console, fakeFs, reporter)

        // when
        runner.run(CleanupConfig(imageDir, recursive = false, dryRun = true, mode = HandlingMode.QUARANTINE))

        // then
        assertTrue(
            reporter.events.any { it is CleanupEvent.DryRunMove && it.source == lonely },
            "Dry run should describe intended move"
        )
        assertTrue(fakeFs.moved.isEmpty(), "Dry run must not perform moves")
        assertTrue(fakeFs.createdDirs.isEmpty(), "Dry run must not create directories")
    }

    @Test
    fun `quarantine errors are reported without aborting other files`() {
        // given
        val imageDir = pathOf("C:/images/quarantine-error")
        val first = imageDir.resolve("first.ARW")
        val second = imageDir.resolve("second.ARW")

        val fakeFs = FakeFileSystem()
            .stubDirectory(imageDir, first, second)
        val console = RecordingConsole()
        val reporter = RecordingCleanupReporter()
        val runner = CleanupRunner(console, fakeFs, reporter)

        fakeFs.failMoveFor(first, throwable = IllegalStateException("disk full"))

        // when
        runner.run(CleanupConfig(imageDir, recursive = false, dryRun = false, mode = HandlingMode.QUARANTINE))

        // then
        assertTrue(
            reporter.events.any { it is CleanupEvent.MoveFailed && it.source == first && it.reason.contains("disk full") },
            "Errors should be reported"
        )
        assertTrue(
            fakeFs.moved.any { it.first == second },
            "Failure on first file should not prevent processing the second"
        )
    }

    @Test
    fun `recursive delete run deletes nested files without creating quarantine`() {
        // given
        val imageDir = pathOf("C:/images/session")
        val nested = imageDir.resolve("nested").resolve("lonely.ARW")
        val nestedJpg = imageDir.resolve("nested").resolve("paired.JPG")

        val fakeFs = FakeFileSystem()
            .stubDirectory(imageDir)
            .stubDirectory(imageDir.resolve("nested"), nested, nestedJpg)
        val console = RecordingConsole()
        val reporter = RecordingCleanupReporter()
        val runner = CleanupRunner(console, fakeFs, reporter)

        // when
        runner.run(CleanupConfig(imageDir, recursive = true, dryRun = false, mode = HandlingMode.DELETE))

        // then
        assertEquals(listOf(nested), fakeFs.deleted, "Nested unmatched RAW should be deleted")
        assertTrue(fakeFs.createdDirs.isEmpty(), "Delete mode must not create quarantine directories")
        assertTrue(
            reporter.events.any { it is CleanupEvent.DeleteSucceeded && it.path == nested },
            "Reporter should record deletions"
        )
    }

    @Test
    fun `summary reporter receives aggregated stats`() {
        // given
        val imageDir = pathOf("C:/images/summary")
        val lonely = imageDir.resolve("lonely.ARW")
        val fakeFs = FakeFileSystem()
            .stubDirectory(imageDir, lonely)
        val console = RecordingConsole()
        val reporter = RecordingCleanupReporter()
        val runner = CleanupRunner(console, fakeFs, reporter)

        // when
        runner.run(CleanupConfig(imageDir, recursive = false, dryRun = false, mode = HandlingMode.QUARANTINE))

        // then
        val summary = reporter.events.filterIsInstance<CleanupEvent.Summary>().single().context
        assertEquals(1, summary.directoryCount)
        assertEquals(imageDir.resolve("_arw_quarantine"), summary.quarantineDir)
        assertEquals(1, summary.stats.totalArw)
        assertEquals(0, summary.stats.totalJpg)
        assertEquals(1, summary.stats.totalUnmatched)
        assertEquals(1, summary.stats.quarantined)
    }
}

private class RecordingCleanupReporter : CleanupReporter {
    val events = mutableListOf<CleanupEvent>()
    override fun publish(event: CleanupEvent) {
        events += event
    }
}
