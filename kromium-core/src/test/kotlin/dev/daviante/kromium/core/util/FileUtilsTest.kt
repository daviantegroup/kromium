package dev.daviante.kromium.core.util

import kotlin.test.*
import java.io.File
import java.nio.file.Files

class FileUtilsTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("kromium_fileutils_test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testSanitizeDirectoryValid() {
        val subDir = File(tempDir, "validSub")
        subDir.mkdirs()
        val sanitized = FileUtils.sanitizeDirectory(subDir)
        assertNotNull(sanitized)
        assertEquals(subDir.canonicalPath, sanitized.canonicalPath)
    }

    @Test
    fun testSanitizeDirectoryRejectsTraversal() {
        val traversalDir = File(tempDir, "../evil")
        val sanitized = FileUtils.sanitizeDirectory(traversalDir)
        assertNull(sanitized, "Sanitize must reject paths containing ..")
    }

    @Test
    fun testSanitizeDirectoryRejectsRoots() {
        for (root in File.listRoots()) {
            val sanitized = FileUtils.sanitizeDirectory(root)
            assertNull(sanitized, "Sanitize must reject filesystem root: ${root.path}")
        }
    }

    @Test
    fun testSanitizeDirectoryRejectsUserHome() {
        val userHome = System.getProperty("user.home")
        if (!userHome.isNullOrBlank()) {
            val sanitized = FileUtils.sanitizeDirectory(File(userHome))
            assertNull(sanitized, "Sanitize must reject user home root directory directly")
        }
    }

    @Test
    fun testResolveChildValid() {
        val child = FileUtils.resolveChild(tempDir, "subdir/file.txt")
        assertNotNull(child)
        assertTrue(child.canonicalPath.startsWith(tempDir.canonicalPath))
    }

    @Test
    fun testResolveChildRejectsTraversal() {
        val child = FileUtils.resolveChild(tempDir, "../escaped.txt")
        assertNull(child, "resolveChild must reject path traversal")
    }

    @Test
    fun testEnsureAndDeleteDirectory() {
        val target = File(tempDir, "to_delete")
        assertTrue(FileUtils.ensureDirectory(target))
        assertTrue(target.exists())
        assertTrue(target.isDirectory)

        assertTrue(FileUtils.deleteDirectory(target))
        assertFalse(target.exists())
    }

    @Test
    fun testDeleteDirectoryRefusesRoot() {
        for (root in File.listRoots()) {
            assertFalse(FileUtils.deleteDirectory(root), "deleteDirectory must refuse filesystem root")
        }
    }

    @Test
    fun testDeleteDirectoryRefusesTraversal() {
        val traversal = File(tempDir, "../evil_delete")
        assertFalse(FileUtils.deleteDirectory(traversal), "deleteDirectory must refuse path traversal")
    }
}
