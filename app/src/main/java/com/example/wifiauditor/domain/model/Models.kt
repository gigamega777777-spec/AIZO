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
    val widthMhz: Int? = null, val standard: String? = null, val capabilities: String = "",
) {
    val band: String
        get() = when {
            frequencyMhz < 3000 -> "2.4 ГГц"
            frequencyMhz < 5900 -> "5 ГГц"
            else -> "6 ГГц"
        }

    val signalQuality: String
        get() = when {
            rssi >= -50 -> "отличный"
            rssi >= -60 -> "хороший"
            rssi >= -70 -> "средний"
            rssi >= -80 -> "слабый"
            else -> "очень слабый"
        }

    /** Очень грубая оценка по модели свободного пространства; стены и помехи искажают её сильно. */
    val distanceM: Double
        get() = Math.pow(10.0, (27.55 - 20 * Math.log10(frequencyMhz.toDouble()) - rssi) / 20.0)
}

data class ChannelLoad(val channel: Int, val apCount: Int)

data class LanInfo(
    val gateway: String?, val prefixLength: Int?, val dns: List<String>,
    val activeHosts: List<String> = emptyList(),
)

data class PortResult(val port: Int, val service: String, val open: Boolean)

data class ChecklistItem(val id: String, val text: String, var done: Boolean = false)

data class LogEntry(val time: String, val text: String, val level: Severity = Severity.INFO)

data class Preflight(
    val permissionsOk: Boolean = false, val wifiEnabled: Boolean = false,
    val locationEnabled: Boolean = false, val connected: Boolean = false,
)

data class ScanOutcome(val aps: List<ScannedAp>, val fresh: Boolean)

data class AuditReport(
    val wifi: CurrentWifi?, val nearby: List<ScannedAp>, val channelLoad: List<ChannelLoad>,
    val recommendedChannel: Int?, val lan: LanInfo?, val ports: List<PortResult>,
    val apIsolationLikely: Boolean?, val findings: List<Finding>, val score: Int,
    val checklist: List<ChecklistItem>,
)
