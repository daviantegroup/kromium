package dev.daviante.kromium.core.util

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import dev.daviante.kromium.domain.model.*

class PlatformDetectorTest {

    @Test
    fun testCurrentPlatformDetection() {
        val platform = PlatformDetector.current()
        assertNotNull(platform, "Platform detector should resolve a non-null platform")
        
        // Assert valid enumeration types
        assertTrue(
            platform.os in listOf(OperatingSystem.Windows, OperatingSystem.MacOS, OperatingSystem.Linux),
            "Resolved OS must be one of the known supported operating systems"
        )
        assertTrue(
            platform.arch in listOf(Architecture.X64, Architecture.Arm64),
            "Resolved Architecture must be one of the known architectures"
        )
        assertTrue(
            platform.os.dynamicLibraryExtension.isNotBlank(),
            "Dynamic library extension should be populated for the current OS"
        )
    }

    @Test
    fun testOsSpecificBehaviors() {
        assertEquals(".dll", OperatingSystem.Windows.dynamicLibraryExtension)
        assertEquals(".dylib", OperatingSystem.MacOS.dynamicLibraryExtension)
        assertEquals(".so", OperatingSystem.Linux.dynamicLibraryExtension)
        
        assertTrue(OperatingSystem.MacOS.isMacOS)
        assertTrue(OperatingSystem.Windows.isWindows)
        assertTrue(OperatingSystem.Linux.isLinux)
    }

    @Test
    fun testMacOsDynamicFrameworkPaths() {
        val tempDir = java.nio.file.Files.createTempDirectory("macos_framework_test").toFile()
        try {
            // 1. Fallback behavior (older JBR)
            val fallbackFramework = OperatingSystem.MacOS.getFrameworkPath(tempDir, inFrameworks = true)
            assertTrue(fallbackFramework.endsWith("Chromium Embedded Framework.framework"))
            assertTrue(fallbackFramework.contains("Frameworks"))

            // 2. JBR 25 / CEF 150 layout with cef_server.app
            val cefServerFrameworks = java.io.File(tempDir, "Frameworks/cef_server.app/Contents/Frameworks")
            cefServerFrameworks.mkdirs()

            val resolvedFramework = OperatingSystem.MacOS.getFrameworkPath(tempDir, inFrameworks = true).replace('\\', '/')
            assertTrue(resolvedFramework.contains("cef_server.app/Contents/Frameworks"))

            val resolvedBundle = OperatingSystem.MacOS.getMainBundlePath(tempDir).replace('\\', '/')
            assertTrue(resolvedBundle.contains("cef_server.app/Contents/Frameworks/jcef Helper.app"))

            val resolvedBrowser = OperatingSystem.MacOS.getBrowserPath(tempDir).replace('\\', '/')
            assertTrue(resolvedBrowser.contains("cef_server.app/Contents/Frameworks/jcef Helper.app/Contents/MacOS/jcef Helper"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
