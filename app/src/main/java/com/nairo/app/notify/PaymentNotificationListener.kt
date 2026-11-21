package com.nairo.app.notify

import android.app.Notification
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nairo.app.data.NotificationRepository
import com.nairo.app.data.local.NotificationEvent
import com.nairo.app.sync.PushSyncEngine
import com.nairo.app.util.PaymentHintParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PaymentNotificationListener : NotificationListenerService() {

    @Inject lateinit var repository: NotificationRepository
    @Inject lateinit var pushSync: PushSyncEngine
    @Inject lateinit var paymentHintParser: PaymentHintParser

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        pushSync.start()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.isOngoing) return
        val packageName = sbn.packageName.orEmpty()
        if (!paymentHintParser.isWatchPackage(packageName)) return
        if (packageName == applicationContext.packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = sequenceOf(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        ).filterNotNull().filter { it.isNotBlank() }.distinct().joinToString("\n")

        val ticker = notification.tickerText?.toString().orEmpty()
        if (title.isBlank() && body.isBlank() && ticker.isBlank()) return
        if (!paymentHintParser.shouldCapture(packageName, title, body, ticker)) return
        // 无金额的「收款相关」噪声（汇总角标、纯文案等）直接丢弃
        val amount = paymentHintParser.amountHint(title, body, ticker)?.trim().orEmpty()
        if (amount.isEmpty()) return
        // 金额为 0 不是收款（如营销文案「今日0元领」「¥0」），丢弃
        val amountValue = amount.toBigDecimalOrNull()
        if (amountValue == null || amountValue <= java.math.BigDecimal.ZERO) return

        val event = NotificationEvent(
            packageName = packageName,
            appLabel = resolveLabel(packageName),
            title = title.ifBlank { "（无标题）" },
            body = body.ifBlank { ticker.ifBlank { "（无内容）" } },
            ticker = ticker,
            postedAt = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
            channelHint = paymentHintParser.channelHint(packageName, title, body),
            amountHint = amount,
            rawKey = "${packageName}|${sbn.id}|${sbn.postTime}|$title|$body",
        )

        scope.launch {
            if (repository.saveIfNew(event)) {
                pushSync.enqueueFromEvent(event)
            }
        }
    }

    private fun resolveLabel(packageName: String): String =
        try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }

    companion object {
        fun requestRebind(context: android.content.Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            val cn = ComponentName(context, PaymentNotificationListener::class.java)
            requestRebind(cn)
        }
    }
}
