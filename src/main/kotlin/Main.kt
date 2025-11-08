package de.till1993

import java.nio.file.InvalidPathException
import kotlin.io.path.*


fun main(args: Array<String>) {
    fun printHelp() {
        println("___________________________________________")
        println("ARW Cleanup Tool")
        println("___________________________________________")
        println("Usage: arw_cleanup [--dry-run|-n] [--recursive|-r] <image_directory_path>")
        println("This tool deletes ARW files in the specified directory (optionally recursively) that do not have a corresponding JPG file.")
        println("Matching is case-insensitive. Quote the path if it contains spaces.")
        println()
        println("Options:")
        println("  --dry-run, -n    Show which files would be deleted without actually deleting them")
        println("  --recursive, -r  Process subdirectories recursively")
        println("  --help, -h       Show this help message")
        println()
        println("Examples:")
        println("  arw_cleanup \"C:\\Users\\User\\Pictures\\My Images\"")
        println("  arw_cleanup --dry-run --recursive \"D:\\Photos\\2025\"")
        println("___________________________________________")
    }

    if (args.any { it in listOf("--help", "-h") }) {
        printHelp(); return@main
    }

    val dryRun = args.any { it.equals("--dry-run", ignoreCase = true) || it == "-n" }
    val recursive = args.any { it.equals("--recursive", ignoreCase = true) || it == "-r" }

    // Positional args: remove flags
    val positional =
        args.filterNot { it.equals("--dry-run", true) || it == "-n" || it.equals("--recursive", true) || it == "-r" }
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
    }


    println("Using image directory: $imageDir")
    if (dryRun) println("Dry run enabled: no files will be deleted.")
    if (recursive) println("Recursive mode enabled: processing subdirectories.")

    val allFiles = if (recursive) {
        imageDir.walk().filter { it.isRegularFile() }.toList()
    } else {
        imageDir.listDirectoryEntries().filter { it.isRegularFile() }
    }

    println("Scanned ${allFiles.size} regular file(s)${if (recursive) " (recursive)" else ""}.")

    val filesByDir = allFiles.groupBy { it.parent }

    var totalArw = 0
    var totalJpg = 0
    var totalToDelete = 0
    var deletedCount = 0

    for ((dir, files) in filesByDir) {
        val arwFiles = files.filter { it.extension.equals("arw", ignoreCase = true) }
        val jpgFiles = files.filter { it.extension.equals("jpg", ignoreCase = true) }

        if (arwFiles.isEmpty() && jpgFiles.isEmpty()) continue

        totalArw += arwFiles.size
        totalJpg += jpgFiles.size

        val keepBaseNames = jpgFiles.map { it.nameWithoutExtension.lowercase() }.toSet()
        val arwFilesToDelete = arwFiles.filterNot { it.nameWithoutExtension.lowercase() in keepBaseNames }

        totalToDelete += arwFilesToDelete.size

        println("Directory: ${dir.toAbsolutePath()} => ARW: ${arwFiles.size}, JPG: ${jpgFiles.size}, to delete: ${arwFilesToDelete.size}")

        for (file in arwFilesToDelete) {
            if (dryRun) {
                println("Would delete: ${file.toAbsolutePath()}")
                continue
            }
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

    println("Found $totalArw ARW file(s) (case-insensitive) across ${filesByDir.size} directory(ies).")
    println("Found $totalJpg JPG file(s) (case-insensitive) across ${filesByDir.size} directory(ies).")
    println("Found $totalToDelete ARW file(s) to ${if (dryRun) "potentially delete" else "delete"} (no matching JPG in same directory).")
}