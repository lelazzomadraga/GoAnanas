package ph.edu.asiatech.goananas.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ph.edu.asiatech.goananas.data.PineappleClass
import ph.edu.asiatech.goananas.data.ScanStatus
import ph.edu.asiatech.goananas.ui.AppViewModel
import ph.edu.asiatech.goananas.ui.ProbabilityBars
import ph.edu.asiatech.goananas.ui.ScanRow
import ph.edu.asiatech.goananas.ui.SectionCard
import ph.edu.asiatech.goananas.ui.StatusBadge
import ph.edu.asiatech.goananas.ui.classColor
import ph.edu.asiatech.goananas.ui.dateOnly
import ph.edu.asiatech.goananas.ui.dateTime
import ph.edu.asiatech.goananas.ui.pct
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppViewModel, onOpenScan: (Long) -> Unit) {
    val scans by vm.scans.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<PineappleClass?>(null) }
    var openOnly by remember { mutableStateOf(false) }
    val shown = scans.filter { s ->
        (filter == null || s.pineappleClass == filter) &&
            (!openOnly || s.status == ScanStatus.OPEN) &&
            (query.isBlank() || listOf(s.block, s.notes, s.plantPart, s.pineappleClass.displayName).any { it.contains(query, ignoreCase = true) })
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Scan history") })
        OutlinedTextField(
            query, { query = it }, leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
            placeholder = { Text("Search block, notes, or disease") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = filter == null && !openOnly, onClick = { filter = null; openOnly = false }, label = { Text("All") })
            FilterChip(selected = openOnly, onClick = { openOnly = !openOnly }, label = { Text("Needs action") })
            PineappleClass.modelOrder.forEach { c ->
                FilterChip(selected = filter == c, onClick = { filter = if (filter == c) null else c }, label = { Text(c.displayName) })
            }
        }
        Text("${shown.size} of ${scans.size} scans", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(shown, key = { it.id }) { s ->
                ScanRow(s) { onOpenScan(s.id) }
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onGuide: (String) -> Unit) {
    val scanFlow = remember(id) { vm.scan(id) }
    val scan by scanFlow.collectAsStateWithLifecycle(initialValue = null)
    var notes by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(scan?.id) { scan?.let { notes = it.notes } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Scan details") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") } },
        )
        val s = scan
        if (s == null) {
            Text("This scan was deleted.", Modifier.padding(16.dp))
        } else {
            val c = s.pineappleClass
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(File(s.imagePath), contentDescription = c.displayName, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(c.displayName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = classColor(c))
                        Text("${pct(s.confidence)} confidence • ${s.plantPart}${if (s.block.isNotBlank()) " • ${s.block}" else ""}")
                        Text(dateTime(s.createdAt), style = MaterialTheme.typography.bodySmall)
                    }
                    StatusBadge(s.status)
                }
                if (s.recheckAt != null && s.status == ScanStatus.OPEN) {
                    Text("Re-check reminder: ${dateOnly(s.recheckAt)}", style = MaterialTheme.typography.bodyMedium)
                }
                SectionCard("Recommended action") {
                    Text(c.recommendedAction)
                    TextButton(onClick = { onGuide(c.label) }) { Text("Disease guide") }
                }
                SectionCard("Confidence breakdown") { ProbabilityBars(PineappleClass.modelOrder.zip(s.probabilityList).sortedByDescending { it.second }) }
                if (!c.isHealthy) {
                    SectionCard("Update status") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.setStatus(s.id, ScanStatus.RECHECKED) }, Modifier.weight(1f)) { Text("Rechecked") }
                            Button(onClick = { vm.setStatus(s.id, ScanStatus.RESOLVED) }, Modifier.weight(1f)) { Text("Resolved") }
                        }
                        if (s.status != ScanStatus.OPEN) TextButton(onClick = { vm.setStatus(s.id, ScanStatus.OPEN) }) { Text("Reopen") }
                    }
                }
                SectionCard("Notes") {
                    OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { vm.updateNotes(s.id, notes) }, enabled = notes != s.notes) { Text("Save notes") }
                }
                Text(
                    "Processing time: ${s.inferenceMs} ms • ${if (s.synced) "Synced to the cloud" else "Saved on this phone"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this scan?") },
            text = { Text("The photo and diagnosis will be removed from this phone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(id); onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
