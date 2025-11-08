package de.till1993.core

import de.till1993.cli.Console
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeTo

class CleanupRunner(
    private val console: Console,
    private val fileSystem: FileSystem = LocalFileSystem,
    private val reporter: CleanupReporter = ConsoleCleanupReporter(console)
) {

    fun run(config: CleanupConfig) {
        val quarantineDir = config.imageDir.resolve(DEFAULT_QUARANTINE_FOLDER).normalize()
        if (config.mode == HandlingMode.QUARANTINE && !config.dryRun) {
            if (!ensureQuarantineExists(quarantineDir)) return
        }

        reporter.publish(CleanupEvent.RunStarted(config, quarantineDir))

        val files = collectFiles(config, quarantineDir)
        reporter.publish(CleanupEvent.ScanCompleted(files.size, config.recursive))

        val stats = CleanupStats()
        val filesByDir = files.groupBy { it.parent ?: config.imageDir }
        filesByDir.forEach { (dir, dirFiles) ->
            processDirectory(dir, dirFiles, config, quarantineDir, stats)
        }

        reporter.publish(
            CleanupEvent.Summary(
                SummaryContext(
                    directoryCount = filesByDir.size,
                    stats = stats,
                    config = config,
                    quarantineDir = quarantineDir
                )
            )
        )
    }

    private fun ensureQuarantineExists(quarantineDir: Path): Boolean =
        runCatching { fileSystem.createDirectories(quarantineDir); true }
            .getOrElse { error ->
                reporter.publish(
                    CleanupEvent.QuarantineCreationFailed(
                        quarantineDir,
                        error.message ?: error::class.simpleName ?: "Unknown error"
                    )
                )
                false
            }

    private fun collectFiles(config: CleanupConfig, quarantineDir: Path): List<Path> {
        val filesSeq = if (config.recursive) {
            fileSystem.walkRegularFiles(config.imageDir)
        } else {
            fileSystem.listRegularFiles(config.imageDir)
        }
        return filesSeq
            .filterNot { it.normalize().startsWith(quarantineDir) }
            .toList()
    }

    private fun processDirectory(
        dir: Path,
        files: List<Path>,
        config: CleanupConfig,
        quarantineDir: Path,
        stats: CleanupStats
    ) {
        val arwFiles = files.filter { it.extension.equals("arw", ignoreCase = true) }
        val jpgFiles = files.filter { it.extension.equals("jpg", ignoreCase = true) }
        if (arwFiles.isEmpty() && jpgFiles.isEmpty()) return

        stats.totalArw += arwFiles.size
        stats.totalJpg += jpgFiles.size

        val keepBaseNames = jpgFiles.map { it.nameWithoutExtension.lowercase() }.toSet()
        val unmatched = arwFiles.filterNot { it.nameWithoutExtension.lowercase() in keepBaseNames }
        stats.totalUnmatched += unmatched.size

        reporter.publish(
            CleanupEvent.DirectoryStats(
                directory = dir,
                arwCount = arwFiles.size,
                jpgCount = jpgFiles.size,
                unmatchedCount = unmatched.size
            )
        )

        unmatched.forEach { handleFile(it, config, quarantineDir, stats) }
    }

    private fun handleFile(
        file: Path,
        config: CleanupConfig,
        quarantineDir: Path,
        stats: CleanupStats
    ) {
        if (config.dryRun) {
            when (config.mode) {
                HandlingMode.QUARANTINE -> {
                    val target = determineQuarantineTarget(quarantineDir, config.imageDir, file)
                    reporter.publish(CleanupEvent.DryRunMove(file, target))
                }
                HandlingMode.DELETE ->
                    reporter.publish(CleanupEvent.DryRunDelete(file))
            }
            return
        }

        when (config.mode) {
            HandlingMode.QUARANTINE ->
                moveFileToQuarantine(file, config, quarantineDir, stats)

            HandlingMode.DELETE ->
                deleteFile(file, stats)
        }
    }

    private fun moveFileToQuarantine(
        file: Path,
        config: CleanupConfig,
        quarantineDir: Path,
        stats: CleanupStats
    ) {
        try {
            val targetPath = moveToQuarantine(quarantineDir, config.imageDir, file)
            stats.quarantined++
            reporter.publish(CleanupEvent.MoveSucceeded(file, targetPath))
        } catch (e: Exception) {
            reporter.publish(
                CleanupEvent.MoveFailed(
                    file,
                    e.message ?: e::class.simpleName ?: "Unknown error"
                )
            )
        }
    }

    private fun deleteFile(file: Path, stats: CleanupStats) {
        try {
            val deleted = fileSystem.deleteIfExists(file)
            if (deleted) {
                stats.deleted++
                reporter.publish(CleanupEvent.DeleteSucceeded(file))
            } else {
                reporter.publish(CleanupEvent.DeleteSkippedMissing(file))
            }
        } catch (e: Exception) {
            reporter.publish(
                CleanupEvent.DeleteFailed(
                    file,
                    e.message ?: e::class.simpleName ?: "Unknown error"
                )
            )
        }
    }

    private fun determineQuarantineTarget(quarantineDir: Path, imageDir: Path, file: Path): Path {
        val relative = runCatching { file.relativeTo(imageDir) }.getOrNull()
        return if (relative == null || relative.nameCount == 0) {
            quarantineDir.resolve(file.name)
        } else {
            quarantineDir.resolve(relative)
        }
    }

    private fun moveToQuarantine(quarantineDir: Path, imageDir: Path, file: Path): Path {
        val targetPath = determineQuarantineTarget(quarantineDir, imageDir, file).normalize()
        targetPath.parent?.let { fileSystem.createDirectories(it) }
        fileSystem.move(file, targetPath, overwrite = true)
        return targetPath
    }
}
