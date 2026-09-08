package dev.daviante.kromium.core.logging



import java.util.logging.Level
import java.util.logging.Logger

/**
 * Default logger implementation using [java.util.logging.Logger].
 */
class JulKromiumLogger : KromiumLogger {

    private val logger = Logger.getLogger("Kromium")

    override fun debug(tag: String, message: String) {
        logger.log(Level.FINE, "[$tag] $message")
    }

    override fun info(tag: String, message: String) {
        logger.log(Level.INFO, "[$tag] $message")
    }

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        logger.log(Level.WARNING, "[$tag] $message", throwable)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        logger.log(Level.SEVERE, "[$tag] $message", throwable)
    }
}
