package dev.daviante.kromium.core.util

import dev.daviante.kromium.domain.model.Architecture
import dev.daviante.kromium.domain.model.OperatingSystem
import dev.daviante.kromium.domain.model.PlatformInfo



object PlatformDetector {
    @Volatile
    private var cachedPlatform: PlatformInfo? = null

    @JvmStatic
    @Throws(IllegalStateException::class)
    fun current(): PlatformInfo {
        cachedPlatform?.let { return it }

        val osName = System.getProperty("os.name") ?: ""
        val osArch = System.getProperty("os.arch") ?: ""

        val os = OperatingSystem.fromSystem(osName)
            ?: throw IllegalStateException("Unsupported operating system: $osName")
        val arch = Architecture.fromSystem(osArch)
            ?: throw IllegalStateException("Unsupported CPU architecture: $osArch")

        return PlatformInfo(os, arch).also { cachedPlatform = it }
    }
}
