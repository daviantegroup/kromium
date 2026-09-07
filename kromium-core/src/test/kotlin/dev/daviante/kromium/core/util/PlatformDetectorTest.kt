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
}
