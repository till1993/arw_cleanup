package de.till1993

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.text.Charsets

private const val QUARANTINE_DIR_NAME = "_arw_quarantine"

internal fun runCleanup(imageDir: Path, vararg options: String) {
    val args = options.toMutableList().apply { add(imageDir.toString()) }
    main(args.toTypedArray())
}

internal fun Path.ensureDir(): Path = apply { createDirectories() }

internal fun Path.subDir(name: String): Path = resolve(name).ensureDir()

internal fun Path.sampleFile(relativePath: String, content: String = "placeholder"): Path =
    resolve(relativePath).also { file ->
        file.parent?.createDirectories()
        file.writeText(content)
    }

internal fun Path.quarantineDir(): Path = resolve(QUARANTINE_DIR_NAME)

internal fun Path.quarantinedFile(relativePath: String): Path = quarantineDir().resolve(relativePath)

internal fun captureStdout(block: () -> Unit): String {
    val originalOut = System.out
    val buffer = ByteArrayOutputStream()
    System.setOut(PrintStream(buffer, true))
    return try {
        block()
        buffer.toString(Charsets.UTF_8)
    } finally {
        System.setOut(originalOut)
    }
}
