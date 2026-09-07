package dev.daviante.kromium.data.engine

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
