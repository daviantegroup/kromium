package dev.daviante.kromium.presentation.handler

/**
 * Functional interface for intercepting and deciding on media/hardware permission requests.
 *
 * Compatible with Kotlin lambdas `{ request -> ... }` and Java lambdas `request -> ...`.
 */
fun interface KromiumPermissionHandler {

    /**
     * Evaluates an incoming [KromiumPermissionRequest] and returns a [KromiumPermissionDecision].
     *
     * @param request Details of the requesting web frame, origin, and requested permission types.
     * @return [KromiumPermissionDecision.GRANT] to permit access, or [KromiumPermissionDecision.DENY] to reject.
     */
    fun onRequestPermission(request: KromiumPermissionRequest): KromiumPermissionDecision

    companion object {
        /**
         * Creates a handler that automatically grants all media and device permission requests.
         */
        @JvmStatic
        fun grantAll(): KromiumPermissionHandler =
            KromiumPermissionHandler { KromiumPermissionDecision.GRANT }

        /**
         * Creates a handler that automatically denies all media and device permission requests.
         */
        @JvmStatic
        fun denyAll(): KromiumPermissionHandler =
            KromiumPermissionHandler { KromiumPermissionDecision.DENY }

        /**
         * Creates a handler that automatically grants requests originating from specified trusted domains/origins,
         * denying all other origins.
         *
         * @param allowedOrigins Set of permitted origins (e.g. "https://meet.company.com", "app://myapp", "localhost").
         */
        @JvmStatic
        fun forOrigins(allowedOrigins: Set<String>): KromiumPermissionHandler =
            KromiumPermissionHandler { request ->
                val origin = request.origin.lowercase().trim()
                val isAllowed = allowedOrigins.any { allowed ->
                    val norm = allowed.lowercase().trim()
                    origin == norm || origin == "https://$norm" || origin == "http://$norm" || origin.endsWith(".$norm")
                }
                if (isAllowed) KromiumPermissionDecision.GRANT else KromiumPermissionDecision.DENY
            }

        /**
         * Creates a handler that automatically grants requests originating from specified trusted domains/origins.
         */
        @JvmStatic
        fun forOrigins(vararg allowedOrigins: String): KromiumPermissionHandler =
            forOrigins(allowedOrigins.toSet())
    }
}
