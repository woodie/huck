package com.netpress.huck.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.delay
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

// Ports zouk's RunningDogAnimation/RunningDogView, both in Sources/ZoukKit/RunningDogView.swift
// -- same file/type split here: RunningDogAnimation decodes GIF frames once (the JVM equivalent
// of Swift's CGImageSource-based frame array), RunningDogView is the actual @Composable, named
// to match the SwiftUI view struct it ports rather than something Compose-flavored like
// "RunningDogImage". Swift steps through the frame array on a 0.1s Timer; RunningDogView below
// does the same on a 100ms LaunchedEffect/delay loop. See docs/COMMENTS.md for why the source
// GIF lives in src/main/resources rather than composeResources/drawable.
private const val FRAME_DELAY_MS = 100L

object RunningDogAnimation {
    val frames: List<ImageBitmap> by lazy { decodeFrames() }

    private fun decodeFrames(): List<ImageBitmap> {
        val stream =
            RunningDogAnimation::class.java.getResourceAsStream("/running_dog.gif")
                ?: return emptyList()

        return stream.use { input ->
            val reader = ImageIO.getImageReadersByFormatName("gif").next()
            reader.setInput(MemoryCacheImageInputStream(input))
            runCatching {
                (0 until reader.getNumImages(true)).map { index ->
                    composited(reader, index).toComposeImageBitmap()
                }
            }.getOrElse { emptyList() }
        }
    }

    // GIF frames are often smaller than the logical screen and meant to be layered over the
    // previous frame rather than read standalone -- painting each onto a full canvas avoids
    // torn/partial frames for GIFs that rely on that layering.
    private fun composited(
        reader: javax.imageio.ImageReader,
        index: Int,
    ): BufferedImage {
        val frame = reader.read(index)
        val width = reader.getWidth(0)
        val height = reader.getHeight(0)
        if (frame.width == width && frame.height == height) return frame

        val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = canvas.createGraphics()
        graphics.drawImage(frame, 0, 0, null)
        graphics.dispose()
        return canvas
    }
}

@Composable
fun RunningDogView(modifier: Modifier = Modifier) {
    val frames = remember { RunningDogAnimation.frames }
    var frameIndex by remember { mutableStateOf(0) }

    if (frames.isEmpty()) {
        AppIconImage(modifier = modifier)
        return
    }

    LaunchedEffect(frames) {
        while (true) {
            delay(FRAME_DELAY_MS)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    Image(bitmap = frames[frameIndex], contentDescription = "Fetching scans", modifier = modifier)
}
