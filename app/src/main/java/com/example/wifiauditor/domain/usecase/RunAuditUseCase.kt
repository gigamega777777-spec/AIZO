package com.example.wifiauditor.domain.usecase

import com.example.wifiauditor.data.*
import com.example.wifiauditor.domain.model.*

class RunAuditUseCase(
    private val net: NetworkInfoProvider,
    private val analyzer: WifiSecurityAnalyzer,
    private val scanner: GatewayPortScanner,
) {
    suspend operator fun invoke(): AuditReport {
        val findings = mutableListOf<Finding>()
        val wifi = net.currentWifi()
        val nearby = net.scanResults().sortedByDescending { it.rssi }

        wifi?.let { findings += analyzer.assessCurrent(it) }
        findings += analyzer.detectAnomalies(nearby)

        val band = wifi?.band ?: "2.4 GHz"
        val load = analyzer.channelLoad(nearby, band)
        val rec = analyzer.recommendChannel(load, band)
        if (wifi != null && rec != null && load.firstOrNull { it.channel == wifi.channel }?.apCount ?: 0 >
            (load.firstOrNull { it.channel == rec }?.apCount ?: 0) + 2)
            findings += Finding(Severity.INFO, "Канал ${wifi.channel} перегружен",
                "Попробуйте канал $rec в настройках роутера.", 3)

        val (lan, selfIp) = net.lanInfo() ?: (LanInfo(null, null, emptyList()) to null)
        var ports = emptyList<PortResult>()
        var hosts = emptyList<String>()
        var isolation: Boolean? = null
        if (lan.gateway != null) {
            ports = scanner.scan(lan.gateway)
            findings += scanner.assess(ports)
            lan.prefixLength?.let {
                hosts = scanner.discoverHosts(lan.gateway, it, selfIp)
                isolation = scanner.apIsolationLikely(hosts, lan.gateway)
            }
        }
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
