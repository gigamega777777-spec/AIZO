package com.example.wifiauditor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifiauditor.data.*
import com.example.wifiauditor.domain.model.*
import com.example.wifiauditor.domain.usecase.RunAuditUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UiState(
    val loading: Boolean = false,
    val report: AuditReport? = null,
    val checked: Set<String> = emptySet(),
    val error: String? = null,
    val preflight: Preflight = Preflight(),
    val progress: Float = 0f,
    val step: String = "",
    val log: List<LogEntry> = emptyList(),
    val networks: List<ScannedAp> = emptyList(),
    val scanning: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val analyzer = WifiSecurityAnalyzer()
    private val net = NetworkInfoProvider(app, analyzer)
    private val useCase = RunAuditUseCase(net, analyzer, GatewayPortScanner())
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init { refreshStatus() }

    private fun log(text: String, level: Severity = Severity.INFO) {
        val e = LogEntry(timeFmt.format(Date()), text, level)
        _state.update { it.copy(log = (it.log + e).takeLast(120)) }
    }

    /** Обновляет «чек готовности» и подгружает кэш сетей, если права уже выданы. */
    fun refreshStatus() {
        val p = net.preflight()
        _state.update { st ->
            val nets = if (st.networks.isEmpty() && p.permissionsOk)
                net.scanResults().sortedByDescending { it.rssi } else st.networks
            st.copy(preflight = p, networks = nets)
        }
    }

    fun runAudit() {
        if (_state.value.loading) return
        refreshStatus()
        _state.update { it.copy(loading = true, error = null, progress = 0f, step = "Подготовка…", log = emptyList(), report = null) }
        log("Аудит запущен")
        viewModelScope.launch {
            runCatching {
                useCase { p, text, level ->
                    _state.update { it.copy(progress = p, step = text) }
                    log(text, level)
                }
            }.onSuccess { r ->
                _state.update {
                    it.copy(loading = false, progress = 1f, step = "Готово", report = r,
                        networks = if (r.nearby.isNotEmpty()) r.nearby else it.networks)
                }
                log("Аудит завершён. Оценка: ${r.score}/100")
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Неизвестная ошибка") }
                log("Ошибка: ${e.message}", Severity.CRITICAL)
            }
        }
    }

    fun refreshNetworks() {
        if (_state.value.scanning) return
        refreshStatus()
        _state.update { it.copy(scanning = true) }
        log("Запрос обновления списка сетей…")
        viewModelScope.launch {
            val out = runCatching { net.refreshScan() }.getOrNull()
            if (out == null) log("Не удалось выполнить скан", Severity.WARNING)
            else {
                _state.update { it.copy(networks = out.aps) }
                if (out.fresh) log("Список обновлён: ${out.aps.size} сетей")
                else log("Система не выполнила новый скан (лимит частоты), показан кэш: ${out.aps.size}", Severity.WARNING)
            }
            _state.update { it.copy(scanning = false) }
        }
    }

    fun toggle(id: String) = _state.update {
        it.copy(checked = if (id in it.checked) it.checked - id else it.checked + id)
    }
}
