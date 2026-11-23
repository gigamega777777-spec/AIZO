package com.example.wifiauditor.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.wifiauditor.domain.model.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

class NetworkInfoProvider(context: Context, private val analyzer: WifiSecurityAnalyzer) {
    private val app = context.applicationContext
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(app, p) == PackageManager.PERMISSION_GRANTED

    /** Проверка условий, без которых аудит даст пустой результат. */
    fun preflight(): Preflight {
        val perms = granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            (Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.NEARBY_WIFI_DEVICES))
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locOn = if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else true
        val connected = cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        return Preflight(perms, wifi.isWifiEnabled, locOn, connected)
    }

    /** Пассивно: getScanResults возвращает кэш системы, новых сканов не инициирует. */
    @SuppressLint("MissingPermission")
    fun scanResults(): List<ScannedAp> =
        runCatching { wifi.scanResults.map(analyzer::toScannedAp).distinctBy { it.bssid } }
            .getOrDefault(emptyList())

    /** Просит систему обновить скан (документированный startScan; Android ограничивает частоту). */
    @SuppressLint("MissingPermission")
    suspend fun refreshScan(): ScanOutcome {
        val fresh = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, i: Intent) {
                        runCatching { app.unregisterReceiver(this) }
                        if (cont.isActive) cont.resume(true)
                    }
                }
                ContextCompat.registerReceiver(
                    app, receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                cont.invokeOnCancellation { runCatching { app.unregisterReceiver(receiver) } }
                @Suppress("DEPRECATION")
                val started = runCatching { wifi.startScan() }.getOrDefault(false)
                if (!started) {
                    runCatching { app.unregisterReceiver(receiver) }
                    if (cont.isActive) cont.resume(false)
                }
            }
        } ?: false
        return ScanOutcome(scanResults().sortedByDescending { it.rssi }, fresh)
    }

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
