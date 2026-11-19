package com.desintoxicacion.digital.core

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Llamadas a la web de Google Apps Script. Todas en segundo plano; la respuesta llega en el hilo principal. */
object Server {
    private val main = Handler(Looper.getMainLooper())

    fun configured(): Boolean = Store.serverUrl.startsWith("https://")

    /** Llama a [action] con [params]. Si [auth], añade el usuario y su clave. */
    fun call(action: String, params: Map<String, String> = emptyMap(), auth: Boolean = true, done: (JSONObject?, String?) -> Unit) {
        if (!configured()) {
            done(null, "Falta la dirección de la web en Ajustes.")
            return
        }
        val all = LinkedHashMap<String, String>()
        all["action"] = action
        if (auth) {
            all["user"] = Store.userId
            all["secret"] = Store.secret
        }
        all.putAll(params)
        Thread {
            var result: JSONObject? = null
            var error: String? = null
            try {
                val o = getJson(buildUrl(Store.serverUrl, all))
                if (o.optBoolean("ok")) result = o else error = o.optString("error", "Error de la web")
            } catch (e: Exception) {
                error = "Sin conexión con la web (${e.javaClass.simpleName})."
            }
            main.post { done(result, error) }
        }.start()
    }

    private fun buildUrl(base: String, params: Map<String, String>): String {
        val q = params.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
        return base + (if (base.contains("?")) "&" else "?") + q
    }

    /** GET que sigue las redirecciones de Google (script.google.com -> script.googleusercontent.com). */
    private fun getJson(url: String): JSONObject {
        var current = url
        repeat(5) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000
            c.readTimeout = 20000
            val code = c.responseCode
            if (code in 300..399) {
                current = c.getHeaderField("Location") ?: throw IllegalStateException("Redirección sin destino")
                c.disconnect()
                return@repeat
            }
            val body = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
            c.disconnect()
            return JSONObject(body)
        }
        throw IllegalStateException("Demasiadas redirecciones")
    }
}
