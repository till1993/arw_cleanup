package de.till1993.core

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.walk

interface FileSystem {
    fun createDirectories(path: Path)
    fun listRegularFiles(path: Path): Sequence<Path>
    fun walkRegularFiles(path: Path): Sequence<Path>
    fun deleteIfExists(path: Path): Boolean
    fun move(source: Path, target: Path, overwrite: Boolean = false)
    fun exists(path: Path): Boolean
    fun isDirectory(path: Path): Boolean
}

object LocalFileSystem : FileSystem {
    override fun createDirectories(path: Path) {
        path.createDirectories()
    }

    override fun listRegularFiles(path: Path): Sequence<Path> =
        path.listDirectoryEntries().asSequence().filter { it.isRegularFile() }

    override fun walkRegularFiles(path: Path): Sequence<Path> =
        path.walk().filter { it.isRegularFile() }

    override fun deleteIfExists(path: Path): Boolean =
        path.deleteIfExists()

    override fun move(source: Path, target: Path, overwrite: Boolean) {
        source.moveTo(target, overwrite = overwrite)
    }

    override fun exists(path: Path): Boolean = path.exists()

    override fun isDirectory(path: Path): Boolean = path.isDirectory()
}
