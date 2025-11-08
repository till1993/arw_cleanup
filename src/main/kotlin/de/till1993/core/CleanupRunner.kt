package de.till1993.core

import de.till1993.cli.Console
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeTo

class CleanupRunner(
    private val console: Console,
    private val fileSystem: FileSystem = LocalFileSystem
) {

    fun run(config: CleanupConfig) {
        val quarantineDir = config.imageDir.resolve(DEFAULT_QUARANTINE_FOLDER).normalize()
        if (config.mode == HandlingMode.QUARANTINE && !config.dryRun) {
            if (!ensureQuarantineExists(quarantineDir)) return
        }

        announceRun(config, quarantineDir)

        val files = collectFiles(config, quarantineDir)
        console.info("Scanned ${files.size} regular file(s)${if (config.recursive) " (recursive)" else ""}.")

        val stats = CleanupStats()
        val filesByDir = files.groupBy { it.parent ?: config.imageDir }
        filesByDir.forEach { (dir, dirFiles) ->
            processDirectory(dir, dirFiles, config, quarantineDir, stats)
        }

        summarize(filesByDir.size, stats, config, quarantineDir)
    }

    private fun ensureQuarantineExists(quarantineDir: Path): Boolean =
        runCatching { fileSystem.createDirectories(quarantineDir); true }
            .getOrElse { error ->
                console.error(
                    "Unable to create quarantine directory (${quarantineDir.toAbsolutePath()}): ${error.message}"
                )
                false
            }

    private fun announceRun(config: CleanupConfig, quarantineDir: Path) {
        console.info("Using image directory: ${config.imageDir}")
        if (config.dryRun) console.info("Dry run enabled: no files will be deleted or moved.")
        if (config.recursive) console.info("Recursive mode enabled: processing subdirectories.")
        when (config.mode) {
            HandlingMode.QUARANTINE ->
                console.info(
                    "Quarantine mode (default): unmatched ARW files move to ${quarantineDir.toAbsolutePath()}."
                )
            HandlingMode.DELETE ->
                console.info("Delete mode: unmatched ARW files will be permanently removed.")
        }
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

        console.info(
            "Directory: ${dir.toAbsolutePath()} => ARW: ${arwFiles.size}, " +
                "JPG: ${jpgFiles.size}, to handle: ${unmatched.size}"
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
                    console.info("Would move to quarantine: ${file.toAbsolutePath()} -> ${target.toAbsolutePath()}")
                }

                HandlingMode.DELETE ->
                    console.info("Would delete: ${file.toAbsolutePath()}")
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
            console.info("Moved to quarantine: ${file.toAbsolutePath()} -> ${targetPath.toAbsolutePath()}")
        } catch (e: Exception) {
            console.error("Failed to move to quarantine: ${file.toAbsolutePath()}, reason: ${e.message}")
        }
    }

    private fun deleteFile(file: Path, stats: CleanupStats) {
        try {
            val deleted = fileSystem.deleteIfExists(file)
            if (deleted) {
                stats.deleted++
                console.info("Deleted: ${file.toAbsolutePath()}")
            } else {
                console.warn("Failed to delete: ${file.toAbsolutePath()} (file not found)")
            }
        } catch (e: Exception) {
            console.error("Failed to delete: ${file.toAbsolutePath()}, reason: ${e.message}")
        }
    }

    private fun summarize(
        directoryCount: Int,
        stats: CleanupStats,
        config: CleanupConfig,
        quarantineDir: Path
    ) {
        console.info(
            "Found ${stats.totalArw} ARW file(s) (case-insensitive) across $directoryCount directory(ies)."
        )
        console.info(
            "Found ${stats.totalJpg} JPG file(s) (case-insensitive) across $directoryCount directory(ies)."
        )

        val verb = if (config.mode == HandlingMode.QUARANTINE) "quarantine" else "delete"
        val action = if (config.dryRun) "potentially $verb" else verb
        console.info(
            "Found ${stats.totalUnmatched} ARW file(s) to $action (no matching JPG in same directory)."
        )

        if (!config.dryRun) {
            when (config.mode) {
                HandlingMode.QUARANTINE ->
                    console.info("Moved ${stats.quarantined} file(s) into ${quarantineDir.toAbsolutePath()}.")

                HandlingMode.DELETE ->
                    console.info("Deleted ${stats.deleted} file(s).")
            }
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
