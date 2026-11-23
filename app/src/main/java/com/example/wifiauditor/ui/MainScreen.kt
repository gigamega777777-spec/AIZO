package com.example.wifiauditor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wifiauditor.domain.model.*

private val Green = Color(0xFF2E7D32)
private val Amber = Color(0xFFF9A825)
private val Red = Color(0xFFC62828)

@Composable
fun MainScreen(vm: MainViewModel, onRequestPermissions: () -> Unit) {
    val s by vm.state.collectAsState()
    var logOpen by rememberSaveable { mutableStateOf(true) }
    var netsOpen by rememberSaveable { mutableStateOf(true) }
    var tab by rememberSaveable { mutableStateOf(0) }

    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = FontFamily.Cursive)) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Аудит") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Filled.Search, null) }, label = { Text("OSINT") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Filled.Info, null) }, label = { Text("Сеть") })
            }
        }
    ) { pad ->
        if (tab == 0) LazyColumn(
            Modifier.padding(pad).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("AIZO", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF7C3AED))
                Text("Пассивный аудит только вашей собственной сети", fontSize = 12.sp)
            }
            item { StatusCard(s) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRequestPermissions) { Text("Разрешения") }
                    Button(
                        onClick = { if (s.preflight.permissionsOk) vm.runAudit() else onRequestPermissions() },
                        enabled = !s.loading,
                    ) { Text(if (s.loading) "Сканирую…" else "Запустить аудит") }
                }
            }
            s.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

            item { LogCard(s.log, logOpen) { logOpen = !logOpen } }

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
                            Text("DNS: ${r.lan?.dns?.joinToString()?.ifBlank { "—" } ?: "—"}")
                            Text("Активных устройств: ${r.lan?.activeHosts?.size ?: 0}")
                            r.apIsolationLikely?.let {
                                Text(if (it) "AP Isolation: вероятно включена" else "AP Isolation: не обнаружена")
                            }
                            r.ports.forEach {
                                Text("${it.port} ${it.service}: ${if (it.open) "ОТКРЫТ" else "закрыт"}",
                                    color = if (it.open && it.port in setOf(21, 23)) Red else Color.Unspecified)
                            }
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
            }

            // ---- Выпадающий список сетей поблизости ----
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { netsOpen = !netsOpen }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Сети поблизости (${s.networks.size})  ${if (netsOpen) "▲" else "▼"}",
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Button(onClick = vm::refreshNetworks, enabled = !s.scanning) {
                            Text(if (s.scanning) "Скан…" else "Обновить")
                        }
                    }
                }
            }
            if (netsOpen) {
                if (s.networks.isEmpty()) {
                    item {
                        Text("Список пуст. Нужны разрешения, включённая геолокация и Wi-Fi; затем нажмите «Обновить».",
                            fontSize = 13.sp)
                    }
                }
                items(s.networks, key = { it.bssid }) { NetworkRow(it) }
            }

            // ---- OSINT: сбор информации и разведка ----
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("OSINT — сбор информации и разведка", style = MaterialTheme.typography.titleMedium, color = Color(0xFF7C3AED))
                        Text("Инструменты для пассивной разведки и визуализации связей:", fontSize = 13.sp)
                        Text("• Nmap — сканирование портов и сервисов", fontSize = 13.sp)
                        Text("• Maltego — визуализация связей в виде графа", fontSize = 13.sp)
                        Text("• Shodan / Censys — поиск устройств в интернете", fontSize = 13.sp)
                        Text("• theHarvester — сбор email, доменов, IP", fontSize = 13.sp)
                        Text("• Recon-ng — фреймворк разведки", fontSize = 13.sp)
                    }
                }
            }
        } else if (tab == 1) {
            Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("OSINT — разведка", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF7C3AED))
                OutlinedTextField(value = s.osintTarget, onValueChange = vm::setOsintTarget, label = { Text("Цель (IP/домен)") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = vm::runOsintScan, enabled = !s.osintScanning) { Text(if (s.osintScanning) "Сканирую…" else "Сканировать порты") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Перехват пакетов")
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = s.packetCapture, onCheckedChange = { vm.togglePacketCapture() })
                }
                s.osintPorts.forEach { p ->
                    Text("${p.port} ${p.service}: ${if (p.open) "ОТКРЫТ" else "закрыт"}", color = if (p.open) Color(0xFFDC2626) else Color.Unspecified)
                }
                Text("Разведка сети: ${s.networks.size} сетей поблизости", fontSize = 13.sp)
            }
        } else {
            Column(Modifier.padding(pad).padding(16.dp)) {
                Text("Сеть", style = MaterialTheme.typography.headlineSmall)
                Text("Устройств: ${s.networks.size}")
            }
        }
    }
    }
}

@Composable
private fun StatusCard(s: UiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val title = when {
                s.loading -> "Идёт аудит…"
                s.report != null -> "Аудит завершён"
                else -> "Готов к запуску"
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (s.loading) {
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                Text("${(s.progress * 100).toInt()}% · ${s.step}", fontSize = 13.sp)
            }
            val p = s.preflight
            Check("Разрешения выданы", p.permissionsOk)
            Check("Wi-Fi включён", p.wifiEnabled)
            Check("Геолокация включена", p.locationEnabled)
            Check("Подключение к Wi-Fi", p.connected)
        }
    }
}

@Composable
private fun Check(text: String, ok: Boolean) =
    Text((if (ok) "✓ " else "✗ ") + text, color = if (ok) Green else Red, fontSize = 13.sp)

@Composable
private fun LogCard(log: List<LogEntry>, open: Boolean, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Журнал (${log.size})  ${if (open) "▲" else "▼"}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle))
            if (open) {
                Spacer(Modifier.height(6.dp))
                if (log.isEmpty()) Text("Пока пусто. Нажмите «Запустить аудит».", fontSize = 12.sp)
                log.forEach { e ->
                    Text(
                        "${e.time}  ${e.text}",
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = when (e.level) {
                            Severity.CRITICAL -> Red
                            Severity.WARNING -> Amber
                            else -> Color.Unspecified
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkRow(ap: ScannedAp) {
    var open by rememberSaveable(ap.bssid) { mutableStateOf(false) }
    val weak = ap.security in setOf(SecurityType.OPEN, SecurityType.WEP, SecurityType.WPA)
    Card(Modifier.fillMaxWidth().clickable { open = !open }) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ap.ssid, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${ap.rssi} dBm  ${if (open) "▲" else "▼"}", fontSize = 13.sp)
            }
            Text("${ap.security.label} · ${ap.band} · канал ${ap.channel}", fontSize = 13.sp)
            if (weak) Text("Слабая или отсутствующая защита", color = Red, fontSize = 12.sp)
            if (open) {
                Spacer(Modifier.height(6.dp))
                Text("BSSID: ${ap.bssid}", fontSize = 12.sp)
                Text("Частота: ${ap.frequencyMhz} МГц", fontSize = 12.sp)
                ap.widthMhz?.let { Text("Ширина канала: $it МГц", fontSize = 12.sp) }
                ap.standard?.let { Text("Стандарт: $it", fontSize = 12.sp) }
                Text("Сигнал: ${ap.rssi} dBm (${ap.signalQuality})", fontSize = 12.sp)
                Text("Расстояние: ~${String.format("%.0f", ap.distanceM)} м (очень грубо)", fontSize = 12.sp)
                Text("Capabilities: ${ap.capabilities}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun ScoreCard(score: Int) {
    val color = when { score >= 80 -> Green; score >= 50 -> Amber; else -> Red }
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
    val c = when (f.severity) { Severity.CRITICAL -> Red; Severity.WARNING -> Amber; else -> Color.Gray }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(f.title, color = c, style = MaterialTheme.typography.titleSmall)
            Text(f.recommendation, fontSize = 13.sp)
        }
    }
}
