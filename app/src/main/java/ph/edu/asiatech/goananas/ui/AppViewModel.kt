package ph.edu.asiatech.goananas.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ph.edu.asiatech.goananas.GoAnanasApp
import ph.edu.asiatech.goananas.data.ScanEntity
import ph.edu.asiatech.goananas.data.UserSettings
import ph.edu.asiatech.goananas.ml.Diagnosis
import ph.edu.asiatech.goananas.notify.ReminderScheduler
import ph.edu.asiatech.goananas.sync.CloudSync
import java.util.concurrent.TimeUnit

/** state of the photograph that is being diagnosed */
sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Working : AnalysisState
    data class Done(val photo: Bitmap, val diagnosis: Diagnosis) : AnalysisState
    data class Failed(val message: String) : AnalysisState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ga = app as GoAnanasApp
    private val repo = ga.repository
    private val settingsStore = ga.settings

    val settings: StateFlow<UserSettings?> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val scans: StateFlow<List<ScanEntity>> = repo.scans.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val classCounts = repo.classCounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val affectedBlocks = repo.affectedBlocks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val dueRechecks = repo.dueRechecks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val blocks = repo.blocks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentTimes = repo.scansSince(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(56))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun scan(id: Long) = repo.scan(id)

    val modelInfo get() = ga.classifier.info
    val cloudEnabled: Boolean get() = CloudSync.isEnabled(getApplication())
    val cloudEmail: String? get() = CloudSync.currentEmail(getApplication())

    private val _analysis = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysis: StateFlow<AnalysisState> = _analysis.asStateFlow()
    var selectedPart: String = "Leaf"

    fun analyze(uri: Uri) {
        _analysis.value = AnalysisState.Working
        viewModelScope.launch {
            _analysis.value = try {
                val photo = withContext(Dispatchers.IO) { loadBitmap(getApplication(), uri) }
                val diagnosis = withContext(Dispatchers.Default) { ga.classifier.classify(photo) }
                AnalysisState.Done(photo, diagnosis)
            } catch (e: Exception) {
                AnalysisState.Failed("The photo could not be analyzed: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun resetAnalysis() { _analysis.value = AnalysisState.Idle }

    fun save(block: String, notes: String, onSaved: (Long) -> Unit) {
        val done = _analysis.value as? AnalysisState.Done ?: return
        viewModelScope.launch {
            val id = repo.save(done.photo, done.diagnosis, selectedPart, block, notes)
            _analysis.value = AnalysisState.Idle
            onSaved(id)
        }
    }

    fun setStatus(id: Long, status: String) = viewModelScope.launch { repo.setStatus(id, status) }
    fun updateNotes(id: Long, notes: String) = viewModelScope.launch { repo.updateNotes(id, notes) }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }

    // ---- profile, settings and account
    fun saveProfile(name: String, role: String, farm: String) = viewModelScope.launch {
        settingsStore.saveProfile(name, role, farm)
        ReminderScheduler.setMonthly(getApplication(), settingsStore.current().monthlyReminder)
    }

    fun setRecheck(on: Boolean) = viewModelScope.launch { settingsStore.setRecheckReminders(on) }
    fun setMonthly(on: Boolean) = viewModelScope.launch {
        settingsStore.setMonthlyReminder(on); ReminderScheduler.setMonthly(getApplication(), on)
    }
    fun setOutbreak(on: Boolean) = viewModelScope.launch { settingsStore.setOutbreakAlerts(on) }
    fun syncNow() = CloudSync.requestSync(getApplication())

    fun register(email: String, password: String, name: String, role: String, farm: String, onResult: (String?) -> Unit) =
        viewModelScope.launch {
            onResult(runCatching {
                CloudSync.register(getApplication(), email, password, name, role, farm)
                settingsStore.saveProfile(name, role, farm)
                ReminderScheduler.setMonthly(getApplication(), true)
            }.exceptionOrNull()?.message)
        }

    fun signIn(email: String, password: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        onResult(runCatching {
            val (name, role, farm) = CloudSync.signIn(getApplication(), email, password)
            settingsStore.saveProfile(name, role, farm)
            ReminderScheduler.setMonthly(getApplication(), settingsStore.current().monthlyReminder)
            CloudSync.requestSync(getApplication())
        }.exceptionOrNull()?.message)
    }

    fun signOut() = viewModelScope.launch {
        CloudSync.signOut(getApplication())
        settingsStore.signOutLocal()
    }

    companion object {
        /** decodes a photo at a manageable size and turns it upright using its EXIF orientation */
        fun loadBitmap(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0) { "the selected file is not an image" }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val bmp = resolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("the image could not be read")
            val rotation = resolver.openInputStream(uri).use { stream ->
                if (stream == null) 0 else when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
            if (rotation == 0) return bmp
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
    }
}
