package ph.edu.asiatech.goananas

import android.app.Application
import org.opencv.android.OpenCVLoader
import ph.edu.asiatech.goananas.data.AppDatabase
import ph.edu.asiatech.goananas.data.ScanRepository
import ph.edu.asiatech.goananas.data.SettingsStore
import ph.edu.asiatech.goananas.ml.PineappleClassifier
import ph.edu.asiatech.goananas.notify.Notifier

/** creates the shared objects once (simple manual dependency injection) */
class GoAnanasApp : Application() {
    lateinit var settings: SettingsStore
    lateinit var repository: ScanRepository
    val classifier: PineappleClassifier by lazy { PineappleClassifier(this) }

    override fun onCreate() {
        super.onCreate()
        OpenCVLoader.initLocal()
        settings = SettingsStore(this)
        repository = ScanRepository(this, AppDatabase.get(this).scanDao(), settings)
        Notifier.createChannels(this)
    }
}
