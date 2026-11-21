package com.nairo.app.update

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val received: Long, val total: Long) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checker: UpdateChecker,
    private val downloader: UpdateDownloader,
) : ViewModel() {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val current: AppVersion = AppVersion.current(context)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _useMirror = MutableStateFlow(prefs.getBoolean(KEY_MIRROR, true))
    val useMirror: StateFlow<Boolean> = _useMirror.asStateFlow()

    /** 弹窗是否显示（用户点「稍后」后隐藏，手动检查或下次启动会再弹）。 */
    private val _dialogVisible = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = _dialogVisible.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var job: Job? = null

    init {
        downloader.cleanup()
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last >= AUTO_CHECK_INTERVAL_MS) check(manual = false)
    }

    fun setUseMirror(value: Boolean) {
        _useMirror.value = value
        prefs.edit().putBoolean(KEY_MIRROR, value).apply()
    }

    fun check(manual: Boolean) {
        val s = _state.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading) {
            if (manual) _dialogVisible.value = s is UpdateState.Downloading
            return
        }
        if (manual && s is UpdateState.Ready) {
            _dialogVisible.value = true
            return
        }
        job = viewModelScope.launch {
            _state.value = UpdateState.Checking
            val info = checker.fetchLatest(_useMirror.value)
            if (info == null) {
                _state.value = UpdateState.Failed("检查失败，请检查网络后重试")
                if (manual) _messages.tryEmit("检查失败，请检查网络后重试")
                return@launch
            }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            if (info.isNewerThan(current.code) && info.supports(Build.VERSION.SDK_INT)) {
                _state.value = UpdateState.Available(info)
                _dialogVisible.value = true
            } else {
                _state.value = UpdateState.UpToDate
                if (manual) _messages.tryEmit("已是最新版本")
            }
        }
    }

    fun startDownload() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> s.info ?: return
            else -> return
        }
        _dialogVisible.value = true
        job = viewModelScope.launch {
            _state.value = UpdateState.Downloading(info, 0, info.apkSize)
            try {
                val file = downloader.download(info, _useMirror.value) { received, total ->
                    _state.value = UpdateState.Downloading(info, received, total)
                }
                _state.value = UpdateState.Ready(info, file)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = UpdateState.Available(info)
                throw e
            } catch (e: Throwable) {
                _state.value = UpdateState.Failed(
                    if (e.message == "verify_failed") "安装包校验失败，请重试" else "下载失败，请重试",
                    info,
                )
            }
        }
    }

    fun cancelDownload() {
        job?.cancel()
    }

    fun dismissDialog() {
        _dialogVisible.value = false
    }

    private companion object {
        const val PREFS = "nairo_update"
        const val KEY_MIRROR = "use_mirror"
        const val KEY_LAST_CHECK = "last_check"
        const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
