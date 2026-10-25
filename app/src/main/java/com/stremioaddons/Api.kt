package com.stremioaddons

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strs(): List<String> = (0 until length()).map { val v = get(it); if (v is JSONObject) v.optString("name") else v.toString() }

/** An add-on as shown in the grid. json = full Stremio descriptor (transportUrl, transportName, manifest, flags). */
data class Card(val url: String, val enabled: Boolean, val json: String) {
    private val d by lazy { JSONObject(json) }
    private val m get() = d.optJSONObject("manifest") ?: JSONObject()
    val id: String get() = m.optString("id")
    val name: String get() = m.optString("name", url)
    val version: String get() = m.optString("version", "?")
    val logo: String get() = m.optString("logo")
    val description: String get() = m.optString("description")
    val isProtected: Boolean get() = d.optJSONObject("flags")?.optBoolean("protected") == true
    val configurable: Boolean get() = m.optJSONObject("behaviorHints")?.optBoolean("configurable") == true
    val types: List<String> get() = m.optJSONArray("types")?.strs() ?: emptyList()
    val resources: List<String> get() = m.optJSONArray("resources")?.strs() ?: emptyList()
    val configureUrl: String get() = url.substringBefore('?').removeSuffix("manifest.json") + "configure"
}

object Api {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build()
    private val JSON = "application/json".toMediaType()
    const val SLOW_MS = 3000L
    private val fast = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS).build()

    /** Response time in ms, or -1 if the add-on is unreachable / returns an error. */
    fun ping(url: String): Long {
        val t = System.nanoTime()
        return try {
            fast.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) -1L else { r.body?.string(); (System.nanoTime() - t) / 1_000_000 }
            }
        } catch (e: Exception) { -1L }
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val req = Request.Builder().url("https://api.strem.io/api/$path").post(body.toString().toRequestBody(JSON)).build()
        http.newCall(req).execute().use { r ->
            val o = JSONObject(r.body?.string() ?: "{}")
            val e = o.opt("error")
            if (e != null && e != JSONObject.NULL) throw IOException(if (e is JSONObject) e.optString("message", "API error") else e.toString())
            return o
        }
    }

    fun login(email: String, pw: String): String =
        post("login", JSONObject().put("type", "Login").put("email", email).put("password", pw).put("facebook", false))
            .getJSONObject("result").getString("authKey")

    fun get(key: String, update: Boolean = false): JSONArray =
        post("addonCollectionGet", JSONObject().put("type", "AddonCollectionGet").put("authKey", key).put("update", update))
            .getJSONObject("result").getJSONArray("addons")

    fun set(key: String, addons: JSONArray) {
        post("addonCollectionSet", JSONObject().put("type", "AddonCollectionSet").put("authKey", key).put("addons", addons))
    }

    fun manifest(url: String): JSONObject {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} fetching manifest")
            val m = JSONObject(r.body?.string() ?: "")
            for (k in listOf("id", "name", "version")) if (m.optString(k).isBlank()) throw IOException("Invalid manifest: missing '$k'")
            return m
        }
    }

    fun normalize(s: String): String {
        var u = s.trim()
        if (u.startsWith("stremio://")) u = "https://" + u.removePrefix("stremio://")
        if (!u.substringBefore('?').endsWith("manifest.json")) u = u.trimEnd('/') + "/manifest.json"
        return u
    }
}

object Store {
    private fun enc(c: Context) = EncryptedSharedPreferences.create(
        c, "secure", MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun key(c: Context): String? = try { enc(c).getString("k", null) } catch (e: Exception) { null }
    fun setKey(c: Context, k: String?) { enc(c).edit().apply { if (k == null) remove("k") else putString("k", k) }.apply() }
    fun cols(c: Context) = c.getSharedPreferences("p", 0).getInt("cols", 2)
    fun setCols(c: Context, n: Int) { c.getSharedPreferences("p", 0).edit().putInt("cols", n).apply() }
    fun auto(c: Context) = c.getSharedPreferences("p", 0).getBoolean("auto", false)
    fun setAuto(c: Context, v: Boolean) { c.getSharedPreferences("p", 0).edit().putBoolean("auto", v).apply() }
}
