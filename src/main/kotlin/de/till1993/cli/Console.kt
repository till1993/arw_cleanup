package de.till1993.cli

import java.io.PrintStream

interface Console {
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
}

class StdConsole(
    private val out: PrintStream = System.out,
    private val err: PrintStream = System.err
) : Console {
    override fun info(message: String) {
        out.println(message)
    }

    override fun warn(message: String) {
        out.println("WARN: $message")
    }

    override fun error(message: String) {
        err.println("ERROR: $message")
    }
}
