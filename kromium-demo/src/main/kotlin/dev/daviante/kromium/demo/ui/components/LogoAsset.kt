package dev.daviante.kromium.demo.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import javax.imageio.ImageIO

object LogoAsset {
    val imageBitmap: ImageBitmap by lazy {
        val stream = LogoAsset::class.java.getResourceAsStream("/logo.png")
            ?: File("logo.png").takeIf { it.exists() }?.inputStream()
            ?: File("d:/doki_surf/kromium/logo.png").takeIf { it.exists() }?.inputStream()
            ?: error("logo.png could not be located in classpath or filesystem")

        stream.use {
            val bufferedImage = ImageIO.read(it)
                ?: error("Failed to decode logo.png as an image")
            bufferedImage.toComposeImageBitmap()
        }
    }

    val painter: BitmapPainter by lazy {
        BitmapPainter(imageBitmap)
    }
}
