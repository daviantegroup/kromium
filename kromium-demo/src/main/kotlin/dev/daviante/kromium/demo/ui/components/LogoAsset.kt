package dev.daviante.kromium.demo.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density
import java.io.File
import javax.imageio.ImageIO

object LogoAsset {

    val painter: Painter by lazy {
        loadSvg() ?: BitmapPainter(imageBitmap)
    }

    val imageBitmap: ImageBitmap by lazy {
        val stream = LogoAsset::class.java.getResourceAsStream("/logo.png")
            ?: File("kromium-demo/src/main/resources/logo.png").takeIf { it.exists() }?.inputStream()
            ?: File("assets/logo.svg").takeIf { it.exists() }?.inputStream()
            ?: error("logo could not be located in classpath or filesystem")

        stream.use {
            val bufferedImage = ImageIO.read(it)
                ?: error("Failed to decode logo as an image")
            bufferedImage.toComposeImageBitmap()
        }
    }

    @Suppress("DEPRECATION")
    private fun loadSvg(): Painter? {
        val stream = LogoAsset::class.java.getResourceAsStream("/logo.svg")
            ?: File("kromium-demo/src/main/resources/logo.svg").takeIf { it.exists() }?.inputStream()
            ?: File("assets/logo.svg").takeIf { it.exists() }?.inputStream()
            ?: return null

        return stream.use {
            try {
                loadSvgPainter(it, Density(1f))
            } catch (_: Throwable) {
                null
            }
        }
    }
}

