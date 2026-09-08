package dev.daviante.kromium.core.logging



import java.util.logging.Level
import java.util.logging.Logger

/**
 * Logging interface for Kromium internals.
 *
 * Consumers can replace the default [JulKromiumLogger] with their own implementation
 * by setting [KromiumLogger.instance]:
 * ```kotlin
 * KromiumLogger.instance = MyCustomLogger()
 * ```
 */
interface KromiumLogger {

    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)

    companion object {
        /**
         * Global logger instance used by all Kromium internals.
         * Replace with a custom implementation to integrate with your logging framework.
         */
        @Volatile
        var instance: KromiumLogger = JulKromiumLogger()
            @JvmStatic set

        // Convenience delegation methods
        @JvmStatic fun d(tag: String, message: String) = instance.debug(tag, message)
        @JvmStatic fun i(tag: String, message: String) = instance.info(tag, message)
        @JvmStatic fun w(tag: String, message: String, throwable: Throwable? = null) = instance.warn(tag, message, throwable)
        @JvmStatic fun e(tag: String, message: String, throwable: Throwable? = null) = instance.error(tag, message, throwable)
    }
}

