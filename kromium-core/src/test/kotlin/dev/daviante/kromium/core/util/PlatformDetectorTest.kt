package dev.daviante.kromium.core.util

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import dev.daviante.kromium.domain.model.Architecture
import dev.daviante.kromium.domain.model.OperatingSystem

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
            // 1. Fallback behavior (older JBR layout when cef_server.app is absent)
            val fallbackFramework = OperatingSystem.MacOS.getFrameworkPath(tempDir, inFrameworks = true)
            assertTrue(fallbackFramework.endsWith("Chromium Embedded Framework.framework"))
            assertTrue(fallbackFramework.replace('\\', '/').contains("Frameworks"))

            // 2. JBR 25 layout with cef_server.app takes precedence when present
            val cefServerFrameworksDir = java.io.File(tempDir, "Frameworks/cef_server.app/Contents/Frameworks")
            cefServerFrameworksDir.mkdirs()

            val resolvedFramework = OperatingSystem.MacOS.getFrameworkPath(tempDir, inFrameworks = true).replace('\\', '/')
            assertTrue(resolvedFramework.contains("cef_server.app/Contents/Frameworks"))

            val resolvedBundle = OperatingSystem.MacOS.getMainBundlePath(tempDir).replace('\\', '/')
            assertTrue(resolvedBundle.contains("cef_server.app/Contents/Frameworks/jcef Helper.app"))

            val resolvedBrowser = OperatingSystem.MacOS.getBrowserPath(tempDir).replace('\\', '/')
            assertTrue(resolvedBrowser.contains("cef_server.app/Contents/Frameworks/jcef Helper.app/Contents/MacOS/jcef Helper"))

            // Verify fixed args ordering
            val args = OperatingSystem.MacOS.getFixedArgs(tempDir, listOf("--custom-arg=1"))
            assertEquals(4, args.size)
            val argList = args.toList()
            assertTrue(argList[0].startsWith("--framework-dir-path="))
            assertTrue(argList[1].startsWith("--main-bundle-path="))
            assertTrue(argList[2].startsWith("--browser-subprocess-path="))
            assertEquals("--custom-arg=1", argList[3])
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testLinuxPathResolution() {
        val tempDir = java.nio.file.Files.createTempDirectory("linux_path_test").toFile()
        try {
            // Check default browser fallback
            val defaultHelper = OperatingSystem.Linux.getBrowserPath(tempDir)
            assertTrue(defaultHelper.replace('\\', '/').endsWith("lib/jcef_helper"))

            // Check existing candidate
            val binHelper = java.io.File(tempDir, "bin/jcef_helper")
            binHelper.parentFile?.mkdirs()
            binHelper.createNewFile()
            val resolvedHelper = OperatingSystem.Linux.getBrowserPath(tempDir)
            assertEquals(binHelper.canonicalPath, resolvedHelper)

            // Check resources path with pak
            val libDir = java.io.File(tempDir, "lib")
            libDir.mkdirs()
            val pak = java.io.File(libDir, "resources.pak")
            pak.createNewFile()
            val resolvedResources = OperatingSystem.Linux.getResourcesPath(tempDir)
            assertEquals(libDir.canonicalPath, resolvedResources)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testWindowsPathResolution() {
        val tempDir = java.nio.file.Files.createTempDirectory("windows_path_test").toFile()
        try {
            // Check default browser fallback
            val defaultHelper = OperatingSystem.Windows.getBrowserPath(tempDir)
            assertTrue(defaultHelper.replace('\\', '/').endsWith("bin/jcef_helper.exe"))

            // Check root executable candidate
            val rootHelper = java.io.File(tempDir, "jcef_helper.exe")
            rootHelper.createNewFile()
            val resolvedHelper = OperatingSystem.Windows.getBrowserPath(tempDir)
            assertEquals(rootHelper.canonicalPath, resolvedHelper)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
