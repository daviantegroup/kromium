package dev.daviante.kromium.domain.exception

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


/**
 * Sealed exception hierarchy for all Kromium-specific errors.
 *
 * This allows consumers to programmatically handle specific failure modes
 * instead of catching generic [IllegalStateException] and parsing messages.
 */
sealed class KromiumException(
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause) {

    /** Kromium has not been initialized. Call [Kromium.initialize] first. */
    data object NotInitialized : KromiumException("Kromium is not initialized. Call Kromium.initialize() first.")

    /** Kromium has been disposed and cannot be used. */
    data object Disposed : KromiumException("Kromium has been disposed and cannot accept new operations.")

    /** The current operating system or CPU architecture is not supported. */
    data class UnsupportedPlatform(
        val os: String?,
        val arch: String?
    ) : KromiumException("Unsupported platform: os=$os, arch=$arch")

    /** The installation directory could not be created or is not writable. */
    data class InstallationFailed(
        val directory: String,
        override val cause: Throwable? = null
    ) : KromiumException("Failed to prepare installation directory: $directory", cause)

    /** No compatible JCEF bundle was found for the current platform in the release. */
    data class NoBundleAvailable(
        val platform: String,
        val releaseTag: String?
    ) : KromiumException("No compatible JCEF bundle found for $platform in release ${releaseTag ?: "latest"}")

    /** The engine bundle download failed. */
    data class DownloadFailed(
        val url: String,
        override val cause: Throwable? = null
    ) : KromiumException("Failed to download engine bundle from: $url", cause)

    /** The downloaded archive's checksum does not match the expected value. */
    data class ChecksumMismatch(
        val expected: String,
        val actual: String
    ) : KromiumException("Archive checksum mismatch — expected: $expected, actual: $actual. The download may be corrupted or tampered with.")

    /** The engine bundle could not be extracted. */
    data class ExtractionFailed(
        val archivePath: String,
        override val cause: Throwable? = null
    ) : KromiumException("Failed to extract engine bundle from: $archivePath", cause)

    /** A malicious path was detected in the archive (Zip-Slip / directory traversal). */
    data class MaliciousArchiveEntry(
        val entryName: String
    ) : KromiumException("Malicious archive entry detected (directory traversal): $entryName")

    /** CEF native bootstrap failed. */
    data class BootstrapFailed(
        override val cause: Throwable? = null
    ) : KromiumException("CEF native bootstrap failed: ${cause?.message ?: "unknown error"}", cause)

    /** The installed JCEF bundle appears corrupted or incomplete. */
    data class InstallationCorrupted(
        val directory: String
    ) : KromiumException("JCEF installation at $directory appears corrupted — re-download required.")

    /** A JavaScript evaluation timed out. */
    data class JsEvaluationTimeout(
        val timeoutMs: Long
    ) : KromiumException("JavaScript evaluation timed out after ${timeoutMs}ms")

    /** Configuration validation failed. */
    data class InvalidConfig(
        val detail: String
    ) : KromiumException("Invalid Kromium configuration: $detail")

    /** A proxy configuration or dynamic switching error occurred. */
    data class ProxyError(
        val detail: String
    ) : KromiumException("Proxy error: $detail")
}
