package ph.edu.asiatech.goananas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ph.edu.asiatech.goananas.data.PineappleClass
import ph.edu.asiatech.goananas.data.ScanEntity
import ph.edu.asiatech.goananas.data.ScanStatus
import ph.edu.asiatech.goananas.ui.theme.AlertRed
import ph.edu.asiatech.goananas.ui.theme.FreshGreen
import ph.edu.asiatech.goananas.ui.theme.LeafGreen
import ph.edu.asiatech.goananas.ui.theme.PineGold
import ph.edu.asiatech.goananas.ui.theme.SoftGold
import ph.edu.asiatech.goananas.ui.theme.SoftGreen
import ph.edu.asiatech.goananas.ui.theme.SoftRed
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun classColor(c: PineappleClass): Color = if (c.isHealthy) FreshGreen else AlertRed
fun pct(v: Float) = "%.1f%%".format(Locale.US, v * 100)
fun dateTime(ms: Long): String = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(Date(ms))
fun dateOnly(ms: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(ms))

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

/** confidence of each of the six classes, highest first */
@Composable
fun ProbabilityBars(ranked: List<Pair<PineappleClass, Float>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ranked.forEachIndexed { i, (c, p) ->
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Text(c.displayName, Modifier.weight(1f), fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal)
                    Text(pct(p), fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal)
                }
                LinearProgressIndicator(
                    progress = { p.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (i == 0) classColor(c) else Color(0xFF9E9E9E),
                    trackColor = Color(0xFFECEFF1),
                )
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (bg, fg) = when (status) {
        ScanStatus.OPEN -> SoftRed to AlertRed
        ScanStatus.RECHECKED -> SoftGold to Color(0xFF8A6100)
        ScanStatus.RESOLVED, ScanStatus.HEALTHY -> SoftGreen to LeafGreen
        else -> SoftGold to Color(0xFF8A6100)
    }
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(status, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ScanRow(scan: ScanEntity, onClick: () -> Unit) {
    val c = scan.pineappleClass
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = File(scan.imagePath), contentDescription = c.displayName, contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.displayName, fontWeight = FontWeight.SemiBold, color = classColor(c))
            Text(
                listOf(scan.plantPart, scan.block.ifBlank { null }, dateTime(scan.createdAt)).filterNotNull().joinToString(" • "),
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("Confidence ${pct(scan.confidence)}", style = MaterialTheme.typography.bodySmall)
        }
        StatusBadge(scan.status)
    }
}

@Composable
fun StatTile(value: String, label: String, accent: Color = LeafGreen, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun DemoModelBanner(trained: Boolean) {
    if (trained) return
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SoftGold).padding(12.dp)) {
        Text(
            "Demo model: this build uses an untrained model, so its results are not real diagnoses. " +
                "Replace it with the model produced by model_training/train_model.py.",
            color = Color(0xFF6B4E00), style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Suppress("unused")
val Accent = PineGold
