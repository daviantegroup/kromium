package dev.daviante.kromium.data.engine

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


import java.io.File


object KromiumEngine {
    
    /**
     * Gets information about the Kromium Engine installation.
     * Note: Version strings are currently hardcoded to the target JCEF bundle (CEF 150).
     * In a future update, these will be dynamically parsed from the bundle metadata.
     */
    @JvmStatic
    @JvmOverloads
    fun getInfo(installDir: File = EngineRegistry.defaultInstallDir()): KromiumEngineInfo {
        return KromiumEngineInfo(
            installDir = installDir,
            isInstalled = EngineRegistry.isInstalled(installDir),
            jcefVersion = "150.0.14",
            cefVersion = "150.0.14+g7c1aa68+chromium-150.0.7871.129",
            chromiumVersion = "150.0.7871.129"
        )
    }

    /**
     * Clears the current engine installation.
     */
    @JvmStatic
    @JvmOverloads
    fun clearInstallation(installDir: File = EngineRegistry.defaultInstallDir()) {
        EngineRegistry.clearInstallation(installDir)
    }
}
