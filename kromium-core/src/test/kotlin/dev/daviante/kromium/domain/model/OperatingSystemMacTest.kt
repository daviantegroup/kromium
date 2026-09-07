package dev.daviante.kromium.domain.model

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OperatingSystemMacTest {

    @Test
    fun testEnsureMacFrameworkLinksCreatesSymlinksOrCopies() {
        val tempDir = Files.createTempDirectory("kromium_mac_test").toFile()
        try {
            val nestedFrameworks = File(tempDir, "Frameworks/cef_server.app/Contents/Frameworks").apply { mkdirs() }
            val dummyFramework = File(nestedFrameworks, "Chromium Embedded Framework.framework").apply {
                mkdirs()
                File(this, "dummy.txt").writeText("cef framework content")
            }
            val dummyHelper = File(nestedFrameworks, "jcef Helper.app").apply {
                mkdirs()
                File(this, "dummy.txt").writeText("helper content")
            }

            // Run the method
            OperatingSystem.MacOS.ensureMacFrameworkLinks(tempDir)

            val frameworksDir = File(tempDir, "Frameworks")
            val linkedFramework = File(frameworksDir, "Chromium Embedded Framework.framework")
            val linkedHelper = File(frameworksDir, "jcef Helper.app")

            assertTrue(linkedFramework.exists(), "Chromium Embedded Framework.framework should exist in Frameworks/")
            assertTrue(linkedHelper.exists(), "jcef Helper.app should exist in Frameworks/")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
