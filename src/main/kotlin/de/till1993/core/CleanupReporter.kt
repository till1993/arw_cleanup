package de.till1993.core

import de.till1993.cli.Console
import java.nio.file.Path

sealed interface CleanupEvent {
    data class RunStarted(
        val config: CleanupConfig,
        val quarantineDir: Path
    ) : CleanupEvent

    data class QuarantineCreationFailed(
        val quarantineDir: Path,
        val reason: String
    ) : CleanupEvent

    data class ScanCompleted(
        val regularFileCount: Int,
        val recursive: Boolean
    ) : CleanupEvent

    data class DirectoryStats(
        val directory: Path,
        val arwCount: Int,
        val jpgCount: Int,
        val unmatchedCount: Int
    ) : CleanupEvent

    data class DryRunMove(
        val source: Path,
        val target: Path
    ) : CleanupEvent

    data class DryRunDelete(
        val path: Path
    ) : CleanupEvent

    data class MoveSucceeded(
        val source: Path,
        val target: Path
    ) : CleanupEvent

    data class MoveFailed(
        val source: Path,
        val reason: String
    ) : CleanupEvent

    data class DeleteSucceeded(
        val path: Path
    ) : CleanupEvent

    data class DeleteSkippedMissing(
        val path: Path
    ) : CleanupEvent

    data class DeleteFailed(
        val path: Path,
        val reason: String
    ) : CleanupEvent

    data class Summary(val context: SummaryContext) : CleanupEvent
}

fun interface CleanupReporter {
    fun publish(event: CleanupEvent)
}

class ConsoleCleanupReporter(
    private val console: Console
) : CleanupReporter {

    override fun publish(event: CleanupEvent) {
        when (event) {
            is CleanupEvent.RunStarted -> announceRun(event)
            is CleanupEvent.ScanCompleted ->
                console.info(
                    "Scanned ${event.regularFileCount} regular file(s)" +
                        if (event.recursive) " (recursive)." else "."
                )

            is CleanupEvent.DirectoryStats ->
                console.info(
                    "Directory: ${event.directory.toAbsolutePath()} => ARW: ${event.arwCount}, " +
                        "JPG: ${event.jpgCount}, to handle: ${event.unmatchedCount}"
                )

            is CleanupEvent.DryRunMove ->
                console.info(
                    "Would move to quarantine: ${event.source.toAbsolutePath()} -> ${event.target.toAbsolutePath()}"
                )

            is CleanupEvent.DryRunDelete ->
                console.info("Would delete: ${event.path.toAbsolutePath()}")

            is CleanupEvent.MoveSucceeded ->
                console.info(
                    "Moved to quarantine: ${event.source.toAbsolutePath()} -> ${event.target.toAbsolutePath()}"
                )

            is CleanupEvent.MoveFailed ->
                console.error("Failed to move to quarantine: ${event.source.toAbsolutePath()}, reason: ${event.reason}")

            is CleanupEvent.DeleteSucceeded ->
                console.info("Deleted: ${event.path.toAbsolutePath()}")

            is CleanupEvent.DeleteSkippedMissing ->
                console.warn("Failed to delete: ${event.path.toAbsolutePath()} (file not found)")

            is CleanupEvent.DeleteFailed ->
                console.error("Failed to delete: ${event.path.toAbsolutePath()}, reason: ${event.reason}")

            is CleanupEvent.QuarantineCreationFailed ->
                console.error(
                    "Unable to create quarantine directory (${event.quarantineDir.toAbsolutePath()}): ${event.reason}"
                )

            is CleanupEvent.Summary ->
                summarize(event.context)
        }
    }

    private fun announceRun(event: CleanupEvent.RunStarted) {
        val config = event.config
        console.info("Using image directory: ${config.imageDir}")
        if (config.dryRun) console.info("Dry run enabled: no files will be deleted or moved.")
        if (config.recursive) console.info("Recursive mode enabled: processing subdirectories.")
        when (config.mode) {
            HandlingMode.QUARANTINE ->
                console.info(
                    "Quarantine mode (default): unmatched ARW files move to ${event.quarantineDir.toAbsolutePath()}."
                )

            HandlingMode.DELETE ->
                console.info("Delete mode: unmatched ARW files will be permanently removed.")
        }
    }

    private fun summarize(context: SummaryContext) {
        val stats = context.stats
        console.info(
            "Found ${stats.totalArw} ARW file(s) (case-insensitive) across ${context.directoryCount} directory(ies)."
        )
        console.info(
            "Found ${stats.totalJpg} JPG file(s) (case-insensitive) across ${context.directoryCount} directory(ies)."
        )

        val verb = if (context.config.mode == HandlingMode.QUARANTINE) "quarantine" else "delete"
        val action = if (context.config.dryRun) "potentially $verb" else verb
        console.info(
            "Found ${stats.totalUnmatched} ARW file(s) to $action (no matching JPG in same directory)."
        )

        if (!context.config.dryRun) {
            when (context.config.mode) {
                HandlingMode.QUARANTINE ->
                    console.info("Moved ${stats.quarantined} file(s) into ${context.quarantineDir.toAbsolutePath()}.")

                HandlingMode.DELETE ->
                    console.info("Deleted ${stats.deleted} file(s).")
            }
        }
    }
}
