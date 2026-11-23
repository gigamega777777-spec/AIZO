package com.example.wifiauditor.data

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.*

class PacketCaptureService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        @Volatile var running = false
        @Volatile var packetCount = 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        val builder = Builder()
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .setSession("AIZO capture")
        tun = builder.establish()
        running = true
        scope.launch {
            val fd = tun?.fileDescriptor ?: return@launch
            val buf = ByteArray(32767)
            while (running) {
                val n = try { java.io.FileInputStream(fd).read(buf) } catch (_: Exception) { -1 }
                if (n <= 0) break
                packetCount++
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        tun?.close()
        tun = null
        super.onDestroy()
    }
}