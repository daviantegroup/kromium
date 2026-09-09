package dev.daviante.kromium.data.engine

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.io.File

class EngineRegistryTest {

    @Test
    fun testDefaultInstallDir() {
        val dir = EngineRegistry.defaultInstallDir()
        assertNotNull(dir, "Default install dir must not be null")
        assertTrue(
            dir.path.contains("bundle", ignoreCase = true) || dir.path.contains("jcef", ignoreCase = true),
            "Install directory path should reflect its purpose"
        )
    }

    @Test
    fun testInstallationMarking() {
        // Use standard java temp dir for test
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kromium-test-registry-${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            assertFalse(EngineRegistry.isInstalled(tempDir), "Should not be installed initially")

            EngineRegistry.markInstalled(tempDir)

            // Single partial binary should NOT qualify as fully installed
            File(tempDir, "jcef.dll").createNewFile()
            File(tempDir, "libcef.so").createNewFile()
            val platform = dev.daviante.kromium.core.util.PlatformDetector.current()
            if (platform.os == dev.daviante.kromium.domain.model.OperatingSystem.Windows || platform.os == dev.daviante.kromium.domain.model.OperatingSystem.Linux) {
                assertFalse(EngineRegistry.isInstalled(tempDir), "Should NOT be installed when only one of the required libraries is present")
            }

            // Create complete dummy files to simulate a real installation based on OS
            File(tempDir, "libcef.dll").createNewFile()
            File(tempDir, "libjcef.so").createNewFile()
            val macDir = File(tempDir, "Chromium Embedded Framework.framework")
            macDir.mkdirs()

            assertTrue(EngineRegistry.isInstalled(tempDir), "Should be installed after marking and placing complete binaries")

        } finally {
            tempDir.deleteRecursively()
        }
    }
}
