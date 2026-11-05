package ph.edu.asiatech.goananas

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import ph.edu.asiatech.goananas.notify.Notifier
import ph.edu.asiatech.goananas.ui.GoAnanasRoot
import ph.edu.asiatech.goananas.ui.theme.GoAnanasTheme

class MainActivity : ComponentActivity() {
    /** scan opened from a notification */
    private val openScanId = mutableStateOf<Long?>(null)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        readIntent(intent)
        setContent {
            GoAnanasTheme {
                GoAnanasRoot(openScanId = openScanId.value, onOpenedScan = { openScanId.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        val id = intent?.getLongExtra(Notifier.EXTRA_SCAN_ID, -1L) ?: -1L
        if (id > 0) openScanId.value = id
    }
}
