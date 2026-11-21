package com.nairo.app.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nairo.app.ui.components.BarItem
import com.nairo.app.ui.components.NDialog
import com.nairo.app.ui.components.NairoBottomBar
import com.nairo.app.ui.components.NairoIcons
import com.nairo.app.ui.pages.BindPage
import com.nairo.app.ui.pages.MatchRulesScreen
import com.nairo.app.ui.pages.StatusPage
import com.nairo.app.ui.pages.TimelinePage
import com.nairo.app.ui.pages.UpdateUi
import com.nairo.app.ui.scan.ScanBindScreen
import com.nairo.app.ui.theme.Brand
import com.nairo.app.ui.theme.Canvas
import com.nairo.app.ui.theme.Hairline
import com.nairo.app.ui.theme.Ink400
import com.nairo.app.ui.theme.Ink500
import com.nairo.app.ui.theme.Ink900
import com.nairo.app.update.UpdateInstaller
import com.nairo.app.update.UpdateState
import com.nairo.app.update.UpdateViewModel
import com.nairo.app.util.BatteryOptimization
import com.nairo.app.util.NotificationAccess
import com.nairo.app.util.OemBackgroundGuide

private enum class AppTab(val label: String, val icon: ImageVector) {
    Timeline("到账", NairoIcons.Receipt),
    Bind("设备", NairoIcons.Smartphone),
    Status("设置", NairoIcons.Settings),
}

@Composable
fun NairoRoot(
    viewModel: MonitorViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val pushItems by viewModel.pushItems.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val matchRules by viewModel.matchRules.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val updateDialogVisible by updateViewModel.dialogVisible.collectAsStateWithLifecycle()
    val useMirror by updateViewModel.useMirror.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tab by rememberSaveable { mutableStateOf(AppTab.Timeline.name) }
    val current = AppTab.entries.firstOrNull { it.name == tab } ?: AppTab.Timeline
    var showScanner by rememberSaveable { mutableStateOf(false) }
    var showMatchRules by rememberSaveable { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun openScanner() {
        tab = AppTab.Bind.name
        showScanner = true
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) openScanner() }

    fun requestScan() {
        val granted = context.checkSelfPermission(Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) openScanner() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.bindMessage, uiState.bindError) {
        val text = uiState.bindError ?: uiState.bindMessage
        if (!text.isNullOrBlank()) {
            snackbar.showSnackbar(text)
            viewModel.clearBindFeedback()
        }
    }

    LaunchedEffect(Unit) {
        updateViewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    fun installUpdate() {
        val ready = updateState as? UpdateState.Ready ?: return
        if (!UpdateInstaller.canInstall(context)) {
            Toast.makeText(context, "请先允许安装未知应用，返回后再点安装", Toast.LENGTH_LONG).show()
            context.startActivity(UpdateInstaller.permissionIntent(context))
            return
        }
        updateViewModel.dismissDialog()
        context.startActivity(UpdateInstaller.installIntent(context, ready.file))
    }

    if (updateDialogVisible) {
        UpdateDialog(
            state = updateState,
            onDownload = updateViewModel::startDownload,
            onCancel = updateViewModel::cancelDownload,
            onInstall = ::installUpdate,
            onDismiss = updateViewModel::dismissDialog,
        )
    }

    if (showPasteDialog) {
        PasteConfigDialog(
            onDismiss = { showPasteDialog = false },
            onConfirm = {
                viewModel.bindFromJson(it)
                showPasteDialog = false
                showScanner = false
            },
        )
    }

    if (showClearDialog) {
        NDialog(
            title = "清空记录",
            onDismiss = { showClearDialog = false },
            confirmText = "清空",
            confirmColor = com.nairo.app.ui.theme.Danger,
            onConfirm = {
                viewModel.clearEvents()
                showClearDialog = false
            },
        ) {
            Text("将删除所有通知和上报记录，确定吗？", style = MaterialTheme.typography.bodyMedium, color = Ink500)
        }
    }

    AnimatedContent(
        targetState = showScanner,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "scanGate",
    ) { scanning ->
        if (scanning) {
            ScanBindScreen(
                onResult = {
                    viewModel.bindFromJson(it)
                    showScanner = false
                },
                onClose = { showScanner = false },
                onPaste = {
                    showScanner = false
                    showPasteDialog = true
                },
            )
        } else if (showMatchRules) {
            BackHandler { showMatchRules = false }
            MatchRulesScreen(
                snapshot = matchRules,
                onBack = { showMatchRules = false },
                onRefresh = viewModel::refreshMatchRules,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Canvas),
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .statusBarsPadding(),
                ) {
                    when (current) {
                        AppTab.Timeline -> TimelinePage(
                            events = events,
                            pushItems = pushItems,
                            accounts = accounts,
                            onRetry = viewModel::retryPush,
                            onRetryAll = viewModel::retryAllFailedPush,
                            onClear = { showClearDialog = true },
                            onGoDevices = { tab = AppTab.Bind.name },
                        )
                        AppTab.Bind -> BindPage(
                            accounts = accounts,
                            onPaste = { showPasteDialog = true },
                            onScan = ::requestScan,
                            onUnbind = viewModel::unbind,
                        )
                        AppTab.Status -> StatusPage(
                            uiState = uiState,
                            matchRules = matchRules,
                            onOpenListenerSettings = {
                                context.startActivity(NotificationAccess.settingsIntent())
                            },
                            onRequestBattery = {
                                context.startActivity(BatteryOptimization.requestIgnoreIntent(context))
                            },
                            onOpenAppDetails = {
                                context.startActivity(BatteryOptimization.appDetailsIntent(context))
                            },
                            onToggleKeepAlive = viewModel::setKeepAlive,
                            onRequestPostNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onOpenAutostartHint = { OemBackgroundGuide.open(context) },
                            onRefreshMatchRules = viewModel::refreshMatchRules,
                            onOpenMatchRules = { showMatchRules = true },
                            update = UpdateUi(
                                versionName = updateViewModel.current.name,
                                statusText = updateStatusText(updateViewModel.current.name, updateState),
                                busy = updateState is UpdateState.Checking || updateState is UpdateState.Downloading,
                                useMirror = useMirror,
                                onCheck = { updateViewModel.check(manual = true) },
                                onToggleMirror = updateViewModel::setUseMirror,
                            ),
                        )
                    }
                }
                NairoBottomBar(
                    items = AppTab.entries.map { BarItem(it.label, it.icon) },
                    selected = current.ordinal,
                    onSelect = { tab = AppTab.entries[it].name },
                )
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 86.dp, start = 16.dp, end = 16.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Ink900,
                    contentColor = Color.White,
                )
            }
            }
        }
    }
}

@Composable
private fun PasteConfigDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    NDialog(
        title = "粘贴绑定内容",
        onDismiss = onDismiss,
        confirmText = "绑定",
        confirmEnabled = text.isNotBlank(),
        onConfirm = { onConfirm(text) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Canvas)
                .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            if (text.isEmpty()) {
                Text("粘贴控制台生成的绑定内容", style = MaterialTheme.typography.bodyMedium, color = Ink400)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp, max = 180.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink900),
                cursorBrush = SolidColor(Brand),
            )
        }
    }
}

private fun updateStatusText(currentName: String, state: UpdateState): String = when (state) {
    is UpdateState.Checking -> "当前版本 $currentName · 正在检查…"
    is UpdateState.Available -> "当前版本 $currentName · 发现新版本 ${state.info.versionName}"
    is UpdateState.Downloading -> "当前版本 $currentName · 正在下载 ${state.info.versionName}"
    is UpdateState.Ready -> "当前版本 $currentName · ${state.info.versionName} 已下载，待安装"
    is UpdateState.UpToDate -> "当前版本 $currentName · 已是最新"
    else -> "当前版本 $currentName"
}
