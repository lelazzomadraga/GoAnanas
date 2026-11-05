package ph.edu.asiatech.goananas.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ph.edu.asiatech.goananas.data.PineappleClass
import ph.edu.asiatech.goananas.ui.AppViewModel
import ph.edu.asiatech.goananas.ui.ScanRow
import ph.edu.asiatech.goananas.ui.SectionCard
import ph.edu.asiatech.goananas.ui.StatTile
import ph.edu.asiatech.goananas.ui.classColor
import ph.edu.asiatech.goananas.ui.theme.AlertRed
import ph.edu.asiatech.goananas.ui.theme.FreshGreen
import ph.edu.asiatech.goananas.ui.theme.LeafGreen
import ph.edu.asiatech.goananas.ui.theme.PineGold
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: AppViewModel, onOpenScan: (Long) -> Unit) {
    val counts by vm.classCounts.collectAsStateWithLifecycle()
    val blocks by vm.affectedBlocks.collectAsStateWithLifecycle()
    val due by vm.dueRechecks.collectAsStateWithLifecycle()
    val times by vm.recentTimes.collectAsStateWithLifecycle()
    val total = counts.sumOf { it.total }
    val healthy = counts.firstOrNull { it.predictedLabel == PineappleClass.HEALTHY.label }?.total ?: 0
    val perClass = PineappleClass.modelOrder.map { c -> c to (counts.firstOrNull { it.predictedLabel == c.label }?.total ?: 0) }

    // scans per week for the last 8 weeks (oldest first)
    val now = System.currentTimeMillis()
    val week = TimeUnit.DAYS.toMillis(7)
    val weekly = (7 downTo 0).map { w -> times.count { t -> t > now - (w + 1) * week && t <= now - w * week } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Farm dashboard") })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("$total", "Plants scanned", LeafGreen, Modifier.weight(1f))
                    StatTile(if (total == 0) "—" else "${healthy * 100 / total}%", "Healthy", FreshGreen, Modifier.weight(1f))
                    StatTile("${total - healthy}", "With disease", AlertRed, Modifier.weight(1f))
                }
            }
            item {
                SectionCard("Results per class") {
                    val max = (perClass.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
                    perClass.forEach { (c, n) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.displayName, Modifier.width(150.dp), style = MaterialTheme.typography.bodyMedium)
                            Canvas(Modifier.weight(1f).height(18.dp)) {
                                drawRoundRect(Color(0xFFECEFF1), cornerRadius = CornerRadius(8f, 8f))
                                drawRoundRect(classColor(c), size = Size(size.width * n / max, size.height), cornerRadius = CornerRadius(8f, 8f))
                            }
                            Text("  $n", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                SectionCard("Scans per week (last 8 weeks)") {
                    val max = (weekly.maxOrNull() ?: 0).coerceAtLeast(1)
                    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                        val gap = 10f
                        val w = (size.width - gap * (weekly.size - 1)) / weekly.size
                        weekly.forEachIndexed { i, n ->
                            val h = size.height * n / max
                            drawRoundRect(
                                if (i == weekly.lastIndex) PineGold else LeafGreen,
                                topLeft = Offset(i * (w + gap), size.height - h), size = Size(w, h), cornerRadius = CornerRadius(6f, 6f),
                            )
                        }
                    }
                    Text("Bars show the number of scans per week; the gold bar is the current week.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                SectionCard("Most affected blocks") {
                    if (blocks.isEmpty()) Text("No diseased plants recorded with a block name yet.")
                    blocks.take(5).forEachIndexed { i, b ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("${i + 1}. ${b.block}", Modifier.weight(1f))
                            Text("${b.total} diseased", color = AlertRed, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                SectionCard("Plants waiting for re-check") {
                    if (due.isEmpty()) Text("No plants are waiting for a re-check.")
                    due.take(10).forEach { ScanRow(it) { onOpenScan(it.id) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Disease guide") })
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Text("The six conditions recognized by Go Ananas!. Tap one to see its symptoms, cause, and management.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            PineappleClass.entries.forEach { c ->
                item {
                    Row(Modifier.fillMaxWidth().clickable { onOpen(c.label) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.displayName, fontWeight = FontWeight.SemiBold, color = classColor(c))
                            Text("${c.localName} • photograph the ${c.partToPhotograph.lowercase()}", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                    HorizontalDivider()
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text("Limitation: the app reads only what is visible on the surface. Internal conditions such as \"kalawang\" " +
                    "cannot be detected, and the result is a screening aid, not a laboratory diagnosis.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideDetailScreen(label: String, onBack: () -> Unit) {
    val c = PineappleClass.fromLabel(label)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(c.displayName) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(c.localName, style = MaterialTheme.typography.titleMedium, color = classColor(c))
            SectionCard("What it looks like") { Text(c.symptoms) }
            SectionCard("Cause") { Text(c.cause) }
            SectionCard("What to do") { c.management.forEach { Text("• $it") } }
            SectionCard("Recommended action shown by the app") { Text(c.recommendedAction) }
            SectionCard("How to photograph it") { Text("Take a clear photo of the ${c.partToPhotograph.lowercase()} from 30 to 60 cm away, in daylight.") }
        }
    }
}
