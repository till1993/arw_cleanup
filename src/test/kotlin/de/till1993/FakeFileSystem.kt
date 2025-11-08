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

    fun stubDirectory(dir: Path, vararg files: Path): FakeFileSystem = apply {
        directoryContents[dir] = files.toList()
    }

    fun failMoveFor(vararg files: Path, throwable: Throwable = IllegalStateException("move failed")): FakeFileSystem =
        apply {
            files.forEach { moveFailures[it] = throwable }
        }

    override fun createDirectories(path: Path) {
        createdDirs.add(path)
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
        return deleteResults[path] ?: true
    }

    override fun move(source: Path, target: Path, overwrite: Boolean) {
        val failure = moveFailures[source]
        if (failure != null) throw failure
        moved.add(source to target)
    }
}
