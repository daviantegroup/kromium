package dev.daviante.kromium.presentation.handler



/**
 * Details of an authentication request made by a server or proxy.
 */
data class KromiumAuthRequest(
    val isProxy: Boolean,
    val host: String,
    val port: Int,
    val realm: String,
    val scheme: String
)
