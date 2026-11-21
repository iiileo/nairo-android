package com.nairo.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nairo.app.bind.AppBindConfig
import com.nairo.app.bind.BoundAccountRepository
import com.nairo.app.data.NotificationRepository
import com.nairo.app.data.local.BoundAccount
import com.nairo.app.data.local.NotificationEvent
import com.nairo.app.data.local.PushOutbox
import com.nairo.app.data.local.PushOutboxDao
import com.nairo.app.keepalive.KeepAliveController
import com.nairo.app.network.NairoApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nairo.app.match.MatchRulesSnapshot
import com.nairo.app.match.MatchRulesStore
import com.nairo.app.notify.PaymentNotificationListener
import com.nairo.app.sync.PushSyncEngine
import com.nairo.app.util.BatteryOptimization
import com.nairo.app.util.NotificationAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MonitorUiState(
    val listenerEnabled: Boolean = false,
    val batteryIgnored: Boolean = false,
    val keepAliveEnabled: Boolean = true,
    val bindMessage: String? = null,
    val bindError: String? = null,
)

@HiltViewModel
class MonitorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NotificationRepository,
    private val pushOutboxDao: PushOutboxDao,
    private val matchRulesStore: MatchRulesStore,
    private val keepAliveController: KeepAliveController,
    private val boundAccounts: BoundAccountRepository,
    private val pushSync: PushSyncEngine,
    private val api: NairoApiClient,
) : ViewModel() {

    val events: StateFlow<List<NotificationEvent>> = repository.events
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pushItems: StateFlow<List<PushOutbox>> = pushOutboxDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<BoundAccount>> = boundAccounts.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val matchRules: StateFlow<MatchRulesSnapshot> = matchRulesStore.snapshot
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            matchRulesStore.snapshot.value,
        )

    private val _uiState = MutableStateFlow(
        MonitorUiState(keepAliveEnabled = keepAliveController.enabled),
    )
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    init {
        refreshStatus()
        keepAliveController.ensureStartedIfEnabled()
        pushSync.start()
    }

    fun refreshStatus() {
        _uiState.update {
            it.copy(
                listenerEnabled = NotificationAccess.isEnabled(context),
                batteryIgnored = BatteryOptimization.isIgnoring(context),
                keepAliveEnabled = keepAliveController.enabled,
            )
        }
        if (NotificationAccess.isEnabled(context)) {
            PaymentNotificationListener.requestRebind(context)
        }
        pushSync.kick()
    }

    fun setKeepAlive(enabled: Boolean) {
        keepAliveController.enabled = enabled
        refreshStatus()
    }

    fun retryPush(id: Long) = pushSync.retryFailed(id)

    fun retryAllFailedPush() = pushSync.retryFailed(null)

    fun clearEvents() {
        viewModelScope.launch {
            repository.clear()
            pushOutboxDao.clearAll()
        }
    }

    fun bindFromJson(raw: String) {
        viewModelScope.launch {
            try {
                val config = AppBindConfig.parse(raw)
                val name = config.name.ifBlank { config.callbackId }

                // 一部手机只能绑定一个监控设备
                val existing = boundAccounts.list()
                val other = existing.firstOrNull { it.callbackId != config.callbackId }
                if (other != null) {
                    fail("本机已绑定「${other.name}」，一部手机只能绑定一个设备。请先解除绑定后再扫码。")
                    return@launch
                }

                // 一个监控设备只能被一部手机绑定：向服务器登记
                val result = withContext(Dispatchers.IO) { api.bindDevice(config) }
                if (!result.ok) {
                    fail(describeBindError(result, name))
                    return@launch
                }

                boundAccounts.bind(config)
                keepAliveController.ensureStartedIfEnabled()
                pushSync.refreshMatchRules()
                pushSync.kick()
                _uiState.update {
                    it.copy(bindMessage = "已绑定「$name」", bindError = null)
                }
            } catch (e: Exception) {
                fail(e.message ?: "绑定失败")
            }
        }
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(bindMessage = null, bindError = message) }
    }

    private fun describeBindError(result: NairoApiClient.BindResult, name: String): String = when (result.error) {
        "bound_elsewhere" -> {
            val who = result.occupiedBy.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
            "「$name」已被其他手机$who 绑定。请先在原手机解除绑定，或到控制台点「解除手机绑定」后再扫码。"
        }
        "bad_sign" -> "绑定码已失效，请在控制台重新生成后再扫码"
        "not_found" -> "该设备不存在或已被删除"
        "network" -> "无法连接服务器，请检查网络后重试"
        "invalid_request" -> "绑定信息有误，请重新扫码"
        else -> "绑定失败（${result.error}）"
    }

    fun unbind(callbackId: String) {
        viewModelScope.launch {
            val account = boundAccounts.list().firstOrNull { it.callbackId == callbackId }
            // 先释放服务器上的占用，失败不阻塞本地解除（可在控制台强制解除）
            val released = account?.let {
                withContext(Dispatchers.IO) { api.unbindDevice(it) }
            }
            boundAccounts.unbind(callbackId)
            val message = when {
                released == null || released.ok || released.error == "bound_elsewhere" -> "已解除绑定"
                released.error == "network" -> "已解除本机绑定；服务器暂未同步，如需换手机请到控制台点「解除手机绑定」"
                else -> "已解除本机绑定"
            }
            _uiState.update { it.copy(bindMessage = message, bindError = null) }
        }
    }

    fun clearBindFeedback() {
        _uiState.update { it.copy(bindMessage = null, bindError = null) }
    }

    fun refreshMatchRules() {
        pushSync.refreshMatchRules()
    }
}
