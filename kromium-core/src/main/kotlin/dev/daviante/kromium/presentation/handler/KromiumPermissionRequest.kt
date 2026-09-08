package dev.daviante.kromium.presentation.handler

import java.net.URI

/**
 * Encapsulates an incoming media or device permission request from a web page.
 *
 * @property url Full URL of the frame initiating the permission request.
 * @property origin Extracted web origin (e.g. "https://meet.google.com" or "app://myapp").
 * @property requestedTypes The set of [KromiumPermissionType]s being requested.
 * @property rawFlags The raw Chromium media permission bitmask.
 */
data class KromiumPermissionRequest(
    val url: String,
    val origin: String,
    val requestedTypes: Set<KromiumPermissionType>,
    val rawFlags: Int
) {
    /** True if the request seeks microphone / audio capture access. */
    fun hasAudio(): Boolean = requestedTypes.contains(KromiumPermissionType.AUDIO_CAPTURE)

    /** True if the request seeks camera / webcam video capture access. */
    fun hasVideo(): Boolean = requestedTypes.contains(KromiumPermissionType.VIDEO_CAPTURE)

    /** True if the request seeks screen sharing / desktop video capture access. */
    fun hasScreenShare(): Boolean = requestedTypes.contains(KromiumPermissionType.DESKTOP_VIDEO)

    /** True if the request seeks system audio capture access. */
    fun hasDesktopAudio(): Boolean = requestedTypes.contains(KromiumPermissionType.DESKTOP_AUDIO)

    /** True if the request includes the specified permission type. */
    fun contains(type: KromiumPermissionType): Boolean = requestedTypes.contains(type)

    companion object {
        /**
         * Constructs a [KromiumPermissionRequest] from a URL and raw native bitmask.
         */
        @JvmStatic
        fun from(requestingUrl: String, accessFlags: Int): KromiumPermissionRequest {
            val origin = extractOrigin(requestingUrl)
            val types = KromiumPermissionType.fromFlags(accessFlags)
            return KromiumPermissionRequest(
                url = requestingUrl,
                origin = origin,
                requestedTypes = types,
                rawFlags = accessFlags
            )
        }

        private fun extractOrigin(urlString: String): String {
            return try {
                val uri = URI.create(urlString)
                val scheme = uri.scheme
                val host = uri.host ?: uri.authority
                if (scheme != null && host != null) {
                    val port = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
                    "$scheme://$host$port"
                } else {
                    urlString
                }
            } catch (_: Throwable) {
                urlString
            }
        }
    }
}
