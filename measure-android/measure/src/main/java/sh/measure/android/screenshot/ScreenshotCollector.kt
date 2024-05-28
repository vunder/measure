package sh.measure.android.screenshot

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.core.view.isVisible
import sh.measure.android.Config
import sh.measure.android.isMainThread
import sh.measure.android.logger.LogLevel
import sh.measure.android.logger.Logger
import sh.measure.android.tracing.InternalTrace
import sh.measure.android.utils.ComposeHelper
import sh.measure.android.utils.LowMemoryCheck
import sh.measure.android.utils.ResumedActivityProvider
import sh.measure.android.utils.isSensitiveInputType
import java.io.ByteArrayOutputStream
import kotlin.LazyThreadSafetyMode.NONE

internal class Screenshot(
    val data: ByteArray,
    val extension: String,
)

internal interface ScreenshotCollector {
    /**
     * Attempts to take a screenshot of the visible activity, if any.
     *
     * @return The screenshot as a byte array, or null if the screenshot could not be taken.
     */
    fun takeScreenshot(): Screenshot?
}

/**
 * Captures a screenshot of the currently resumed activity, given the system memory is not running
 * low. The screenshot is captured using the PixelCopy API on Android O and above, and using
 * the Canvas API on older versions. The returned screenshot is compressed webp on supported
 * versions with a fallback to JPEG.
 *
 * The screenshot is masked to hide sensitive text and can also be configured to mask all text.
 */
internal class ScreenshotCollectorImpl(
    private val logger: Logger,
    private val resumedActivityProvider: ResumedActivityProvider,
    private val lowMemoryCheck: LowMemoryCheck,
    private val config: Config,
) : ScreenshotCollector {
    private val maskPaint by lazy(NONE) {
        Paint().apply {
            color = Color.parseColor(config.screenshotMaskHexColor)
            style = Paint.Style.FILL
        }
    }
    private val maskRadius = config.screenshotMaskRadius
    private val webpScreenshotCompression = config.screenshotWebpQuality
    private val jpegScreenshotCompression = config.screenshotWebpQuality

    override fun takeScreenshot(): Screenshot? {
        if (lowMemoryCheck.isLowMemory()) {
            logger.log(
                LogLevel.Debug, "Unable to take screenshot, system has low memory."
            )
            return null
        }

        return resumedActivityProvider.getResumedActivity()?.let {
            if (!isActivityAlive(it)) {
                logger.log(LogLevel.Debug, "Unable to take screenshot, activity is unavailable.")
                return null
            }
            val bitmap = captureBitmap(it) ?: return null
            val (extension: String, compressed: ByteArray) = compressBitmap(bitmap) ?: return null
            logger.log(LogLevel.Debug, "Screenshot taken successfully")
            return Screenshot(data = compressed, extension = extension)
        }
    }

    private fun captureBitmap(activity: Activity): Bitmap? {
        val window = activity.window ?: run {
            logger.log(LogLevel.Debug, "Unable to take screenshot, window is null.")
            return null
        }

        val decorView = window.peekDecorView() ?: run {
            logger.log(LogLevel.Debug, "Unable to take screenshot, decor view is null.")
            return null
        }

        val view = decorView.rootView ?: run {
            logger.log(LogLevel.Debug, "Unable to take screenshot, root view is null.")
            return null
        }

        if (view.width <= 0 || view.height <= 0) {
            logger.log(LogLevel.Debug, "Unable to take screenshot, invalid view bounds.")
            return null
        }

        InternalTrace.beginSection("find-rects-to-mask")
        val rectsToMask = findRectsToMask(view)
        InternalTrace.endSection()
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            captureBitmapUsingPixelCopy(window, bitmap)?.apply {
                rectsToMask.forEach { rect ->
                    val canvas = Canvas(this)
                    val rectF = RectF(rect)
                    canvas.clipRect(rectF)
                    canvas.drawRoundRect(rectF, maskRadius, maskRadius, maskPaint)
                }
            }
        } else {
            captureBitmapUsingCanvas(activity, view, bitmap, rectsToMask)
        }
    }

    private fun findRectsToMask(view: View): List<Rect> {
        val rects = mutableListOf<Rect>()
        recursivelyFindRectsToMask(view, rects)
        return rects
    }

    private fun recursivelyFindRectsToMask(view: View, rectsToMask: MutableList<Rect>) {
        if (!view.isVisible) {
            return
        }
        when {
            view is TextView -> {
                if (view.isSensitiveInputType() || (config.maskAllTextInScreenshots && !view.isClickable)) {
                    val rect = Rect()
                    if (view.getGlobalVisibleRect(rect)) {
                        rectsToMask.add(rect)
                    }
                }
            }

            ComposeHelper.isComposeView(view) -> {
                findComposableRectsToMask(view, rectsToMask)
            }

            view is ViewGroup -> {
                (0 until view.childCount).forEach {
                    recursivelyFindRectsToMask(view.getChildAt(it), rectsToMask)
                }
            }
        }
    }

    private fun findComposableRectsToMask(view: View, rectsToMask: MutableList<Rect>) {
        val semanticsOwner = (view as? RootForTest)?.semanticsOwner ?: return
        val semanticsNodes = semanticsOwner.getAllSemanticsNodes(true)

        semanticsNodes.forEach { node ->
            val hasEditableText = node.config.getOrNull(SemanticsProperties.EditableText) != null
            val isPassword = node.config.getOrNull(SemanticsProperties.Password) != null
            val hasText = node.config.getOrNull(SemanticsProperties.Text) != null
            val isClickable = isNodeClickable(node)

            if (hasEditableText && (isPassword || config.maskAllTextInScreenshots)) {
                rectsToMask.add(node.boundsInWindow.toRect())
            }

            if (hasText && config.maskAllTextInScreenshots && !isClickable) {
                rectsToMask.add(node.boundsInWindow.toRect())
            }
        }
    }

    private fun isNodeClickable(node: SemanticsNode): Boolean {
        return node.config.getOrNull(SemanticsActions.OnClick) != null || node.config.getOrNull(
            SemanticsActions.OnLongClick
        ) != null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun captureBitmapUsingPixelCopy(window: Window, bitmap: Bitmap): Bitmap? {
        val thread = HandlerThread("msr-pixel-copy")
        thread.start()

        try {
            val handler = Handler(thread.looper)
            PixelCopy.request(window, bitmap, { }, handler)
        } catch (e: Throwable) {
            logger.log(LogLevel.Error, "Failed to take screenshot using PixelCopy", e)
            return null
        } finally {
            thread.quit()
        }

        return bitmap
    }

    private fun captureBitmapUsingCanvas(
        activity: Activity,
        view: View,
        bitmap: Bitmap,
        rectsToMask: List<Rect>,
    ): Bitmap {
        try {
            val canvas = Canvas(bitmap)
            if (isMainThread()) {
                view.draw(canvas)
                rectsToMask.forEach { rect ->
                    applyMask(rect, canvas)
                }
            } else {
                activity.runOnUiThread {
                    view.draw(canvas)
                }
            }
        } catch (e: Throwable) {
            logger.log(LogLevel.Error, "Failed to take screenshot using canvas", e)
        }
        return bitmap
    }

    private fun applyMask(rect: Rect, canvas: Canvas) {
        val rectF = RectF(rect)
        canvas.clipRect(rectF)
        canvas.drawRoundRect(rectF, maskRadius, maskRadius, maskPaint)
    }

    private fun compressBitmap(bitmap: Bitmap): Pair<String, ByteArray>? {
        try {
            ByteArrayOutputStream().use { byteArrayOutputStream ->
                val extension = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(
                        Bitmap.CompressFormat.WEBP_LOSSY,
                        webpScreenshotCompression,
                        byteArrayOutputStream
                    )
                    "webp"
                } else {
                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG, jpegScreenshotCompression, byteArrayOutputStream
                    )
                    "jpeg"
                }
                if (byteArrayOutputStream.size() <= 0) {
                    logger.log(LogLevel.Debug, "Screenshot is 0 bytes, discarding")
                    return null
                }
                val byteArray = byteArrayOutputStream.toByteArray()
                return Pair(extension, byteArray)
            }
        } catch (e: Throwable) {
            logger.log(LogLevel.Error, "Failed to take screenshot, compression to PNG failed", e)
            return null
        }
    }

    private fun isActivityAlive(activity: Activity): Boolean {
        return !activity.isFinishing && !activity.isDestroyed
    }
}

private fun androidx.compose.ui.geometry.Rect.toRect(): Rect {
    return Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
}
