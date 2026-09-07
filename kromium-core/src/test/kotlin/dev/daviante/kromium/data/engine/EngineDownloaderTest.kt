package dev.daviante.kromium.data.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import dev.daviante.kromium.domain.model.*
import dev.daviante.kromium.domain.exception.*

class EngineDownloaderTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun mockClient(responseBody: String): HttpClient {
        val mockEngine = MockEngine { request ->
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/vnd.github+json")
            )
        }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
                json(json, ContentType("application", "vnd.github+json"))
            }
        }
    }

    @Test
    fun testResolvePackageUrlSuccess() = runTest {
        // Mock a GitHub release response containing a JCEF bundle link in the body
        val mockResponse = """
            {
                "tag_name": "v1.0",
                "body": "Download here: https://example.com/jcef-win-x64.tar.gz",
                "assets": []
            }
        """.trimIndent()

        val downloader = EngineDownloader(mockClient(mockResponse))
        
        val platform = PlatformInfo(OperatingSystem.Windows, Architecture.X64)
        val result = downloader.resolvePackageUrl(platform, "test", "test", "v1.0")

        assertEquals("https://example.com/jcef-win-x64.tar.gz", result.bundleUrl)
    }

    @Test
    fun testResolvePackageUrlMacOsArm64() = runTest {
        val mockResponse = """
            {
                "tag_name": "v1.0",
                "body": "MacOS ARM64: https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-25.0.4.1-osx-aarch64-b583.48.tar.gz\nMacOS x64: https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-25.0.4.1-osx-x64-b583.48.tar.gz",
                "assets": []
            }
        """.trimIndent()

        val downloader = EngineDownloader(mockClient(mockResponse))
        val platform = PlatformInfo(OperatingSystem.MacOS, Architecture.Arm64)
        val result = downloader.resolvePackageUrl(platform, "test", "test", "v1.0")

        assertEquals(
            "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-25.0.4.1-osx-aarch64-b583.48.tar.gz",
            result.bundleUrl
        )
    }

    @Test
    fun testResolvePackageUrlMacOsX64() = runTest {
        val mockResponse = """
            {
                "tag_name": "v1.0",
                "body": "MacOS ARM64: https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-25.0.4.1-osx-aarch64-b583.48.tar.gz\nMacOS x64: https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-25.0.4.1-osx-x64-b583.48.tar.gz",
                "assets": []
            }
        """.trimIndent()

        val downloader = EngineDownloader(mockClient(mockResponse))
        val platform = PlatformInfo(OperatingSystem.MacOS, Architecture.X64)
        val result = downloader.resolvePackageUrl(platform, "test", "test", "v1.0")

        assertEquals(
            "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-25.0.4.1-osx-x64-b583.48.tar.gz",
            result.bundleUrl
        )
    }

    @Test
    fun testResolvePackageUrlMacOsAssetFallback() = runTest {
        val mockResponse = """
            {
                "tag_name": "v1.0",
                "body": "No direct links in markdown body",
                "assets": [
                    {
                        "name": "jbr_jcef-25.0.4.1-osx-aarch64-b583.48.tar.gz",
                        "browser_download_url": "https://example.com/download/jbr_jcef-osx-aarch64.tar.gz"
                    }
                ]
            }
        """.trimIndent()

        val downloader = EngineDownloader(mockClient(mockResponse))
        val platform = PlatformInfo(OperatingSystem.MacOS, Architecture.Arm64)
        val result = downloader.resolvePackageUrl(platform, "test", "test", "v1.0")

        assertEquals(
            "https://example.com/download/jbr_jcef-osx-aarch64.tar.gz",
            result.bundleUrl
        )
    }

    @Test
    fun testResolvePackageUrlLinuxArm64() = runTest {
        val mockResponse = """
            {
                "tag_name": "v1.0",
                "body": "Linux aarch64: https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-25.0.4.1-linux-aarch64-b583.48.tar.gz",
                "assets": []
            }
        """.trimIndent()

        val downloader = EngineDownloader(mockClient(mockResponse))
        val platform = PlatformInfo(OperatingSystem.Linux, Architecture.Arm64)
        val result = downloader.resolvePackageUrl(platform, "test", "test", "v1.0")

        assertEquals(
            "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-25.0.4.1-linux-aarch64-b583.48.tar.gz",
            result.bundleUrl
        )
    }

    @Test
    fun testResolvePackageUrlNoBundle() = runTest {
        // Mock a release with NO matching bundle
        val mockResponse = """
            {
                "tag_name": "v1.0",
                "body": "No links here",
                "assets": []
            }
        """.trimIndent()

        val downloader = EngineDownloader(mockClient(mockResponse))
        val platform = PlatformInfo(OperatingSystem.Windows, Architecture.X64)

        assertFailsWith<KromiumException.NoBundleAvailable> {
            downloader.resolvePackageUrl(platform, "test", "test", "v1.0")
        }
    }

    @Test
    fun testResolvePackageUrlRateLimitExceeded() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"message":"API rate limit exceeded"}""",
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/vnd.github+json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
                json(json, ContentType("application", "vnd.github+json"))
            }
        }
        val downloader = EngineDownloader(client)
        val platform = PlatformInfo(OperatingSystem.Windows, Architecture.X64)

        val ex = assertFailsWith<KromiumException.DownloadFailed> {
            downloader.resolvePackageUrl(platform, "test", "test", "v1.0")
        }
        assertTrue(ex.cause?.message?.contains("rate limit", ignoreCase = true) == true)
    }
}
