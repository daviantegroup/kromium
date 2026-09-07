package dev.daviante.kromium.core.util

import dev.daviante.kromium.domain.model.*
import dev.daviante.kromium.domain.config.*
import dev.daviante.kromium.domain.exception.*
import dev.daviante.kromium.data.engine.*
import dev.daviante.kromium.data.model.*
import dev.daviante.kromium.presentation.browser.*
import dev.daviante.kromium.presentation.handler.*
import dev.daviante.kromium.presentation.js.*
import dev.daviante.kromium.presentation.network.*
import dev.daviante.kromium.core.logging.*
import dev.daviante.kromium.core.util.*


object PlatformDetector {
    private var cachedPlatform: PlatformInfo? = null

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
