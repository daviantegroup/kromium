package dev.daviante.kromium.presentation.handler

import java.util.Collections

/**
 * Outcome decision returned by a [KromiumPermissionHandler] in response to a web permission request.
 */
sealed class KromiumPermissionDecision {

    /**
     * Grants access to the requested permissions.
     *
     * @property allowedTypes Optional restricted subset of allowed types.
     * If null, all requested permission types are granted.
     */
    data class Grant(
        val allowedTypes: Set<KromiumPermissionType>? = null
    ) : KromiumPermissionDecision()

    /**
     * Denies access to all requested permissions.
     */
    data object Deny : KromiumPermissionDecision()

    companion object {
        /** Grants all requested permissions. */
        @JvmField
        val GRANT = Grant(null)

        /** Denies all requested permissions. */
        @JvmField
        val DENY = Deny

        /**
         * Selectively grants a specific subset of permissions.
         */
        @JvmStatic
        fun grant(vararg types: KromiumPermissionType): Grant =
            Grant(Collections.unmodifiableSet(types.toSet()))

        /**
         * Selectively grants a specific collection of permissions.
         */
        @JvmStatic
        fun grant(types: Iterable<KromiumPermissionType>): Grant =
            Grant(Collections.unmodifiableSet(types.toSet()))

        /**
         * Explicit deny factory method for Java callers.
         */
        @JvmStatic
        fun deny(): Deny = Deny
    }
}
