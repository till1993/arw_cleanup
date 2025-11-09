package de.till1993

import de.till1993.core.FileSystem
import java.nio.file.Path

class FakeFileSystem : FileSystem {
    val createdDirs = mutableListOf<Path>()
    val moved = mutableListOf<Pair<Path, Path>>()
    val deleted = mutableListOf<Path>()
    val deleteResults: MutableMap<Path, Boolean> = mutableMapOf()

    private val directoryContents = mutableMapOf<Path, List<Path>>()
    private val moveFailures = mutableMapOf<Path, Throwable>()
    private val directories = mutableSetOf<Path>()
    private val regularFiles = mutableSetOf<Path>()

    fun stubDirectory(dir: Path, vararg entries: Path): FakeFileSystem = apply {
        directoryContents[dir] = entries.toList()
        directories.add(dir)
        entries.forEach { file ->
            regularFiles.add(file)
            file.parent?.let { directories.add(it) }
        }
    }

    fun failMoveFor(vararg paths: Path, throwable: Throwable = IllegalStateException("move failed")): FakeFileSystem =
        apply {
            paths.forEach { moveFailures[it] = throwable }
        }

    override fun createDirectories(path: Path) {
        createdDirs.add(path)
        directories.add(path)
    }

    override fun listRegularFiles(path: Path): Sequence<Path> =
        directoryContents[path]?.asSequence() ?: emptySequence()

    override fun walkRegularFiles(path: Path): Sequence<Path> =
        directoryContents.entries
            .asSequence()
            .filter { (dir, _) -> dir.startsWith(path) }
            .flatMap { (_, files) -> files.asSequence() }

    override fun deleteIfExists(path: Path): Boolean {
        deleted.add(path)
        regularFiles.remove(path)
        return deleteResults[path] ?: true
    }

    override fun move(source: Path, target: Path, overwrite: Boolean) {
        val failure = moveFailures[source]
        if (failure != null) throw failure
        regularFiles.remove(source)
        target.parent?.let { directories.add(it) }
        regularFiles.add(target)
        moved.add(source to target)
    }

    override fun exists(path: Path): Boolean =
        path in directories || path in regularFiles

    override fun isDirectory(path: Path): Boolean =
        path in directories
}
