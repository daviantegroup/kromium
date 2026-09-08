package dev.daviante.kromium.core.logging



/**
 * A no-op logger that discards all messages. Useful for tests or production silence.
 */
object NoOpKromiumLogger : KromiumLogger {
    override fun debug(tag: String, message: String) {}
    override fun info(tag: String, message: String) {}
    override fun warn(tag: String, message: String, throwable: Throwable?) {}
    override fun error(tag: String, message: String, throwable: Throwable?) {}
}
