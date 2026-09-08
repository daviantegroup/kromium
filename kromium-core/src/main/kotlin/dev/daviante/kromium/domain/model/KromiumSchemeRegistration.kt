package dev.daviante.kromium.domain.model

import dev.daviante.kromium.presentation.scheme.KromiumAssetHandler

/**
 * Registration entry pairing a scheme and domain name with a [KromiumAssetHandler].
 *
 * @property schemeName The scheme protocol (e.g. "app", "https").
 * @property domainName Optional domain/authority (e.g. "myapp" or null for all domains).
 * @property handler The [KromiumAssetHandler] serving virtual responses for this scheme and domain.
 */
data class KromiumSchemeRegistration(
    val schemeName: String,
    val domainName: String?,
    val handler: KromiumAssetHandler
)
