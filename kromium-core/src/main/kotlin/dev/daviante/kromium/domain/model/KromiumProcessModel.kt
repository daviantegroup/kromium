package dev.daviante.kromium.domain.model

/**
 * Defines the process isolation model used by Kromium and Chromium Embedded Framework.
 */
enum class KromiumProcessModel {
    /**
     * Automatically chooses the best process model for the platform.
     * Prefers [OUT_OF_PROCESS] if the `cef_server` executable is available in the engine bundle,
     * otherwise falls back to [IN_PROCESS] with defensive GPU and subprocess configuration.
     */
    AUTO,

    /**
     * Executes the Chromium browser engine and GPU pipeline in a dedicated out-of-process server (`cef_server`).
     *
     * In this mode, Chromium runs under a completely separate OS Process ID (PID) from the host JVM.
     * Direct3D/DirectX adapters and swapchains are physically isolated to the `cef_server` process,
     * preventing any GPU device collisions (`DXGI_ERROR_DEVICE_REMOVED`, `0x887a0005`) with the host
     * application (e.g., Jetpack Compose Desktop Skiko or Java2D Direct3D).
     *
     * Native crashes in Chromium or the GPU process cannot crash the host JVM process.
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
