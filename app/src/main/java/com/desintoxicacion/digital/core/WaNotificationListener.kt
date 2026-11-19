package com.desintoxicacion.digital.core

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Borra al instante las notificaciones de WhatsApp (para que no hagan ruido) y,
 * durante una desconexión total larga, responde automáticamente a quien te escribe.
 */
class WaNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Store.init(this)
        val p = Profiles.active()
        if (sbn.packageName !in p.waPackages) return
        val n = sbn.notification
        val isSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0

        if (!isSummary && Disconnect.autoReplyActive(p)) {
            try {
                tryAutoReply(n, p)
            } catch (_: Exception) {
            }
        }
        if (p.deleteWaNotifications) {
            try {
                cancelNotification(sbn.key)
            } catch (_: Exception) {
            }
        }
    }

    private fun tryAutoReply(n: Notification, p: Profile) {
        if (n.category == Notification.CATEGORY_CALL) return
        val ex = n.extras
        val isGroup = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) ||
            ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) != null
        if (isGroup) return
        val sender = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val key = n.shortcutId ?: sender
        if (Disconnect.alreadyReplied(key)) return

        val action = n.actions?.firstOrNull { a -> !a.remoteInputs.isNullOrEmpty() } ?: return
        val inputs: Array<RemoteInput> = action.remoteInputs ?: return
        val results = Bundle()
        inputs.forEach { results.putCharSequence(it.resultKey, p.autoReplyText) }
        val fill = Intent()
        RemoteInput.addResultsToIntent(inputs, fill, results)
        try {
            action.actionIntent.send(this, 0, fill)
            Disconnect.markReplied(key)
        } catch (_: PendingIntent.CanceledException) {
        }
    }
}
