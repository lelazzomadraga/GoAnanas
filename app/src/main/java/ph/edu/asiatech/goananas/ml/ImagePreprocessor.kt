package ph.edu.asiatech.goananas.ml

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Same preprocessing as Chapter 3 of the paper (and as model_training/preprocess.py):
 *  1. resize to 224 x 224 with area interpolation
 *  2. 3 x 3 Gaussian filter (noise reduction)
 *  3. CLAHE (clip limit 2.0, 8 x 8 tiles) on the L channel of CIELAB (contrast enhancement)
 *  4. normalization to the MobileNetV2 range [-1, 1]
 */
object ImagePreprocessor {
    const val INPUT_SIZE = 224

    data class Prepared(val input: ByteBuffer, val enhanced: Bitmap)

    fun prepare(source: Bitmap): Prepared {
        val rgba = Mat()
        Utils.bitmapToMat(source.copy(Bitmap.Config.ARGB_8888, false), rgba)
        val rgb = Mat(); Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)

        val resized = Mat()
        Imgproc.resize(rgb, resized, Size(INPUT_SIZE.toDouble(), INPUT_SIZE.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        val blurred = Mat(); Imgproc.GaussianBlur(resized, blurred, Size(3.0, 3.0), 0.0)

        val lab = Mat(); Imgproc.cvtColor(blurred, lab, Imgproc.COLOR_RGB2Lab)
        val channels = ArrayList<Mat>(); Core.split(lab, channels)
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val l2 = Mat(); clahe.apply(channels[0], l2); channels[0] = l2
        val merged = Mat(); Core.merge(channels, merged)
        val enhanced = Mat(); Imgproc.cvtColor(merged, enhanced, Imgproc.COLOR_Lab2RGB)

        val bytes = ByteArray(INPUT_SIZE * INPUT_SIZE * 3)
        enhanced.get(0, 0, bytes)
        val buffer = ByteBuffer.allocateDirect(4 * bytes.size).order(ByteOrder.nativeOrder())
        for (b in bytes) buffer.putFloat((b.toInt() and 0xFF) / 127.5f - 1f)
        buffer.rewind()

        val preview = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val enhancedRgba = Mat(); Imgproc.cvtColor(enhanced, enhancedRgba, Imgproc.COLOR_RGB2RGBA)
        Utils.matToBitmap(enhancedRgba, preview)

        listOf(rgba, rgb, resized, blurred, lab, l2, merged, enhanced, enhancedRgba).forEach { it.release() }
        channels.forEach { it.release() }
        return Prepared(buffer, preview)
    }

    /** simple photo-quality checks so the user can retake poor photographs before trusting the result */
    fun qualityWarnings(source: Bitmap): List<String> {
        val rgba = Mat(); Utils.bitmapToMat(source.copy(Bitmap.Config.ARGB_8888, false), rgba)
        val small = Mat()
        val scale = 512.0 / maxOf(rgba.width(), rgba.height()).coerceAtLeast(1)
        Imgproc.resize(rgba, small, Size(rgba.width() * scale, rgba.height() * scale), 0.0, 0.0, Imgproc.INTER_AREA)
        val gray = Mat(); Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
        val lap = Mat(); Imgproc.Laplacian(gray, lap, CvType.CV_64F)
        val mean = MatOfDouble(); val std = MatOfDouble()
        Core.meanStdDev(lap, mean, std)
        val sharpness = std.toArray()[0].let { it * it }
        val brightness = Core.mean(gray).`val`[0]
        listOf(rgba, small, gray, lap, mean, std).forEach { it.release() }

        val warnings = mutableListOf<String>()
        if (sharpness < BLUR_THRESHOLD) warnings += "The photo looks blurred. Hold the phone steady and tap to focus."
        if (brightness < DARK_THRESHOLD) warnings += "The photo is too dark. Take it in daylight or turn on the flash."
        if (brightness > BRIGHT_THRESHOLD) warnings += "The photo is too bright. Avoid direct sunlight on the lens."
        return warnings
    }

    private const val BLUR_THRESHOLD = 60.0
    private const val DARK_THRESHOLD = 45.0
    private const val BRIGHT_THRESHOLD = 225.0
}
