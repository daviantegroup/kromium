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


import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

private const val TAG = "EngineDownloader"

class EngineDownloader(
    private val client: HttpClient = defaultHttpClient()
) {

    data class ResolvedPackage(val bundleUrl: String, val checksumUrl: String?)

    /**
     * Resolves the download URL for the JCEF engine bundle matching the current platform.
     *
     * @throws KromiumException.NoBundleAvailable if no compatible bundle is found
     */
    suspend fun resolvePackageUrl(
        platform: PlatformInfo = PlatformDetector.current(),
        repoOwner: String = JETBRAINS_OWNER,
        repoName: String = JETBRAINS_REPO,
        releaseTag: String? = null
    ): ResolvedPackage {
        val endpoint = if (releaseTag.isNullOrBlank()) {
            "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        } else {
            "https://api.github.com/repos/$repoOwner/$repoName/releases/tags/$releaseTag"
        }

        KromiumLogger.d(TAG, "Fetching release info from: $endpoint")

        val response = client.get(endpoint) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }

        if (response.status.value == 403) {
            throw KromiumException.DownloadFailed(
                endpoint,
                IllegalStateException("GitHub API rate limit exceeded. Try again later or specify a custom releaseTag/URL.")
            )
        } else if (!response.status.isSuccess()) {
            throw KromiumException.DownloadFailed(
                endpoint,
                IllegalStateException("Failed to fetch release info: HTTP ${response.status.value}")
            )
        }

        val release: GitHubRelease = response.body()

        // 1. Scan release body markdown links for direct jcef package urls
        val urlRegex = "(https?://|www.)[-a-zA-Z0-9+&@#/%?=~_|!:.;]*[-a-zA-Z0-9+&@#/%=~_|]".toRegex()
        val allUrls = urlRegex.findAll(release.body).map { it.value }.toList()
        
        val bundleUrls = allUrls
            .filterNot { it.isBlank() || it.endsWith(".checksum", ignoreCase = true) }
            .filter { it.contains("jcef", ignoreCase = true) }

        // Filter candidate list matching OS and Architecture
        val osKeywords = when (platform.os) {
            OperatingSystem.Windows -> listOf("win", "windows")
            OperatingSystem.MacOS -> listOf("osx", "mac", "darwin")
            OperatingSystem.Linux -> listOf("linux")
        }
        val archKeywords = when (platform.arch) {
            Architecture.X64 -> listOf("x64", "x86_64")
            Architecture.Arm64 -> listOf("aarch64", "arm64")
        }

        val matchedUrls = bundleUrls.filter { url ->
            osKeywords.any { kw -> url.contains(kw, ignoreCase = true) } &&
                archKeywords.any { kw -> url.contains(kw, ignoreCase = true) }
        }

        if (matchedUrls.isNotEmpty()) {
            // Prefer non-sdk bundles and tar.gz
            val bestUrl = matchedUrls.sortedWith(
                compareBy<String> { if (it.contains("sdk", ignoreCase = true)) 1 else 0 }
                    .thenBy { if (it.endsWith(".tar.gz", ignoreCase = true)) 0 else 1 }
            ).first()
            KromiumLogger.d(TAG, "Resolved package URL: $bestUrl")
            
            val checksumUrl = allUrls.firstOrNull { it.equals("$bestUrl.checksum", ignoreCase = true) }
            return ResolvedPackage(bestUrl, checksumUrl)
        }

        // 2. Fallback to assets
        val matchedAssets = release.assets.filter { asset ->
            val name = asset.name.lowercase()
            name.contains("jcef") &&
                osKeywords.any { kw -> name.contains(kw, ignoreCase = true) } &&
                archKeywords.any { kw -> name.contains(kw, ignoreCase = true) } &&
                !name.endsWith(".checksum")
        }

        if (matchedAssets.isNotEmpty()) {
            val matchedAsset = matchedAssets.sortedWith(
                compareBy<GitHubRelease.Asset> { if (it.name.contains("sdk", ignoreCase = true)) 1 else 0 }
                    .thenBy { if (it.name.endsWith(".tar.gz", ignoreCase = true)) 0 else 1 }
            ).first()

            if (matchedAsset.downloadUrl.isNotBlank()) {
                KromiumLogger.d(TAG, "Resolved package URL from assets: ${matchedAsset.downloadUrl}")
                val checksumAsset = release.assets.firstOrNull { it.name.equals("${matchedAsset.name}.checksum", ignoreCase = true) }
                return ResolvedPackage(matchedAsset.downloadUrl, checksumAsset?.downloadUrl)
            }
        }

        throw KromiumException.NoBundleAvailable("${platform.os} ${platform.arch}", releaseTag)
    }

    /**
     * Downloads a file from [downloadUrl] to [targetFile] with progress reporting.
     *
     * Uses a 256KB buffer for optimal I/O throughput on large bundles.
     *
     * @throws KromiumException.DownloadFailed on HTTP errors or I/O failures
     * @throws KromiumException.ChecksumMismatch if verification fails
     */
    suspend fun downloadToFile(
        resolvedPackage: ResolvedPackage,
        targetFile: File,
        onProgress: (DownloadProgress) -> Unit
    ) {
        val downloadUrl = resolvedPackage.bundleUrl
        try {
            val response = client.prepareGet(downloadUrl) {
                onDownload { bytesSentTotal, contentLength ->
                    val fraction = if (contentLength != null && contentLength > 0) {
                        (bytesSentTotal.toFloat() / contentLength.toFloat()).coerceIn(0.0f, 1.0f)
                    } else {
                        0.0f
                    }
                    onProgress(DownloadProgress(bytesSentTotal, contentLength, fraction))
                }
            }.execute { httpResponse ->
                if (!httpResponse.status.isSuccess()) {
                    throw KromiumException.DownloadFailed(
                        downloadUrl,
                        RuntimeException("HTTP ${httpResponse.status.value}")
                    )
                }

                val channel: ByteReadChannel = httpResponse.bodyAsChannel()
                val buffer = ByteArray(256 * 1024) // 256KB buffer for large bundles
                FileOutputStream(targetFile).use { output ->
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read > 0) {
                            output.write(buffer, 0, read)
                        }
                    }
                }
                httpResponse.status.isSuccess()
            }

            if (!response) {
                throw KromiumException.DownloadFailed(downloadUrl)
            }

            // Verify checksum if provided
            if (resolvedPackage.checksumUrl != null) {
                verifyChecksum(resolvedPackage.checksumUrl, targetFile)
            } else {
                KromiumLogger.w(TAG, "No checksum URL available for $downloadUrl — skipping verification.")
            }
        } catch (e: KromiumException) {
            throw e
        } catch (e: Throwable) {
            throw KromiumException.DownloadFailed(downloadUrl, e)
        }
    }

    private suspend fun verifyChecksum(checksumUrl: String, file: File) {
        KromiumLogger.d(TAG, "Fetching checksum from: $checksumUrl")
        val checksumContent = try {
            client.get(checksumUrl).bodyAsText()
        } catch (e: Exception) {
            KromiumLogger.w(TAG, "Failed to download checksum file, skipping verification", e)
            return
        }

        // The checksum file typically format is: `<hash>  <filename>` or just `<hash>`
        val expectedHash = checksumContent.trim().substringBefore(" ").substringBefore("\t")
        if (expectedHash.isBlank()) {
            KromiumLogger.w(TAG, "Checksum file was empty or malformed, skipping verification.")
            return
        }

        val algorithm = when (expectedHash.length) {
            128 -> "SHA-512"
            64 -> "SHA-256"
            40 -> "SHA-1"
            32 -> "MD5"
            else -> "SHA-256"
        }

        KromiumLogger.d(TAG, "Computing $algorithm for downloaded archive...")
        val actualHash = computeDigest(file, algorithm)

        if (!expectedHash.equals(actualHash, ignoreCase = true)) {
            file.delete()
            throw KromiumException.ChecksumMismatch(expectedHash, actualHash)
        }
        KromiumLogger.i(TAG, "Checksum verified successfully ($algorithm): $actualHash")
    }

    private fun computeDigest(file: File, algorithm: String): String {
        val digest = java.security.MessageDigest.getInstance(algorithm)
        file.inputStream().use { fis ->
            val buffer = ByteArray(256 * 1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Closes the underlying HTTP client to release connection pool threads.
     * Call this after download operations are complete.
     */
    fun close() {
        try {
            client.close()
        } catch (e: Throwable) {
            KromiumLogger.w(TAG, "Error closing HTTP client", e)
        }
    }

    companion object {
        const val JETBRAINS_OWNER = "JetBrains"
        const val JETBRAINS_REPO = "JetBrainsRuntime"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
            followRedirects = true
            install(ContentNegotiation) {
                json(json)
                json(json, ContentType("application", "vnd.github+json"))
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 300_000 // 5 minutes
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }
        }
    }
}
