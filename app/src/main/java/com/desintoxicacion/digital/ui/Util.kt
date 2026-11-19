package com.desintoxicacion.digital.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.desintoxicacion.digital.core.GuardService
import com.desintoxicacion.digital.core.Stats

object Perms {
    fun usage(ctx: Context) = Stats.hasAccess(ctx)

    fun accessibility(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val cn = ComponentName(ctx, GuardService::class.java)
        return enabled.split(':').any {
            it.equals(cn.flattenToString(), ignoreCase = true) || it.equals(cn.flattenToShortString(), ignoreCase = true)
        }
    }

    fun notificationListener(ctx: Context) =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun postNotifications(ctx: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun grayscale(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun openUsage(ctx: Context) = open(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    fun openAccessibility(ctx: Context) = open(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    fun openNotificationListener(ctx: Context) = open(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    fun openAppDetails(ctx: Context) =
        open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))

    private fun open(ctx: Context, i: Intent) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

fun goHome(ctx: Context) {
    ctx.startActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

data class AppEntry(val pkg: String, val label: String)

fun launchableApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != ctx.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

/** Palabras con las que se proponen marcadas las apps de cada lista. */
val WHITELIST_KEYWORDS = listOf(
    "crew photo", "hojas de cálculo", "sheets", "documentos", "docs", "maps", "to do", "grabadora",
    "recorder", "ten+", "ten +", "drive", "caixabank", "cámara", "camera", "reloj", "clock",
    "calculadora", "calculator",
)
val CARTELERA_KEYWORDS = listOf("cartelera")
val FREE_KEYWORDS = listOf("crew photo", "whatsapp business")

fun suggested(apps: List<AppEntry>, keywords: List<String>): Set<String> =
    apps.filter { a -> keywords.any { a.label.lowercase().contains(it) } }.map { it.pkg }.toSet()
