package com.example.wifiauditor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wifiauditor.domain.model.*

@Composable
fun MainScreen(vm: MainViewModel, onRequestPermissions: () -> Unit) {
    val s by vm.state.collectAsState()
    Scaffold { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Wi-Fi Security Auditor", style = MaterialTheme.typography.headlineSmall)
                Text("Пассивный аудит только вашей собственной сети", fontSize = 12.sp)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRequestPermissions) { Text("Разрешения") }
                    Button(onClick = vm::runAudit, enabled = !s.loading) { Text(if (s.loading) "Сканирую…" else "Запустить аудит") }
                }
            }
            s.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            s.report?.let { r ->
                item { ScoreCard(r.score) }
                r.wifi?.let { w -> item { WifiCard(w) } }
                if (r.findings.isNotEmpty()) {
                    item { Text("Находки", style = MaterialTheme.typography.titleMedium) }
                    items(r.findings) { FindingCard(it) }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Локальная сеть", style = MaterialTheme.typography.titleMedium)
                            Text("Шлюз: ${r.lan?.gateway ?: "—"}  /${r.lan?.prefixLength ?: "?"}")
                            Text("DNS: ${r.lan?.dns?.joinToString() ?: "—"}")
                            Text("Активных устройств: ${r.lan?.activeHosts?.size ?: 0}")
                            r.apIsolationLikely?.let { Text(if (it) "AP Isolation: вероятно включена" else "AP Isolation: не обнаружена") }
                            r.ports.forEach { Text("${it.port} ${it.service}: ${if (it.open) "ОТКРЫТ" else "закрыт"}") }
                            r.recommendedChannel?.let { Text("Рекомендуемый канал: $it") }
                        }
                    }
                }
                item { Text("Чек-лист для роутера", style = MaterialTheme.typography.titleMedium) }
                items(r.checklist) { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(c.id in s.checked, { vm.toggle(c.id) })
                        Text(c.text)
                    }
                }
                item { Text("Соседние сети (${r.nearby.size})", style = MaterialTheme.typography.titleMedium) }
                items(r.nearby) { ap ->
                    Text("${ap.ssid} · ch ${ap.channel} · ${ap.rssi} dBm · ${ap.security.label}", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ScoreCard(score: Int) {
    val color = when { score >= 80 -> Color(0xFF2E7D32); score >= 50 -> Color(0xFFF9A825); else -> Color(0xFFC62828) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score / 100", fontSize = 44.sp, color = color)
            LinearProgressIndicator(progress = { score / 100f }, color = color, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WifiCard(w: CurrentWifi) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp)) {
        Text("${w.ssid}  (${w.security.label})", style = MaterialTheme.typography.titleMedium)
        Text("BSSID ${w.bssid}")
        Text("${w.band} · канал ${w.channel} · ${w.frequencyMhz} МГц")
        Text("Скорость ${w.linkSpeedMbps} Мбит/с · RSSI ${w.rssi} dBm")
    }
}

@Composable
private fun FindingCard(f: Finding) {
    val c = when (f.severity) { Severity.CRITICAL -> Color(0xFFC62828); Severity.WARNING -> Color(0xFFF9A825); else -> Color.Gray }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(f.title, color = c, style = MaterialTheme.typography.titleSmall)
            Text(f.recommendation, fontSize = 13.sp)
        }
    }
}
