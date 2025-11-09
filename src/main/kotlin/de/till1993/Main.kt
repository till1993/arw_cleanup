package de.till1993

import de.till1993.cli.ArgsParser
import de.till1993.cli.StdConsole
import de.till1993.core.CleanupRunner

fun main(args: Array<String>) {
    val console = StdConsole()
    val config = ArgsParser(console).parse(args) ?: return
    CleanupRunner(console).run(config)
}
