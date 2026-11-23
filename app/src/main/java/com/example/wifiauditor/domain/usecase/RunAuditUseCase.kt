package com.example.wifiauditor.domain.usecase

import com.example.wifiauditor.data.*
import com.example.wifiauditor.domain.model.*

class RunAuditUseCase(
    private val net: NetworkInfoProvider,
    private val analyzer: WifiSecurityAnalyzer,
    private val scanner: GatewayPortScanner,
) {
    /** emit(прогресс 0..1, текст шага, важность) – вызывается на каждом шаге для отображения статуса. */
    suspend operator fun invoke(emit: (Float, String, Severity) -> Unit): AuditReport {
        fun step(p: Float, t: String, l: Severity = Severity.INFO) = emit(p, t, l)
        val findings = mutableListOf<Finding>()

        step(0.05f, "Чтение параметров текущей сети…")
        val wifi = net.currentWifi()
        if (wifi == null) step(0.10f, "Нет подключения к Wi-Fi или не хватает разрешений", Severity.WARNING)
        else step(0.10f, "Сеть «${wifi.ssid}»: ${wifi.security.label}, ${wifi.band}, канал ${wifi.channel}, ${wifi.rssi} dBm")

        step(0.15f, "Чтение списка сетей поблизости…")
        val nearby = net.scanResults().sortedByDescending { it.rssi }
        step(0.20f, "Найдено сетей: ${nearby.size}", if (nearby.isEmpty()) Severity.WARNING else Severity.INFO)

        step(0.25f, "Оценка защиты текущей сети…")
        wifi?.let {
            val f = analyzer.assessCurrent(it)
            findings += f
            step(0.30f, "Замечаний по шифрованию: ${f.size}", if (f.any { x -> x.severity == Severity.CRITICAL }) Severity.CRITICAL else Severity.INFO)
        }

        step(0.35f, "Поиск аномалий (одинаковые SSID, разное шифрование)…")
        val anomalies = analyzer.detectAnomalies(nearby)
        findings += anomalies
        step(0.40f, if (anomalies.isEmpty()) "Аномалий не найдено" else "Аномалий: ${anomalies.size}",
            if (anomalies.isEmpty()) Severity.INFO else Severity.WARNING)

        step(0.42f, "Анализ загрузки каналов…")
        val band = wifi?.band ?: "2.4 GHz"
        val load = analyzer.channelLoad(nearby, band)
        val rec = analyzer.recommendChannel(load, band)
        val mine = load.firstOrNull { it.channel == wifi?.channel }?.apCount ?: 0
        val best = load.firstOrNull { it.channel == rec }?.apCount ?: 0
        if (wifi != null && rec != null && mine > best + 2)
            findings += Finding(Severity.INFO, "Канал ${wifi.channel} перегружен",
                "Попробуйте канал $rec в настройках роутера.", 3)
        step(0.47f, "Рекомендуемый канал: ${rec ?: "—"}")

        step(0.50f, "Определение шлюза, маски и DNS…")
        val (lan, selfIp) = net.lanInfo() ?: (LanInfo(null, null, emptyList()) to null)
        var ports = emptyList<PortResult>()
        var hosts = emptyList<String>()
        var isolation: Boolean? = null

        if (lan.gateway == null) {
            step(0.90f, "Шлюз не определён, проверки локальной сети пропущены", Severity.WARNING)
        } else {
            step(0.55f, "Шлюз ${lan.gateway}, DNS: ${lan.dns.joinToString().ifBlank { "—" }}")
            step(0.58f, "Проверка портов шлюза (21, 22, 23, 80, 443, 8080)…")
            ports = scanner.scan(lan.gateway)
            val open = ports.filter { it.open }
            step(0.70f, "Открытые порты: ${if (open.isEmpty()) "нет" else open.joinToString { "${it.port}/${it.service}" }}",
                if (open.any { it.port == 23 }) Severity.CRITICAL else Severity.INFO)
            findings += scanner.assess(ports)

            if (lan.prefixLength != null) {
                step(0.75f, "Поиск устройств в подсети /${lan.prefixLength} (ping)…")
                hosts = scanner.discoverHosts(lan.gateway, lan.prefixLength, selfIp)
                isolation = scanner.apIsolationLikely(hosts, lan.gateway)
                step(0.90f, "Устройств отвечает: ${hosts.size}" +
                    if (isolation == true) " (похоже, включена AP Isolation)" else "")
            }
        }

        step(0.95f, "Подсчёт итоговой оценки…")
        val score = (100 - findings.sumOf { it.penalty }).coerceIn(0, 100)
        return AuditReport(wifi, nearby, load, rec, lan.copy(activeHosts = hosts), ports, isolation, findings, score, defaultChecklist())
    }

    private fun defaultChecklist() = listOf(
        ChecklistItem("wps", "Отключить WPS (PIN-метод уязвим к подбору)"),
        ChecklistItem("guest", "Включить гостевую сеть с изоляцией клиентов"),
        ChecklistItem("upnp", "Отключить UPnP, если он вам не нужен"),
        ChecklistItem("admin", "Сменить стандартный пароль админ-панели"),
        ChecklistItem("fw", "Обновить прошивку роутера"),
        ChecklistItem("remote", "Отключить удалённое управление из WAN"),
        ChecklistItem("wpa3", "Включить WPA3 и PMF (802.11w)"),
    )
}
