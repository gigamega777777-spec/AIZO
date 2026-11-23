package com.example.wifiauditor.data

import android.net.wifi.ScanResult
import com.example.wifiauditor.domain.model.*

class WifiSecurityAnalyzer {

    /** Тип защиты по строке capabilities из ScanResult (работает на всех API). */
    fun parseSecurity(caps: String): SecurityType {
        val c = caps.uppercase()
        return when {
            "EAP" in c -> SecurityType.ENTERPRISE
            "SAE" in c && "PSK" in c -> SecurityType.WPA2_WPA3
            "SAE" in c -> SecurityType.WPA3
            "WPA2" in c || "RSN" in c -> SecurityType.WPA2
            "WPA" in c -> SecurityType.WPA
            "WEP" in c -> SecurityType.WEP
            c.contains("ESS") -> SecurityType.OPEN
            else -> SecurityType.UNKNOWN
        }
    }

    fun channelFromFrequency(f: Int): Int = when (f) {
        2484 -> 14
        in 2412..2472 -> (f - 2407) / 5
        in 5160..5885 -> (f - 5000) / 5
        in 5955..7115 -> (f - 5950) / 5
        else -> -1
    }

    fun band(f: Int) = when {
        f < 3000 -> "2.4 GHz"
        f < 5900 -> "5 GHz"
        else -> "6 GHz"
    }

    @Suppress("DEPRECATION")
    fun toScannedAp(r: ScanResult): ScannedAp {
        val ssid = r.SSID.orEmpty().ifBlank { "<hidden>" }
        return ScannedAp(ssid, r.BSSID, r.level, r.frequency, channelFromFrequency(r.frequency), parseSecurity(r.capabilities))
    }

    /** PMF (802.11w) – "required" определяем по флагу MFPR в capabilities, если он есть. */
    fun pmfRequired(caps: String): Boolean? =
        if ("MFPR" in caps.uppercase()) true else if (caps.isNotBlank()) false else null

    // ---------- Оценка текущей сети ----------
    fun assessCurrent(w: CurrentWifi): List<Finding> = buildList {
        when (w.security) {
            SecurityType.OPEN -> add(Finding(Severity.CRITICAL, "Сеть открыта (без шифрования)",
                "Включите WPA3-SAE (или минимум WPA2-AES) в настройках роутера.", 40))
            SecurityType.WEP -> add(Finding(Severity.CRITICAL, "WEP взламывается за минуты",
                "Переключитесь на WPA3 или WPA2-AES.", 40))
            SecurityType.WPA -> add(Finding(Severity.CRITICAL, "WPA (TKIP) устарел и уязвим",
                "Используйте WPA2-AES или WPA3.", 30))
            SecurityType.WPA2 -> add(Finding(Severity.WARNING, "WPA2-PSK: приемлемо, но WPA3 надёжнее",
                "Если роутер поддерживает – включите WPA3-SAE или смешанный режим WPA2/WPA3.", 10))
            SecurityType.WPA2_WPA3 -> add(Finding(Severity.INFO, "Смешанный WPA2/WPA3",
                "Для максимума безопасности отключите WPA2, когда все устройства поддержат WPA3.", 3))
            else -> {}
        }
        if (w.security in setOf(SecurityType.WPA2, SecurityType.WPA2_WPA3, SecurityType.WPA3) && w.pmfRequired == false)
            add(Finding(Severity.WARNING, "Защита management-кадров (PMF, 802.11w) не обязательна",
                "Включите PMF в режим «Required/Обязательно» (для WPA3 он обязателен).", 8))
        if (w.rssi < -80) add(Finding(Severity.INFO, "Слабый сигнал (${w.rssi} dBm)",
            "Приблизьтесь к роутеру для точного аудита.", 0))
    }

    // ---------- Загрузка каналов ----------
    fun channelLoad(aps: List<ScannedAp>, band: String): List<ChannelLoad> {
        val inBand = aps.filter { band(it.frequencyMhz) == band }
        val candidates = if (band == "2.4 GHz") (1..13).toList() else inBand.map { it.channel }.distinct()
        return candidates.map { ch ->
            val load = inBand.count { ap ->
                // на 2.4 ГГц каналы шириной 20 МГц пересекаются в пределах ±4
                if (band == "2.4 GHz") kotlin.math.abs(ap.channel - ch) <= 4 else ap.channel == ch
            }
            ChannelLoad(ch, load)
        }
    }

    /** На 2.4 ГГц рекомендуем только непересекающиеся 1/6/11. */
    fun recommendChannel(load: List<ChannelLoad>, band: String): Int? {
        val pool = if (band == "2.4 GHz") load.filter { it.channel in listOf(1, 6, 11) } else load
        return pool.minByOrNull { it.apCount }?.channel
    }

    // ---------- Пассивное выявление аномалий (evil twin) ----------
    fun detectAnomalies(aps: List<ScannedAp>): List<Finding> {
        val out = mutableListOf<Finding>()
        aps.filter { it.ssid != "<hidden>" }.groupBy { it.ssid }.forEach { (ssid, group) ->
            if (group.map { it.bssid }.distinct().size < 2) return@forEach
            val secs = group.map { it.security }.toSet()
            if (secs.size > 1 && !secs.all { it in setOf(SecurityType.WPA2, SecurityType.WPA2_WPA3, SecurityType.WPA3) }) {
                out += Finding(Severity.WARNING,
                    "Сеть «$ssid»: несколько точек с разным шифрованием (${secs.joinToString { it.label }})",
                    "Возможна поддельная точка доступа. Если это ваш mesh/репитер – проверьте одинаковые настройки защиты на всех узлах.", 10)
            }
        }
        return out
    }
}
