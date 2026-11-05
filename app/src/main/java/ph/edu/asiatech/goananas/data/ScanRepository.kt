package ph.edu.asiatech.goananas.data

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ph.edu.asiatech.goananas.ml.Diagnosis
import ph.edu.asiatech.goananas.notify.Notifier
import ph.edu.asiatech.goananas.notify.ReminderScheduler
import ph.edu.asiatech.goananas.sync.CloudSync
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/** keeps the scan history and starts the reminders, outbreak alerts, and cloud sync that follow a diagnosis */
class ScanRepository(
    private val context: Context,
    private val dao: ScanDao,
    private val settings: SettingsStore,
) {
    val scans = dao.observeAll()
    val classCounts = dao.observeClassCounts()
    val affectedBlocks = dao.observeAffectedBlocks()
    val dueRechecks = dao.observeDueRechecks()
    val blocks = dao.observeBlocks()
    fun scan(id: Long) = dao.observeById(id)
    fun scansSince(since: Long) = dao.observeTimesSince(since)

    /** saves the photograph and the diagnosis; returns the new scan id */
    suspend fun save(bitmap: Bitmap, diagnosis: Diagnosis, plantPart: String, block: String, notes: String): Long = withContext(Dispatchers.IO) {
        val uuid = UUID.randomUUID().toString()
        val dir = File(context.filesDir, "scans").apply { mkdirs() }
        val file = File(dir, "$uuid.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }

        val cls = diagnosis.top
        val user = settings.current()
        val now = System.currentTimeMillis()
        val recheckAt = if (!cls.isHealthy && cls.recheckDays > 0) now + TimeUnit.DAYS.toMillis(cls.recheckDays.toLong()) else null
        val entity = ScanEntity(
            uuid = uuid,
            imagePath = file.absolutePath,
            plantPart = plantPart,
            block = block.trim(),
            predictedLabel = cls.label,
            confidence = diagnosis.confidence,
            probabilities = diagnosis.probabilities.joinToString(",") { "%.5f".format(java.util.Locale.US, it) },
            inferenceMs = diagnosis.inferenceMs,
            notes = notes.trim(),
            status = if (cls.isHealthy) ScanStatus.HEALTHY else ScanStatus.OPEN,
            createdAt = now,
            recheckAt = recheckAt,
            userId = CloudSync.currentUserId(context),
        )
        val id = dao.insert(entity)

        if (recheckAt != null && user.recheckReminders) {
            ReminderScheduler.scheduleRecheck(context, id, cls.displayName, entity.block, cls.recheckDays.toLong())
        }
        if (!cls.isHealthy && user.outbreakAlerts && entity.block.isNotEmpty()) {
            val since = now - TimeUnit.DAYS.toMillis(OUTBREAK_WINDOW_DAYS)
            val similar = dao.countSimilar(cls.label, entity.block, since)
            if (similar >= OUTBREAK_THRESHOLD) Notifier.outbreak(context, cls.displayName, entity.block, similar)
        }
        CloudSync.requestSync(context)
        id
    }

    suspend fun setStatus(id: Long, status: String) = withContext(Dispatchers.IO) {
        val scan = dao.getById(id) ?: return@withContext
        dao.update(scan.copy(status = status, synced = false))
        if (status != ScanStatus.OPEN) ReminderScheduler.cancelRecheck(context, id)
        CloudSync.requestSync(context)
    }

    suspend fun updateNotes(id: Long, notes: String) = withContext(Dispatchers.IO) {
        val scan = dao.getById(id) ?: return@withContext
        dao.update(scan.copy(notes = notes.trim(), synced = false))
        CloudSync.requestSync(context)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val scan = dao.getById(id) ?: return@withContext
        File(scan.imagePath).delete()
        ReminderScheduler.cancelRecheck(context, id)
        dao.delete(id)
    }

    companion object {
        /** three or more diseased results of the same class in one block within 14 days trigger an outbreak alert */
        const val OUTBREAK_THRESHOLD = 3
        const val OUTBREAK_WINDOW_DAYS = 14L
    }
}
