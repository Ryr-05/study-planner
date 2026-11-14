package io.github.ryr05.noctil.plan

import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.FirebaseAppCheck
import io.github.ryr05.noctil.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * The backup AI: when Gemini is out of free messages (or down), the same chat goes to Noctil's
 * own relay on Cloudflare, which asks Groq (Llama) and then Cloudflare Workers AI. The keys live
 * only on the relay. Requests carry a Firebase App Check token so the relay can tell it's the app.
 */
object AiRelay {
    /** The relay's address (not a secret). Empty turns the backup off. */
    const val URL_BASE = "https://noctil-relay.ryanmosquisa.workers.dev"

    /** Debug builds only: skip Gemini to try the backup (set by a "force_relay" file in the app's files). */
    @Volatile var forceInDebug = false

    val available: Boolean get() = BuildConfig.CLOUD_SYNC && URL_BASE.isNotBlank()

    /** One chat turn: (role, text) pairs with "system", "user" or "assistant". Returns the model's JSON text. */
    suspend fun chat(messages: List<Pair<String, String>>): String = withContext(Dispatchers.IO) {
        check(available) { "No relay" }
        val body = JSONObject().put("messages", JSONArray(messages.map { (role, text) -> JSONObject().put("role", role).put("content", text) }))
        val conn = (URL("$URL_BASE/chat").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            appCheckToken()?.let { setRequestProperty("X-Firebase-AppCheck", it) }
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(code in 200..299) { "Relay $code" }
            val o = JSONObject(text)
            if (BuildConfig.DEBUG) android.util.Log.d("NoctilAI", "relay (${o.optString("provider")}): ${o.optString("content").take(400)}")
            o.getString("content")
        } finally {
            conn.disconnect()
        }
    }

    private fun appCheckToken(): String? = runCatching {
        Tasks.await(FirebaseAppCheck.getInstance().getAppCheckToken(false), 10, TimeUnit.SECONDS).token
    }.getOrNull()
}
