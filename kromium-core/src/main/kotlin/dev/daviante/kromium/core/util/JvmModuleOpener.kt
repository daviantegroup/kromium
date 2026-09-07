package dev.daviante.kromium.core.util

import dev.daviante.kromium.core.logging.KromiumLogger
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Automatically opens required encapsulated JDK internal packages to ALL-UNNAMED modules
 * at runtime on Java 17, 21, and newer.
 *
 * This eliminates the need for developers to manually pass `--add-opens` JVM flags in their build
 * configurations or runtime launchers when integrating Kromium.
 */
object JvmModuleOpener {

    private const val TAG = "JvmModuleOpener"
    private val opened = AtomicBoolean(false)

    /** Packages in java.desktop required by JCEF across various platforms. */
    private val TARGET_PACKAGES = listOf(
        "sun.lwawt",
        "sun.lwawt.macosx",
        "sun.awt.windows",
        "sun.awt.X11",
        "sun.awt"
    )

    /**
     * Ensures all required platform packages in the `java.desktop` module are opened
     * to all unnamed modules.
     *
     * This method is idempotent and safe to call multiple times.
     */
    fun ensureModulesOpened() {
        if (opened.getAndSet(true)) return

        try {
            val desktopModule = ModuleLayer.boot().findModule("java.desktop").orElse(null)
            if (desktopModule == null) {
                KromiumLogger.d(TAG, "java.desktop module not found in boot layer (Java 8 or non-modular runtime).")
                return
            }

            // Retrieve the internal trusted Lookup instance (IMPL_LOOKUP) via Unsafe
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafeField: Field = unsafeClass.getDeclaredField("theUnsafe")
            theUnsafeField.isAccessible = true
            val unsafe = theUnsafeField.get(null)

            val implLookupField = MethodHandles.Lookup::class.java.getDeclaredField("IMPL_LOOKUP")
            val staticFieldOffsetMethod = unsafeClass.getMethod("staticFieldOffset", Field::class.java)
            val offset = staticFieldOffsetMethod.invoke(unsafe, implLookupField) as Long

            val getObjectMethod = unsafeClass.getMethod("getObject", Any::class.java, Long::class.javaPrimitiveType)
            val trustedLookup = getObjectMethod.invoke(unsafe, MethodHandles.Lookup::class.java, offset) as MethodHandles.Lookup

            // Module.implAddOpensToAllUnnamed(String pn) opens the package to all unnamed modules
            val implAddOpensMethod = trustedLookup.findVirtual(
                Module::class.java,
                "implAddOpensToAllUnnamed",
                MethodType.methodType(Void.TYPE, String::class.java)
            )

            val modulePackages = desktopModule.packages
            var successCount = 0

            for (pkg in TARGET_PACKAGES) {
                if (modulePackages.contains(pkg)) {
                    try {
                        implAddOpensMethod.invokeExact(desktopModule, pkg)
                        successCount++
                        KromiumLogger.d(TAG, "Dynamically opened java.desktop/$pkg to ALL-UNNAMED")
                    } catch (e: Throwable) {
                        KromiumLogger.w(TAG, "Could not dynamically open java.desktop/$pkg: ${e.message}")
                    }
                }
            }

            KromiumLogger.d(TAG, "Dynamic module opening completed: $successCount packages opened.")
        } catch (e: Throwable) {
            KromiumLogger.w(
                TAG,
                "Dynamic module opening failed. If you encounter InaccessibleObjectException or IllegalAccessError, " +
                    "ensure the following JVM arguments are provided:\n" +
                    "  --add-opens=java.desktop/sun.lwawt=ALL-UNNAMED\n" +
                    "  --add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED\n" +
                    "  --add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED\n" +
                    "  --add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED\n" +
                    "Error: ${e.message}"
            )
        }
    }
}
