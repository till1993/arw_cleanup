package de.till1993

import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.*

private const val DEFAULT_QUARANTINE_FOLDER = "_arw_quarantine"

fun main(args: Array<String>) {
    fun printHelp() {
        println("___________________________________________")
        println("ARW Cleanup Tool")
        println("___________________________________________")
        println("Usage: arw_cleanup [--dry-run|-n] [--recursive|-r] [--delete|-d] <image_directory_path>")
        println("This tool quarantines (or deletes, if requested) ARW files in the specified directory (optionally recursively) that do not have a corresponding JPG file.")
        println("Matching is case-insensitive. Quote the path if it contains spaces.")
        println()
        println("Options:")
        println("  --dry-run, -n    Show which files would be deleted without actually deleting them")
        println("  --recursive, -r  Process subdirectories recursively")
        println("  --delete, -d     Delete unmatched ARW files instead of moving them into _arw_quarantine")
        println("  --help, -h       Show this help message")
        println()
        println("Examples:")
        println("  arw_cleanup \"C:\\Users\\User\\Pictures\\My Images\"")
        println("  arw_cleanup --dry-run --recursive \"D:\\Photos\\2025\"")
        println("  arw_cleanup --delete \"D:\\Photos\\2025\"")
        println("___________________________________________")
    }

    if (args.any { it in listOf("--help", "-h") }) {
        printHelp(); return@main
    }

    val dryRun = args.any { it.equals("--dry-run", ignoreCase = true) || it == "-n" }
    val recursive = args.any { it.equals("--recursive", ignoreCase = true) || it == "-r" }
    val deleteMode = args.any { it.equals("--delete", ignoreCase = true) || it == "-d" }

    // Positional args: remove flags
    val positional = args.filterNot {
        it.equals("--dry-run", true) || it == "-n" ||
            it.equals("--recursive", true) || it == "-r" ||
            it.equals("--delete", true) || it == "-d"
    }
    if (positional.size != 1) {
        printHelp(); return@main
    }

    val imageDir = positional.first().let {
        try {
            Path(it)
        } catch (e: InvalidPathException) {
            println("Invalid path: $it, reason: ${e.message}")
            printHelp(); return@main
        }
    }.normalize()

    if (!imageDir.exists() || !imageDir.isDirectory()) {
        println("Provided path is not an existing directory: $imageDir")
        return@main
    }

    val quarantineDir = imageDir.resolve(DEFAULT_QUARANTINE_FOLDER).normalize()
    val useQuarantine = !deleteMode

    if (useQuarantine && !dryRun) {
        try {
            quarantineDir.createDirectories()
        } catch (e: Exception) {
            println("Unable to create quarantine directory (${quarantineDir.toAbsolutePath()}): ${e.message}")
            return@main
        }
    }

    println("Using image directory: $imageDir")
    if (dryRun) println("Dry run enabled: no files will be deleted or moved.")
    if (recursive) println("Recursive mode enabled: processing subdirectories.")
    if (useQuarantine) {
        println("Quarantine mode enabled (default): unmatched ARW files will move to ${quarantineDir.toAbsolutePath()}.")
    } else {
        println("Delete mode enabled: unmatched ARW files will be permanently removed.")
    }

    val allFiles = if (recursive) {
        imageDir.walk().filter { it.isRegularFile() }.toList()
    } else {
        imageDir.listDirectoryEntries().filter { it.isRegularFile() }
    }.filterNot {
        it.normalize().startsWith(quarantineDir)
    }

    println("Scanned ${allFiles.size} regular file(s)${if (recursive) " (recursive)" else ""}.")

    val filesByDir = allFiles.groupBy { it.parent }

    var totalArw = 0
    var totalJpg = 0
    var totalToDelete = 0
    var deletedCount = 0
    var movedToQuarantine = 0

    for ((dir, files) in filesByDir) {
        val arwFiles = files.filter { it.extension.equals("arw", ignoreCase = true) }
        val jpgFiles = files.filter { it.extension.equals("jpg", ignoreCase = true) }

        if (arwFiles.isEmpty() && jpgFiles.isEmpty()) continue

        totalArw += arwFiles.size
        totalJpg += jpgFiles.size

        val keepBaseNames = jpgFiles.map { it.nameWithoutExtension.lowercase() }.toSet()
        val arwFilesToDelete = arwFiles.filterNot { it.nameWithoutExtension.lowercase() in keepBaseNames }

        totalToDelete += arwFilesToDelete.size

        println("Directory: ${dir.toAbsolutePath()} => ARW: ${arwFiles.size}, JPG: ${jpgFiles.size}, to handle: ${arwFilesToDelete.size}")

        for (file in arwFilesToDelete) {
            if (dryRun) {
                if (useQuarantine) {
                    val target = determineQuarantineTarget(quarantineDir, imageDir, file)
                    println("Would move to quarantine: ${file.toAbsolutePath()} -> ${target.toAbsolutePath()}")
                } else {
                    println("Would delete: ${file.toAbsolutePath()}")
                }
                continue
            }
            if (useQuarantine) {
                try {
                    val targetPath = moveToQuarantine(quarantineDir, imageDir, file)
                    movedToQuarantine++
                    println("Moved to quarantine: ${file.toAbsolutePath()} -> ${targetPath.toAbsolutePath()}")
                } catch (e: Exception) {
                    println("Failed to move to quarantine: ${file.toAbsolutePath()}, reason: ${e.message}")
                }
            } else {
                try {
                    val deleted = file.toFile().delete()
                    if (deleted) {
                        deletedCount++
                        println("Deleted: ${file.toAbsolutePath()}")
                    } else {
                        println("Failed to delete: ${file.toAbsolutePath()} (delete() returned false)")
                    }
                } catch (e: Exception) {
                    println("Failed to delete: ${file.toAbsolutePath()}, reason: ${e.message}")
                }
            }
        }
    }

    println("Found $totalArw ARW file(s) (case-insensitive) across ${filesByDir.size} directory(ies).")
    println("Found $totalJpg JPG file(s) (case-insensitive) across ${filesByDir.size} directory(ies).")
    val action = if (useQuarantine) "quarantine" else "delete"
    println("Found $totalToDelete ARW file(s) to ${if (dryRun) "potentially $action" else action} (no matching JPG in same directory).")
    if (!dryRun) {
        if (useQuarantine) {
            println("Moved $movedToQuarantine file(s) into ${quarantineDir.toAbsolutePath()}.")
        } else {
            println("Deleted $deletedCount file(s).")
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
