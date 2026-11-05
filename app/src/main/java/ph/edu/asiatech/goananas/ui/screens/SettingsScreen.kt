package ph.edu.asiatech.goananas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ph.edu.asiatech.goananas.BuildConfig
import ph.edu.asiatech.goananas.data.ROLES
import ph.edu.asiatech.goananas.ui.AppViewModel
import ph.edu.asiatech.goananas.ui.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(ROLES.first()) }
    var farm by remember { mutableStateOf("") }
    LaunchedEffect(s?.name, s?.role, s?.farmCode) { s?.let { name = it.name; role = it.role; farm = it.farmCode } }
    val info = vm.modelInfo

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings") })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard("Profile") {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ROLES.forEach { r -> FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r) }) }
                OutlinedTextField(farm, { farm = it }, label = { Text("Farm code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.saveProfile(name, role, farm) }, enabled = name.isNotBlank()) { Text("Save profile") }
            }
            SectionCard("Notifications") {
                ToggleRow("Re-check reminders for diseased plants", s?.recheckReminders ?: true) { vm.setRecheck(it) }
                ToggleRow("Monthly inspection reminder", s?.monthlyReminder ?: true) { vm.setMonthly(it) }
                ToggleRow("Outbreak alert (3 or more cases in one block within 14 days)", s?.outbreakAlerts ?: true) { vm.setOutbreak(it) }
            }
            SectionCard("Cloud account") {
                if (vm.cloudEnabled) {
                    Text(vm.cloudEmail?.let { "Signed in as $it" } ?: "Not signed in. Scans stay on this phone.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.syncNow() }) { Text("Sync now") }
                        OutlinedButton(onClick = { vm.signOut() }) { Text("Sign out") }
                    }
                } else {
                    Text("Cloud sync is not set up in this build (no google-services.json). All scans are saved on this phone.",
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { vm.signOut() }) { Text("Change profile") }
                }
            }
            SectionCard("Model") {
                Text("Model: MobileNetV2 (TensorFlow Lite), input 224 × 224 × 3, six classes")
                Text("Version: ${info.version}${if (!info.trained) " (demo, untrained)" else ""}")
                Text("Test accuracy reported by training: ${info.testAccuracy}")
                Text("Classes: ${info.classes.joinToString()}", style = MaterialTheme.typography.bodySmall)
            }
            SectionCard("About") {
                Text("Go Ananas! ${BuildConfig.VERSION_NAME}")
                Text("Detection of Pineapple (Ananas comosus) Diseases Using Digital Image Processing and Convolutional Neural Network",
                    style = MaterialTheme.typography.bodySmall)
                Text("BS Computer Engineering, Asia Technological School of Science and Arts", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
