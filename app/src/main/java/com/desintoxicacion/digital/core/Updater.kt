package com.desintoxicacion.digital.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import com.desintoxicacion.digital.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val apkUrl: String, val notes: String)

/** Actualizaciones desde la propia app: pregunta a la web por la última versión, la descarga e instala. */
object Updater {
    private val main = Handler(Looper.getMainLooper())

    fun check(done: (UpdateInfo?) -> Unit) {
        Server.call("version", auth = false) { o, _ ->
            if (o == null) return@call done(null)
            val info = UpdateInfo(o.optInt("versionCode"), o.optString("apkUrl"), o.optString("notes"))
            done(if (info.versionCode > BuildConfig.VERSION_CODE && info.apkUrl.startsWith("https://")) info else null)
        }
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Descarga el APK y abre el instalador de Android. done(error) en el hilo principal. */
    fun downloadAndInstall(ctx: Context, info: UpdateInfo, done: (String?) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            val err = try {
                val dir = File(app.cacheDir, "updates").apply { mkdirs() }
                val file = File(dir, "update.apk")
                var url = info.apkUrl
                var finished = false
                var hops = 0
                while (!finished && hops < 6) {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.instanceFollowRedirects = false
                    c.connectTimeout = 20000
                    c.readTimeout = 60000
                    val code = c.responseCode
                    if (code in 300..399) {
                        url = c.getHeaderField("Location") ?: throw IllegalStateException("Redirección sin destino")
                        hops++
                    } else if (code in 200..299) {
                        c.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                        finished = true
                    } else {
                        throw IllegalStateException("Error $code al descargar")
                    }
                    c.disconnect()
                }
                if (!finished) throw IllegalStateException("Demasiadas redirecciones")
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                val i = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(i)
                null
            } catch (e: Exception) {
                "No se pudo descargar la actualización: ${e.message ?: e.javaClass.simpleName}"
            }
            main.post { done(err) }
        }.start()
    }
}
