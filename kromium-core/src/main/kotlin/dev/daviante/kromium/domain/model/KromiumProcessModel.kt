package dev.daviante.kromium.domain.model

/**
 * Defines the process isolation model used by Kromium and Chromium Embedded Framework.
 */
enum class KromiumProcessModel {
    /**
     * Automatically chooses the best process model for the platform.
     *
     * Uses multi-process Chromium architecture where the browser host runs embedded in the host JVM
     * while GPU acceleration, WebGL rasterization, and web renderers run in dedicated `jcef_helper`
     * subprocesses with their own OS Process IDs (PIDs) via `--browser-subprocess-path`.
     *
     * Provides 100% compatibility with Compose Desktop (`SwingPanel`), Swing, JavaFX, and SWT in both
     * Windowed and Off-Screen Rendering (OSR) modes.
     */
    AUTO,

    /**
     * Executes the Chromium browser engine in an external RPC server daemon (`cef_server`).
     *
     * Note: In JetBrains JCEF, `cef_server` RPC mode requires a custom `CefNativeRenderHandler` (shared
     * memory rasterization) and does not support standard Windowed (`CefRendering.DEFAULT`) or standard
     * OSR rendering. Use [AUTO] or [IN_PROCESS] for desktop UI applications.
     */
    OUT_OF_PROCESS,

    /**
     * Executes the Chromium browser engine directly inside the host JVM process.
     *
     * When running in this mode, Kromium applies defensive GPU flags (such as `--disable-direct-composition`
     * and `--disable-gpu-watchdog` on Windows) to prevent swapchain hijacking and Direct3D collisions
     * with the host UI toolkit.
     */
    IN_PROCESS;

    val isOutOfProcess: Boolean get() = this == OUT_OF_PROCESS
    val isInProcess: Boolean get() = this == IN_PROCESS
}
