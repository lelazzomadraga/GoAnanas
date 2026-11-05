package ph.edu.asiatech.goananas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ph.edu.asiatech.goananas.data.ROLES
import ph.edu.asiatech.goananas.ui.AppViewModel
import ph.edu.asiatech.goananas.ui.theme.AlertRed
import ph.edu.asiatech.goananas.ui.theme.LeafGreen

/** first screen: profile, and (when Firebase is set up) sign in or create an account */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    var mode by remember { mutableStateOf(if (vm.cloudEnabled) 1 else 0) } // 0 offline, 1 sign in, 2 register
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(ROLES.first()) }
    var farm by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Go Ananas!", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = LeafGreen)
        Text("Pineapple disease detection using digital image processing and a convolutional neural network.",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))

        if (vm.cloudEnabled) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Sign in", "Register", "Offline").forEachIndexed { i, label ->
                    val m = listOf(1, 2, 0)[i]
                    SegmentedButton(selected = mode == m, onClick = { mode = m; error = null },
                        shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                }
            }
        }

        if (mode != 1) {
            OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Role", fontWeight = FontWeight.SemiBold)
            ROLES.forEach { r -> FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r) }) }
            OutlinedTextField(farm, { farm = it }, label = { Text("Farm code (e.g. DAKO-TAGAYTAY)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), supportingText = { Text("Users with the same farm code share scans in the web dashboard.") })
        }
        if (mode != 0) {
            OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(password, { password = it }, label = { Text("Password (at least 6 characters)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        }
        error?.let { Text(it, color = AlertRed) }

        val valid = when (mode) {
            0 -> name.isNotBlank()
            1 -> email.isNotBlank() && password.length >= 6
            else -> name.isNotBlank() && farm.isNotBlank() && email.isNotBlank() && password.length >= 6
        }
        Button(
            onClick = {
                error = null
                when (mode) {
                    0 -> vm.saveProfile(name, role, farm)
                    1 -> { busy = true; vm.signIn(email, password) { busy = false; error = it } }
                    else -> { busy = true; vm.register(email, password, name, role, farm) { busy = false; error = it } }
                }
            },
            enabled = valid && !busy, modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.height(20.dp)) else Text(listOf("Continue", "Sign in", "Create account")[mode])
        }
        if (!vm.cloudEnabled) {
            Text("Cloud sync is not set up in this build, so your scans are saved on this phone only.",
                style = MaterialTheme.typography.bodySmall)
        } else if (mode == 0) {
            OutlinedButton(onClick = { mode = 1 }, modifier = Modifier.fillMaxWidth()) { Text("I have an account") }
        }
    }
}
