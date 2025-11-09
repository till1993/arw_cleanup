package de.till1993

import de.till1993.cli.Console

class RecordingConsole : Console {
    val infoMessages = mutableListOf<String>()
    val warnMessages = mutableListOf<String>()
    val errorMessages = mutableListOf<String>()

    override fun info(message: String) {
        infoMessages += message
    }

    override fun warn(message: String) {
        warnMessages += message
    }

    override fun error(message: String) {
        errorMessages += message
    }
}
