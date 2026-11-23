package com.example.wifiauditor.domain.model

enum class SecurityType(val label: String) {
    OPEN("Open"), WEP("WEP"), WPA("WPA"), WPA2("WPA2-PSK"),
    WPA3("WPA3-SAE"), WPA2_WPA3("WPA2/WPA3"), ENTERPRISE("Enterprise (802.1X)"), UNKNOWN("Unknown")
}

enum class Severity { INFO, WARNING, CRITICAL }

data class Finding(val severity: Severity, val title: String, val recommendation: String, val penalty: Int)

data class CurrentWifi(
    val ssid: String, val bssid: String, val frequencyMhz: Int, val channel: Int,
    val band: String, val linkSpeedMbps: Int, val rssi: Int,
    val security: SecurityType, val pmfRequired: Boolean?,
)

data class ScannedAp(
    val ssid: String, val bssid: String, val rssi: Int, val frequencyMhz: Int,
    val channel: Int, val security: SecurityType,
)

data class ChannelLoad(val channel: Int, val apCount: Int)

data class LanInfo(
    val gateway: String?, val prefixLength: Int?, val dns: List<String>,
    val activeHosts: List<String> = emptyList(),
)

data class PortResult(val port: Int, val service: String, val open: Boolean)

data class ChecklistItem(val id: String, val text: String, var done: Boolean = false)

data class AuditReport(
    val wifi: CurrentWifi?, val nearby: List<ScannedAp>, val channelLoad: List<ChannelLoad>,
    val recommendedChannel: Int?, val lan: LanInfo?, val ports: List<PortResult>,
    val apIsolationLikely: Boolean?, val findings: List<Finding>, val score: Int,
    val checklist: List<ChecklistItem>,
)
