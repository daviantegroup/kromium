package dev.daviante.kromium.domain.model

/**
 * Configuration for registering a custom protocol scheme with the underlying Chromium engine.
 *
 * Custom schemes (e.g. `app`, `kromium`, `local`) must be registered with Chromium's internal
 * security manager prior to engine initialization.
 *
 * @property schemeName The name of the custom protocol scheme (e.g. "app" for `app://...`).
 * @property isStandard If true, the scheme will be treated as a standard scheme with authority/host component.
 * @property isLocal If true, the scheme will be treated as local, similar to `file://`.
 * @property isDisplayIsolated If true, the scheme can only be displayed from other origins with the same scheme.
 * @property isSecure If true, the scheme will be treated as secure (HTTPS-equivalent: Web Crypto, Service Workers, Storage APIs).
 * @property isCorsEnabled If true, cross-origin requests (CORS) from/to this scheme are permitted via `fetch()` and XHR.
 * @property isCspBypassing If true, Content Security Policy (CSP) enforcements will be bypassed for this scheme.
 * @property isFetchEnabled If true, the Fetch API is allowed for this scheme.
 */
data class KromiumCustomScheme @JvmOverloads constructor(
    val schemeName: String,
    val isStandard: Boolean = true,
    val isLocal: Boolean = true,
    val isDisplayIsolated: Boolean = false,
    val isSecure: Boolean = true,
    val isCorsEnabled: Boolean = true,
    val isCspBypassing: Boolean = false,
    val isFetchEnabled: Boolean = true
) {
    init {
        require(schemeName.isNotBlank()) { "Scheme name cannot be blank" }
        require(!schemeName.contains("://")) { "Scheme name should not contain '://' (e.g. use 'app', not 'app://')" }
    }

    companion object {
        /**
         * Creates a secure, local custom scheme with CORS and standard URL handling enabled.
         */
        @JvmStatic
        fun secure(schemeName: String): KromiumCustomScheme =
            KromiumCustomScheme(
                schemeName = schemeName,
                isStandard = true,
                isLocal = true,
                isDisplayIsolated = false,
                isSecure = true,
                isCorsEnabled = true,
                isCspBypassing = false,
                isFetchEnabled = true
            )

        /**
         * Creates a standard custom scheme without local origin isolation.
         */
        @JvmStatic
        fun standard(schemeName: String): KromiumCustomScheme =
            KromiumCustomScheme(
                schemeName = schemeName,
                isStandard = true,
                isLocal = false,
                isDisplayIsolated = false,
                isSecure = false,
                isCorsEnabled = true,
                isCspBypassing = false,
                isFetchEnabled = true
            )
    }
}
