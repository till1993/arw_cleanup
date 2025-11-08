package de.till1993

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertTrue

class CliHelpTest {

    @Test
    fun `short help flag prints usage banner`() {
        val output = captureStdout {
            main(arrayOf("-h"))
        }

        assertTrue(
            output.contains("Usage: arw_cleanup"),
            "Help flag should display the usage banner"
        )
    }

    @Test
    fun `missing directory argument prints usage`() {
        val output = captureStdout {
            main(emptyArray())
        }

        assertTrue(
            output.contains("Usage: arw_cleanup"),
            "Missing required directory must show the usage instructions"
        )
    }

    @Test
    fun `nonexistent directory prints helpful error`(@TempDir tempDir: Path) {
        val missingDir = tempDir.resolve("not-there")

        val output = captureStdout {
            main(arrayOf(missingDir.toString()))
        }

        assertTrue(
            output.contains("Provided path is not an existing directory"),
            "Users should see a clear error when the directory does not exist"
        )
    }

    @Test
    fun `unknown flag prints usage guidance`(@TempDir tempDir: Path) {
        val existingDir = tempDir.subDir("existing")

        val output = captureStdout {
            main(arrayOf("--unknown", existingDir.toString()))
        }

        assertTrue(
            output.contains("Usage: arw_cleanup"),
            "Unknown flags should route the user to the usage banner"
        )
    }
}
