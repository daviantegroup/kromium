package dev.daviante.kromium.domain.model

/**
 * Encapsulates the resolved runtime platform.
 */
data class PlatformInfo(
    val os: OperatingSystem,
    val arch: Architecture
)
