package de.till1993.cli

import de.till1993.core.CleanupConfig
import de.till1993.core.HandlingMode
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

internal class ArgsParser(
    private val console: Console,
    private val flags: List<CliFlag> = DEFAULT_FLAGS
) {
    private val helpPrinter = HelpPrinter(console, flags)
    private val flagLookup = flags
        .flatMap { flag -> flag.tokens.map { token -> token.lowercase() to flag } }
        .toMap()

    fun parse(args: Array<String>): CleanupConfig? {
        val builder = ConfigBuilder()
        val positional = mutableListOf<String>()

        for (arg in args) {
            val normalized = arg.lowercase()
            val flag = flagLookup[normalized]
            if (flag != null) {
                flag.apply(builder)
                continue
            }

            if (arg.startsWith("-")) {
                console.error("Unknown option: $arg")
                helpPrinter.printUsage()
                return null
            }

            positional += arg
        }

        if (builder.helpRequested) {
            helpPrinter.printUsage()
            return null
        }

        if (positional.size != 1) {
            val message = if (positional.isEmpty()) {
                "Exactly one image directory path is required."
            } else {
                "Only one image directory path is supported. Received: ${positional.joinToString(", ")}"
            }
            console.error(message)
            helpPrinter.printUsage()
            return null
        }

        val imageDir = positional.first().let { raw ->
            try {
                Path(raw)
            } catch (e: InvalidPathException) {
                console.error("Invalid path: $raw, reason: ${e.message}")
                helpPrinter.printUsage()
                return null
            }
        }.normalize()

        if (!imageDir.exists() || !imageDir.isDirectory()) {
            console.error("Provided path is not an existing directory: $imageDir")
            return null
        }

        return builder.toConfig(imageDir)
    }

    private companion object {
        private val DEFAULT_FLAGS = listOf(
            CliFlag(
                tokens = listOf("--dry-run", "-n"),
                description = "Show which files would be deleted/moved without touching them"
            ) { builder -> builder.dryRun = true },
            CliFlag(
                tokens = listOf("--recursive", "-r"),
                description = "Process subdirectories recursively"
            ) { builder -> builder.recursive = true },
            CliFlag(
                tokens = listOf("--delete", "-d"),
                description = "Delete unmatched ARW files instead of quarantining them"
            ) { builder -> builder.mode = HandlingMode.DELETE },
            CliFlag(
                tokens = listOf("--help", "-h"),
                description = "Show this help message"
            ) { builder -> builder.helpRequested = true }
        )
    }
}

internal class ConfigBuilder {
    var recursive: Boolean = false
    var dryRun: Boolean = false
    var mode: HandlingMode = HandlingMode.QUARANTINE
    var helpRequested: Boolean = false

    fun toConfig(imageDir: Path): CleanupConfig =
        CleanupConfig(imageDir, recursive, dryRun, mode)
}

internal data class CliFlag(
    val tokens: List<String>,
    val description: String,
    val apply: (ConfigBuilder) -> Unit
)

internal class HelpPrinter(
    private val console: Console,
    private val flags: List<CliFlag>
) {
    private val examples = listOf(
        "arw_cleanup \"C:\\\\Users\\\\User\\\\Pictures\\\\My Images\"",
        "arw_cleanup --dry-run --recursive \"D:\\\\Photos\\\\2025\"",
        "arw_cleanup --delete \"D:\\\\Photos\\\\2025\""
    )

    fun printUsage() {
        console.info("___________________________________________")
        console.info("ARW Cleanup Tool")
        console.info("___________________________________________")
        console.info("Usage: arw_cleanup [options] <image_directory_path>")
        console.info("Quarantine unmatched ARW files by default, or delete them when opting in with --delete.")
        console.info("Matching is case-insensitive. Quote the path if it contains spaces.")
        console.info("")
        console.info("Options:")
        flags.forEach { flag ->
            console.info("  ${flag.tokens.joinToString(", ")}  ${flag.description}")
        }
        console.info("")
        console.info("Examples:")
        examples.forEach { example -> console.info("  $example") }
        console.info("___________________________________________")
    }
}
