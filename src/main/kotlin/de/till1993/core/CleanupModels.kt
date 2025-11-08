package de.till1993.core

import java.nio.file.Path

const val DEFAULT_QUARANTINE_FOLDER = "_arw_quarantine"

enum class HandlingMode { QUARANTINE, DELETE }

data class CleanupConfig(
    val imageDir: Path,
    val recursive: Boolean,
    val dryRun: Boolean,
    val mode: HandlingMode
)

data class CleanupStats(
    var totalArw: Int = 0,
    var totalJpg: Int = 0,
    var totalUnmatched: Int = 0,
    var deleted: Int = 0,
    var quarantined: Int = 0
)

data class SummaryContext(
    val directoryCount: Int,
    val stats: CleanupStats,
    val config: CleanupConfig,
    val quarantineDir: Path
)
