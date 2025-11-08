package de.till1993

import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.*

private const val DEFAULT_QUARANTINE_FOLDER = "_arw_quarantine"

private enum class HandlingMode { QUARANTINE, DELETE }

private data class Config(
    val imageDir: Path,
    val recursive: Boolean,
    val dryRun: Boolean,
    val mode: HandlingMode
)

private data class CleanupStats(
    var totalArw: Int = 0,
    var totalJpg: Int = 0,
    var totalUnmatched: Int = 0,
    var deleted: Int = 0,
    var quarantined: Int = 0
)

fun main(args: Array<String>) {
    val config = parseArgs(args) ?: return
    runCleanup(config)
}

private fun parseArgs(args: Array<String>): Config? {
    if (args.any { it.equals("--help", true) || it == "-h" }) {
        printHelp()
        return null
    }

    val dryRun = args.any { it.equals("--dry-run", true) || it == "-n" }
    val recursive = args.any { it.equals("--recursive", true) || it == "-r" }
    val deleteMode = args.any { it.equals("--delete", true) || it == "-d" }

    val positional = args.filterNot {
        it.equals("--dry-run", true) || it == "-n" ||
            it.equals("--recursive", true) || it == "-r" ||
            it.equals("--delete", true) || it == "-d"
    }

    if (positional.size != 1) {
        printHelp()
        return null
    }

    val imageDir = positional.first().let {
        try {
            Path(it)
        } catch (e: InvalidPathException) {
            println("Invalid path: $it, reason: ${e.message}")
            printHelp()
            return null
        }
    }.normalize()

    if (!imageDir.exists() || !imageDir.isDirectory()) {
        println("Provided path is not an existing directory: $imageDir")
        return null
    }

    val mode = if (deleteMode) HandlingMode.DELETE else HandlingMode.QUARANTINE
    return Config(imageDir, recursive, dryRun, mode)
}

private fun printHelp() {
    println("___________________________________________")
    println("ARW Cleanup Tool")
    println("___________________________________________")
    println("Usage: arw_cleanup [--dry-run|-n] [--recursive|-r] [--delete|-d] <image_directory_path>")
    println("Quarantine unmatched ARW files by default, or delete them when opting in with --delete.")
    println("Matching is case-insensitive. Quote the path if it contains spaces.")
    println()
    println("Options:")
    println("  --dry-run, -n    Show which files would be deleted/moved without touching them")
    println("  --recursive, -r  Process subdirectories recursively")
    println("  --delete, -d     Delete unmatched ARW files instead of quarantining them")
    println("  --help, -h       Show this help message")
    println()
    println("Examples:")
    println("  arw_cleanup \"C:\\\\Users\\\\User\\\\Pictures\\\\My Images\"")
    println("  arw_cleanup --dry-run --recursive \"D:\\\\Photos\\\\2025\"")
    println("  arw_cleanup --delete \"D:\\\\Photos\\\\2025\"")
    println("___________________________________________")
}

private fun runCleanup(config: Config) {
    val quarantineDir = config.imageDir.resolve(DEFAULT_QUARANTINE_FOLDER).normalize()
    if (config.mode == HandlingMode.QUARANTINE && !config.dryRun) {
        try {
            quarantineDir.createDirectories()
        } catch (e: Exception) {
            println("Unable to create quarantine directory (${quarantineDir.toAbsolutePath()}): ${e.message}")
            return
        }
    }

    println("Using image directory: ${config.imageDir}")
    if (config.dryRun) println("Dry run enabled: no files will be deleted or moved.")
    if (config.recursive) println("Recursive mode enabled: processing subdirectories.")
    when (config.mode) {
        HandlingMode.QUARANTINE ->
            println("Quarantine mode (default): unmatched ARW files move to ${quarantineDir.toAbsolutePath()}.")
        HandlingMode.DELETE ->
            println("Delete mode: unmatched ARW files will be permanently removed.")
    }

    val files = collectFiles(config, quarantineDir)
    println("Scanned ${files.size} regular file(s)${if (config.recursive) " (recursive)" else ""}.")

    val stats = CleanupStats()
    val filesByDir = files.groupBy { it.parent ?: config.imageDir }
    for ((dir, dirFiles) in filesByDir) {
        processDirectory(dir, dirFiles, config, quarantineDir, stats)
    }

    println("Found ${stats.totalArw} ARW file(s) (case-insensitive) across ${filesByDir.size} directory(ies).")
    println("Found ${stats.totalJpg} JPG file(s) (case-insensitive) across ${filesByDir.size} directory(ies).")
    val verb = if (config.mode == HandlingMode.QUARANTINE) "quarantine" else "delete"
    println("Found ${stats.totalUnmatched} ARW file(s) to ${if (config.dryRun) "potentially $verb" else verb} (no matching JPG in same directory).")
    if (!config.dryRun) {
        when (config.mode) {
            HandlingMode.QUARANTINE ->
                println("Moved ${stats.quarantined} file(s) into ${quarantineDir.toAbsolutePath()}.")
            HandlingMode.DELETE ->
                println("Deleted ${stats.deleted} file(s).")
        }
    }
}

private fun collectFiles(config: Config, quarantineDir: Path): List<Path> {
    val filesSeq = if (config.recursive) {
        config.imageDir.walk().filter { it.isRegularFile() }
    } else {
        config.imageDir.listDirectoryEntries().asSequence().filter { it.isRegularFile() }
    }
    return filesSeq
        .filterNot { it.normalize().startsWith(quarantineDir) }
        .toList()
}

private fun processDirectory(
    dir: Path,
    files: List<Path>,
    config: Config,
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

    println(
        "Directory: ${dir.toAbsolutePath()} => ARW: ${arwFiles.size}, " +
            "JPG: ${jpgFiles.size}, to handle: ${unmatched.size}"
    )

    unmatched.forEach { handleFile(it, config, quarantineDir, stats) }
}

private fun handleFile(
    file: Path,
    config: Config,
    quarantineDir: Path,
    stats: CleanupStats
) {
    if (config.dryRun) {
        when (config.mode) {
            HandlingMode.QUARANTINE -> {
                val target = determineQuarantineTarget(quarantineDir, config.imageDir, file)
                println("Would move to quarantine: ${file.toAbsolutePath()} -> ${target.toAbsolutePath()}")
            }
            HandlingMode.DELETE ->
                println("Would delete: ${file.toAbsolutePath()}")
        }
        return
    }

    when (config.mode) {
        HandlingMode.QUARANTINE -> {
            try {
                val targetPath = moveToQuarantine(quarantineDir, config.imageDir, file)
                stats.quarantined++
                println("Moved to quarantine: ${file.toAbsolutePath()} -> ${targetPath.toAbsolutePath()}")
            } catch (e: Exception) {
                println("Failed to move to quarantine: ${file.toAbsolutePath()}, reason: ${e.message}")
            }
        }
        HandlingMode.DELETE -> {
            try {
                val deleted = file.deleteIfExists()
                if (deleted) {
                    stats.deleted++
                    println("Deleted: ${file.toAbsolutePath()}")
                } else {
                    println("Failed to delete: ${file.toAbsolutePath()} (file not found)")
                }
            } catch (e: Exception) {
                println("Failed to delete: ${file.toAbsolutePath()}, reason: ${e.message}")
            }
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
    targetPath.parent?.createDirectories()
    file.moveTo(targetPath, overwrite = true)
    return targetPath
}
