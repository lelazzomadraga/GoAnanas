package ph.edu.asiatech.goananas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ph.edu.asiatech.goananas.ui.AppViewModel
import ph.edu.asiatech.goananas.ui.DemoModelBanner
import ph.edu.asiatech.goananas.ui.ScanRow
import ph.edu.asiatech.goananas.ui.SectionCard
import ph.edu.asiatech.goananas.ui.StatTile
import ph.edu.asiatech.goananas.ui.theme.AlertRed
import ph.edu.asiatech.goananas.ui.theme.FreshGreen
import ph.edu.asiatech.goananas.ui.theme.LeafGreen
import ph.edu.asiatech.goananas.ui.theme.PineGold

@Composable
fun HomeScreen(vm: AppViewModel, onScan: () -> Unit, onOpenScan: (Long) -> Unit, onSeeHistory: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scans by vm.scans.collectAsStateWithLifecycle()
    val due by vm.dueRechecks.collectAsStateWithLifecycle()
    val diseased = scans.count { !it.pineappleClass.isHealthy }
    val now = System.currentTimeMillis()

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(LeafGreen, FreshGreen))).padding(20.dp),
            ) {
                Column {
                    Text("Magandang araw, ${settings?.name?.ifBlank { "farmer" } ?: "farmer"}!", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(listOfNotNull(settings?.role, settings?.farmCode?.ifBlank { null }).joinToString(" • "),
                        color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = PineGold, contentColor = Color(0xFF3A2A00))) {
                        Icon(Icons.Filled.CameraAlt, null, Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Scan a pineapple", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { DemoModelBanner(vm.modelInfo.trained) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("${scans.size}", "Total scans", LeafGreen, Modifier.weight(1f))
                StatTile("$diseased", "Diseased", AlertRed, Modifier.weight(1f))
                StatTile("${due.count { (it.recheckAt ?: Long.MAX_VALUE) <= now }}", "Re-checks due", PineGold, Modifier.weight(1f))
            }
        }
        if (due.isNotEmpty()) {
            item {
                SectionCard("Plants to re-check") {
                    due.take(3).forEach { ScanRow(it) { onOpenScan(it.id) } }
                }
            }
        }
        item {
            SectionCard("Recent scans") {
                if (scans.isEmpty()) {
                    Text("No scans yet. Tap \"Scan a pineapple\" to check your first plant.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    scans.take(5).forEach { ScanRow(it) { onOpenScan(it.id) } }
                    TextButton(onClick = onSeeHistory) { Text("See all scans") }
                }
            }
        }
        item {
            SectionCard("Tips for a good photo") {
                listOf(
                    "Take the photo 30 to 60 cm from the plant, in daylight.",
                    "Fill the frame with the affected part: leaf, crown, heart, base, or fruit.",
                    "Avoid shadows across the leaves and direct sunlight on the lens.",
                    "Take a second photo if the confidence is below 60%.",
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

