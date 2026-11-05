package ph.edu.asiatech.goananas.ml

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import ph.edu.asiatech.goananas.data.PineappleClass
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** result of one diagnosis */
data class Diagnosis(
    val probabilities: List<Float>,          // model order (PineappleClass.modelOrder)
    val top: PineappleClass,
    val confidence: Float,                   // 0..1
    val inferenceMs: Long,
    val qualityWarnings: List<String>,
    val enhancedPreview: Bitmap,
) {
    val lowConfidence: Boolean get() = confidence < LOW_CONFIDENCE
    /** classes sorted from highest to lowest probability */
    val ranked: List<Pair<PineappleClass, Float>> get() = PineappleClass.modelOrder.zip(probabilities).sortedByDescending { it.second }

    companion object { const val LOW_CONFIDENCE = 0.60f }
}

data class ModelInfo(val version: String, val trained: Boolean, val testAccuracy: String, val classes: List<String>)

/**
 * Runs the MobileNetV2 model (assets/go_ananas_model.tflite) on the phone, without internet.
 * The labels file and model_info.json are produced by model_training/train_model.py.
 */
class PineappleClassifier(private val context: Context) {
    private val interpreter: Interpreter by lazy {
        Interpreter(loadModel(), Interpreter.Options().apply { setNumThreads(4) })
    }

    val labels: List<String> by lazy {
        context.assets.open(LABELS_FILE).bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    val info: ModelInfo by lazy {
        runCatching {
            val j = JSONObject(context.assets.open(INFO_FILE).bufferedReader().readText())
            ModelInfo(
                version = j.optString("version", "unknown"),
                trained = j.optBoolean("trained", false),
                testAccuracy = j.optString("test_accuracy", "—"),
                classes = labels,
            )
        }.getOrElse { ModelInfo("unknown", false, "—", labels) }
    }

    @Synchronized
    fun classify(bitmap: Bitmap): Diagnosis {
        val warnings = ImagePreprocessor.qualityWarnings(bitmap)
        val prepared = ImagePreprocessor.prepare(bitmap)
        val output = Array(1) { FloatArray(labels.size) }
        val start = System.nanoTime()
        interpreter.run(prepared.input, output)
        val ms = (System.nanoTime() - start) / 1_000_000

        // map the model outputs to the classes by label, so the app stays correct even if the label order changes
        val byLabel = labels.zip(output[0].toList()).toMap()
        val probs = PineappleClass.modelOrder.map { byLabel[it.label] ?: 0f }
        val k = probs.indices.maxBy { probs[it] }
        return Diagnosis(probs, PineappleClass.modelOrder[k], probs[k], ms, warnings, prepared.enhanced)
    }

    private fun loadModel(): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_FILE)
        FileInputStream(fd.fileDescriptor).use { stream ->
            return stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    companion object {
        const val MODEL_FILE = "go_ananas_model.tflite"
        const val LABELS_FILE = "labels.txt"
        const val INFO_FILE = "model_info.json"
    }
}
