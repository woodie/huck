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
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode
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
            runCatching { composite(reader) }.getOrElse { emptyList() }
        }
    }

    // GIF frames are frequently smaller than the logical screen, positioned at their own
    // (left, top) offset, and meant to be layered over whatever the previous frame left behind
    // rather than read standalone -- drawing every frame at (0, 0) (an earlier version of this
    // did exactly that) renders correctly only for frames that happen to be full-canvas, and
    // shifts every other frame up-left by its real offset, confirmed visually on a real run.
    // This keeps one running canvas across all frames, honoring each frame's offset and GIF89a
    // disposal method (what the canvas should look like after this frame, before the next one
    // draws), which is what an animated GIF's frames actually assume during playback.
    private fun composite(reader: ImageReader): List<ImageBitmap> {
        val canvasWidth = reader.getWidth(0)
        val canvasHeight = reader.getHeight(0)
        val canvas = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = canvas.createGraphics()
        var previousSnapshot: BufferedImage? = null

        return (0 until reader.getNumImages(true)).map { index ->
            val frame = reader.read(index)
            val metadata = reader.getImageMetadata(index)
            val (left, top) = frameOffset(metadata)
            val disposal = disposalMethod(metadata)

            if (disposal == "restoreToPrevious") previousSnapshot = copyOf(canvas)

            graphics.drawImage(frame, left, top, null)
            val rendered = copyOf(canvas)

            when (disposal) {
                "restoreToBackgroundColor" -> {
                    graphics.composite = AlphaComposite.Clear
                    graphics.fillRect(left, top, frame.width, frame.height)
                    graphics.composite = AlphaComposite.SrcOver
                }
                "restoreToPrevious" ->
                    previousSnapshot?.let {
                        graphics.composite = AlphaComposite.Src
                        graphics.drawImage(it, 0, 0, null)
                        graphics.composite = AlphaComposite.SrcOver
                    }
                else -> Unit // "none"/"doNotDispose"/unrecognized -- leave the canvas as-is
            }

            rendered.toComposeImageBitmap()
        }.also { graphics.dispose() }
    }

    private fun copyOf(image: BufferedImage): BufferedImage {
        val copy = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = copy.createGraphics()
        graphics.drawImage(image, 0, 0, null)
        graphics.dispose()
        return copy
    }

    private fun frameOffset(metadata: IIOMetadata): Pair<Int, Int> {
        val descriptor = metadataNode(metadata, "ImageDescriptor")
        val left = descriptor?.getAttribute("imageLeftPosition")?.toIntOrNull() ?: 0
        val top = descriptor?.getAttribute("imageTopPosition")?.toIntOrNull() ?: 0
        return left to top
    }

    private fun disposalMethod(metadata: IIOMetadata): String =
        metadataNode(metadata, "GraphicControlExtension")?.getAttribute("disposalMethod") ?: "none"

    private fun metadataNode(
        metadata: IIOMetadata,
        tagName: String,
    ): IIOMetadataNode? {
        val root = metadata.getAsTree(metadata.nativeMetadataFormatName) as IIOMetadataNode
        return root.getElementsByTagName(tagName).item(0) as? IIOMetadataNode
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
