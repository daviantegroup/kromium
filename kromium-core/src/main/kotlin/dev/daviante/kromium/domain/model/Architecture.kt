package dev.daviante.kromium.domain.model



import java.util.Locale

/**
 * CPU architecture identifier.
 */
sealed class Architecture(val name: String, private vararg val aliases: String) {
    data object X64 : Architecture("x64", "amd64", "x86_64", "x64")
    data object Arm64 : Architecture("arm64", "arm64", "aarch64")

    fun matches(archName: String): Boolean {
        val lower = archName.lowercase(Locale.ENGLISH)
        return aliases.any { lower.contains(it) }
    }

    override fun toString(): String = when (this) {
        X64 -> "x64"
        Arm64 -> "arm64"
    }

    companion object {
        fun fromSystem(archName: String = System.getProperty("os.arch") ?: ""): Architecture? {
            return when {
                X64.matches(archName) -> X64
                Arm64.matches(archName) -> Arm64
                else -> null
            }
        }
    }
}
