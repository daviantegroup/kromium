package dev.daviante.kromium.core.logging

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
