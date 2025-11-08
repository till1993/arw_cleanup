package de.till1993

import de.till1993.core.CleanupConfig
import de.till1993.core.CleanupEvent
import de.till1993.core.CleanupStats
import de.till1993.core.ConsoleCleanupReporter
import de.till1993.core.HandlingMode
import de.till1993.core.SummaryContext
import kotlin.io.path.Path as pathOf
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class CleanupReporterTest {

    @Test
    fun `console reporter prints summary totals`() {
        // given
        val console = RecordingConsole()
        val reporter = ConsoleCleanupReporter(console)
        val stats = CleanupStats(totalArw = 5, totalJpg = 2, totalUnmatched = 3, deleted = 3)
        val context = SummaryContext(
            directoryCount = 2,
            stats = stats,
            config = CleanupConfig(
                imageDir = pathOf("C:/photos"),
                recursive = false,
                dryRun = false,
                mode = HandlingMode.DELETE
            ),
            quarantineDir = pathOf("C:/photos/_arw_quarantine")
        )

        // when
        reporter.publish(CleanupEvent.Summary(context))

        // then
        assertTrue(console.infoMessages.any { it.contains("Found 5 ARW file(s)") })
        assertTrue(console.infoMessages.any { it.contains("Found 2 JPG file(s)") })
        assertTrue(console.infoMessages.any { it.contains("Found 3 ARW file(s) to delete") })
        assertTrue(console.infoMessages.any { it.contains("Deleted 3 file(s).") })
    }
}
