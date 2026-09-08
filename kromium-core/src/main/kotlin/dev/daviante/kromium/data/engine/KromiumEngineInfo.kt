package dev.daviante.kromium.data.engine



import java.io.File

data class KromiumEngineInfo(
    val installDir: File,
    val isInstalled: Boolean,
    val jcefVersion: String,
    val cefVersion: String,
    val chromiumVersion: String
)
