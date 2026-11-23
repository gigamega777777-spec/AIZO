package com.example.wifiauditor.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.example.wifiauditor.domain.model.*
import java.net.Inet4Address

class NetworkInfoProvider(context: Context, private val analyzer: WifiSecurityAnalyzer) {
    private val app = context.applicationContext
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Пассивно: getScanResults возвращает кэш системы, новых сканов не инициируем. */
    @SuppressLint("MissingPermission")
    fun scanResults(): List<ScannedAp> =
        runCatching { wifi.scanResults.map(analyzer::toScannedAp) }.getOrDefault(emptyList())

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun currentWifi(): CurrentWifi? {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val info = wifi.connectionInfo ?: return null // для SSID/BSSID нужна геолокация
        val bssid = info.bssid.orEmpty()
        val match = runCatching { wifi.scanResults }.getOrDefault(emptyList()).firstOrNull { it.BSSID == bssid }
        val capsStr = match?.capabilities.orEmpty()
        return CurrentWifi(
            ssid = info.ssid.orEmpty().trim('"'),
            bssid = bssid,
            frequencyMhz = info.frequency,
            channel = analyzer.channelFromFrequency(info.frequency),
            band = analyzer.band(info.frequency),
            linkSpeedMbps = info.linkSpeed,
            rssi = info.rssi,
            security = if (capsStr.isBlank()) SecurityType.UNKNOWN else analyzer.parseSecurity(capsStr),
            pmfRequired = analyzer.pmfRequired(capsStr),
        )
    }

    fun lanInfo(): Pair<LanInfo, String?>? {
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        val gw = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
        val addr = lp.linkAddresses.firstOrNull { it.address is Inet4Address }
        return LanInfo(gw, addr?.prefixLength, lp.dnsServers.mapNotNull { it.hostAddress }) to addr?.address?.hostAddress
    }
}
