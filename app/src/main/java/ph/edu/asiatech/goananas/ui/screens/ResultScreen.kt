package ph.edu.asiatech.goananas.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ph.edu.asiatech.goananas.ml.Diagnosis
import ph.edu.asiatech.goananas.ui.AnalysisState
import ph.edu.asiatech.goananas.ui.AppViewModel
import ph.edu.asiatech.goananas.ui.DemoModelBanner
import ph.edu.asiatech.goananas.ui.ProbabilityBars
import ph.edu.asiatech.goananas.ui.SectionCard
import ph.edu.asiatech.goananas.ui.classColor
import ph.edu.asiatech.goananas.ui.pct
import ph.edu.asiatech.goananas.ui.theme.SoftGold
import ph.edu.asiatech.goananas.ui.theme.SoftGreen
import ph.edu.asiatech.goananas.ui.theme.SoftRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(vm: AppViewModel, onBack: () -> Unit, onSaved: (Long) -> Unit, onGuide: (String) -> Unit) {
    val state by vm.analysis.collectAsStateWithLifecycle()
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    val done = state as? AnalysisState.Done
    var showEnhanced by remember { mutableStateOf(false) }
    var block by remember { mutableStateOf(blocks.firstOrNull() ?: "") }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    // the system back button must also clear the result, or the camera screen would open it again
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Diagnosis") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        if (done == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No photo to show.") }
        } else {
        val d: Diagnosis = done.diagnosis
        val c = d.top
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DemoModelBanner(vm.modelInfo.trained)
            Image(
                bitmap = (if (showEnhanced) d.enhancedPreview else done.photo).asImageBitmap(),
                contentDescription = "Target specimen for analysis",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show the enhanced image (CLAHE) used by the model", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(checked = showEnhanced, onCheckedChange = { showEnhanced = it })
            }

            // diagnosis card: green for healthy ("Optimal"), red for a disease or disorder
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (c.isHealthy) SoftGreen else SoftRed).padding(16.dp)) {
                Column {
                    Text(if (c.isHealthy) "Status: Optimal" else "Disease or disorder detected", color = classColor(c), fontWeight = FontWeight.SemiBold)
                    Text(c.displayName, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = classColor(c))
                    Text("${pct(d.confidence)} confidence • ${c.localName}", style = MaterialTheme.typography.bodyMedium)
                    Text("Processed on the phone in ${d.inferenceMs} ms", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            if (d.lowConfidence || d.qualityWarnings.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SoftGold).padding(12.dp)) {
                    Column {
                        if (d.lowConfidence) Text("Low confidence (below 60%). Take another photo of the affected part before acting on this result.", fontWeight = FontWeight.SemiBold)
                        d.qualityWarnings.forEach { Text("• $it") }
                    }
                }
            }
            SectionCard("Recommended action") {
                Text(c.recommendedAction)
                TextButton(onClick = { onGuide(c.label) }) { Text("Read more about ${c.displayName}") }
            }
            SectionCard("Confidence breakdown") { ProbabilityBars(d.ranked) }

            SectionCard("Save this scan") {
                Text("Plant part: ${vm.selectedPart}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(block, { block = it }, label = { Text("Farm block or row (e.g. Block A, Row 3)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (blocks.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        blocks.forEach { b -> AssistChip(onClick = { block = b }, label = { Text(b) }) }
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                if (!c.isHealthy && c.recheckDays > 0) {
                    Text("A reminder to re-check this plant will be set after ${c.recheckDays} days.", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { saving = true; vm.save(block, notes, onSaved) }, enabled = !saving, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text(if (saving) "Saving…" else "Save to history")
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Discard and scan again") }
            }
        }
        }
    }
}
