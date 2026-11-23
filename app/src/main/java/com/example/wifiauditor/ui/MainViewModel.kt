package com.example.wifiauditor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifiauditor.data.*
import com.example.wifiauditor.domain.model.AuditReport
import com.example.wifiauditor.domain.usecase.RunAuditUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = false, val report: AuditReport? = null,
    val checked: Set<String> = emptySet(), val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val analyzer = WifiSecurityAnalyzer()
    private val useCase = RunAuditUseCase(NetworkInfoProvider(app, analyzer), analyzer, GatewayPortScanner())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun runAudit() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { useCase() }
                .onSuccess { r -> _state.update { it.copy(loading = false, report = r) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun toggle(id: String) = _state.update {
        it.copy(checked = if (id in it.checked) it.checked - id else it.checked + id)
    }
}
