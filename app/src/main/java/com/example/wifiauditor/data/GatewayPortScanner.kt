package com.example.wifiauditor.data

import com.example.wifiauditor.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.*

/** Сканирует ТОЛЬКО шлюз собственной сети; никаких обходов, только connect() */
class GatewayPortScanner(private val timeoutMs: Int = 1200) {

    companion object {
        val PORTS = mapOf(21 to "FTP", 22 to "SSH", 23 to "Telnet", 80 to "HTTP", 443 to "HTTPS", 8080 to "HTTP-alt")
    }

    suspend fun scan(gatewayIp: String): List<PortResult> = coroutineScope {
        PORTS.map { (port, name) ->
            async(Dispatchers.IO) { PortResult(port, name, isOpen(gatewayIp, port)) }
        }.awaitAll().sortedBy { it.port }
    }

    private fun isOpen(ip: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs); true }
    } catch (_: Exception) { false }

    fun assess(ports: List<PortResult>): List<Finding> = buildList {
        val open = ports.filter { it.open }.map { it.port }.toSet()
        if (23 in open) add(Finding(Severity.CRITICAL, "На роутере открыт Telnet (23)",
            "Отключите Telnet: пароль и команды идут открытым текстом.", 25))
        if (21 in open) add(Finding(Severity.WARNING, "На роутере открыт FTP (21)",
            "Отключите FTP или используйте SFTP.", 10))
        if ((80 in open || 8080 in open) && 443 !in open) add(Finding(Severity.WARNING,
            "Админ-панель доступна только по HTTP",
            "Включите HTTPS для веб-интерфейса управления.", 10))
        else if (80 in open) add(Finding(Severity.INFO, "Открыт HTTP (80) наряду с HTTPS",
            "Настройте принудительный редирект на HTTPS.", 3))
        if (22 in open) add(Finding(Severity.INFO, "Открыт SSH (22)",
            "Допустимо, если вы им пользуетесь; ограничьте доступ ключами и LAN.", 2))
    }

    // ---------- Поиск активных устройств (ping sweep, без root) ----------
    suspend fun discoverHosts(gatewayIp: String, prefixLength: Int, selfIp: String?): List<String> = coroutineScope {
        val base = InetAddress.getByName(gatewayIp).address
        val prefix = prefixLength.coerceIn(24, 30) // ограничиваем размах: максимум /24
        val hostBits = 32 - prefix
        val baseInt = base.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
        val network = baseInt and (-1 shl hostBits)
        val gate = Semaphore(48)
        (1 until (1 shl hostBits) - 1).map { i ->
            val ip = network or i
            val str = "${(ip ushr 24) and 255}.${(ip ushr 16) and 255}.${(ip ushr 8) and 255}.${ip and 255}"
            async(Dispatchers.IO) {
                gate.withPermit {
                    // isReachable: ICMP/TCP echo на 7; на Android без root чаще работает TCP-вариант
                    if (str != selfIp && runCatching { InetAddress.getByName(str).isReachable(400) }.getOrDefault(false)) str else null
                }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Эвристика AP Isolation: если шлюз отвечает, а ни одного другого клиента не найдено,
     * изоляция ВОЗМОЖНО включена (или клиентов просто нет / они блокируют ping).
     */
    fun apIsolationLikely(hosts: List<String>, gatewayIp: String): Boolean =
        hosts.none { it != gatewayIp }
}
