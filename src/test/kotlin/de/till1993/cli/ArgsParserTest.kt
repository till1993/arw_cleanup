package de.till1993.cli

import de.till1993.FakeFileSystem
import de.till1993.RecordingConsole
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class ArgsParserTest {

    @Test
    fun `parses config when filesystem reports existing directory`() {
        // given
        val imageDir = Path("C:/sessions/ok")
        val console = RecordingConsole()
        val fakeFs = FakeFileSystem().stubDirectory(imageDir)
        val parser = ArgsParser(console, fakeFs)

        // when
        val config = parser.parse(arrayOf(imageDir.toString()))

        // then
        assertNotNull(config)
        assertEquals(imageDir.normalize(), config.imageDir)
    }

    @Test
    fun `rejects config when filesystem reports missing directory`() {
        // given
        val missingDir = Path("C:/sessions/missing")
        val console = RecordingConsole()
        val fakeFs = FakeFileSystem()
        val parser = ArgsParser(console, fakeFs)

        // when
        val config = parser.parse(arrayOf(missingDir.toString()))

        // then
        assertNull(config)
        assertEquals(
            listOf("Provided path is not an existing directory: ${missingDir.normalize()}"),
            console.errorMessages
        )
    }
}
