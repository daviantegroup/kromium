package dev.daviante.kromium.domain.model

/**
 * Controls GPU hardware acceleration and graphics rendering strategy.
 */
enum class KromiumGpuMode {
    /**
     * Full hardware acceleration using native OS graphics APIs (DirectX/Direct3D on Windows,
     * Metal on macOS, Vulkan/OpenGL on Linux).
     */
    HARDWARE,

    /**
     * Pure software rendering (CPU-based).
     *
     * Forces `--disable-gpu`, `--use-gl=angle`, and `--use-angle=swiftshader`.
     * Completely eliminates any direct interaction with GPU drivers or hardware Direct3D devices.
     * Recommended as a bulletproof fallback when running in virtual machines or on machines with
     * unstable/overclocked graphics drivers.
     */
    SOFTWARE,

    /**
     * Hardware-accelerated GPU rasterization with CPU compositing.
     * Applies `--disable-gpu-compositing`.
     */
    COMPOSITING_DISABLED,

    /**
     * Direct3D 11 WARP (Windows Advanced Rasterization Platform).
     *
     * Uses Microsoft's high-speed software Direct3D rasterizer. Bypasses vendor hardware drivers
     * and avoids physical GPU device reset/removal errors (`0x887a0005`) while maintaining
     * DirectX pipeline compatibility on Windows.
     */
    ANGLE_WARP
}
