package com.stremioaddons

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

class VM(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val dao = Room.databaseBuilder(app, Db::class.java, "addons.db").build().dao()

    var authKey by mutableStateOf(Store.key(app))
    var cards by mutableStateOf(listOf<Card>()); private set     // working layout (enabled + parked)
    var remote by mutableStateOf(listOf<Card>()); private set    // last known server state
    var soft by mutableStateOf(setOf<String>()); private set     // parked but server refused removal
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var selected by mutableStateOf(setOf<String>())
    var multi by mutableStateOf(false)
    var query by mutableStateOf("")
    var onlyEnabled by mutableStateOf(false)
    var cols by mutableIntStateOf(Store.cols(app))
    var pendingInstall by mutableStateOf<String?>(null)
    var health by mutableStateOf(mapOf<String, Long>())      // url -> ms, -1 = dead
    var checking by mutableStateOf(false)
    var onlyProblems by mutableStateOf(false)
    var autoApply by mutableStateOf(Store.auto(app))
    private var autoJob: Job? = null
    var backups by mutableStateOf(listOf<Backup>())
    var profiles by mutableStateOf(listOf<Profile>())
    private val undo = ArrayDeque<List<Card>>()
    var undoCount by mutableIntStateOf(0)
    private var persistJob: Job? = null

    val dirty: Boolean get() = cards.filter { it.enabled }.map { it.url } != remote.map { it.url }.filter { it !in soft }

    init {
        viewModelScope.launch {
            cards = dao.slots().map { Card(it.transportUrl, it.enabled, it.descriptorJson) }
            if (authKey != null) go { doRefresh() }
        }
    }

    private fun go(b: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            try { b() } catch (e: CancellationException) { throw e } catch (e: Exception) { message = e.message ?: e.toString() } finally { busy = false }
        }
    }
    private suspend fun <T> io(f: () -> T): T = withContext(Dispatchers.IO) { f() }

    // ---------- auth ----------
    fun login(email: String, pw: String) = go {
        val k = io { Api.login(email, pw) }
        Store.setKey(ctx, k); authKey = k; doRefresh()
    }
    fun useKey(k: String) = go {
        io { Api.get(k) }
        Store.setKey(ctx, k); authKey = k; doRefresh()
    }
    fun logout() {
        Store.setKey(ctx, null); authKey = null; cards = emptyList(); remote = emptyList(); soft = emptySet(); undo.clear(); undoCount = 0
        viewModelScope.launch { dao.clearSlots() }
    }

    // ---------- state helpers ----------
    private fun commitCards(l: List<Card>) {
        cards = l
        persistJob?.cancel()
        persistJob = viewModelScope.launch { dao.replaceSlots(l.mapIndexed { i, c -> Slot(c.url, i, c.enabled, c.json) }) }
        if (autoApply && authKey != null) scheduleAuto()
    }

    // ---------- auto-apply ----------
    private fun scheduleAuto() {
        autoJob?.cancel()
        autoJob = viewModelScope.launch {
            delay(2000)
            while (busy) delay(500)
            if (dirty && autoApply) apply()
        }
    }
    fun setAuto(on: Boolean) {
        autoApply = on; Store.setAuto(ctx, on)
        if (on && dirty) scheduleAuto() else autoJob?.cancel()
    }

    // ---------- health check ----------
    fun checkHealth() {
        if (checking || cards.isEmpty()) return
        val urls = cards.map { it.url }
        health = emptyMap(); checking = true
        viewModelScope.launch {
            val sem = Semaphore(8)
            try {
                coroutineScope {
                    urls.map { u ->
                        async(Dispatchers.IO) {
                            val ms = sem.withPermit { Api.ping(u) }
                            withContext(Dispatchers.Main) { health = health + (u to ms) }
                        }
                    }.awaitAll()
                }
            } finally { checking = false }
            val dead = health.values.count { it < 0 }; val slow = health.values.count { it > Api.SLOW_MS }
            message = "Checked ${health.size}: $dead dead, $slow slow"
        }
    }

    // ---------- duplicates ----------
    fun dupGroups(): List<List<Card>> {
        val byId = cards.filter { it.id.isNotBlank() }.groupBy { it.id }.values.filter { it.size > 1 }
        val used = byId.flatten().map { it.url }.toSet()
        val byName = cards.filter { it.url !in used }
            .groupBy { it.name.trim().lowercase() + "|" + it.resources.sorted().joinToString(",") + "|" + it.types.sorted().joinToString(",") }
            .values.filter { it.size > 1 }
        return byId + byName
    }
    fun keepOnly(keep: String, group: List<String>) {
        mutate(cards.map { when { it.url == keep -> it.copy(enabled = true); it.url in group -> it.copy(enabled = false); else -> it } })
    }

    // ---------- replace after configuring ----------
    fun replaceAddon(oldUrl: String, raw: String) = go {
        val url = Api.normalize(raw)
        val m = io { Api.manifest(url) }
        val idx = cards.indexOfFirst { it.url == oldUrl }
        if (idx < 0) { message = "Original add-on not found"; return@go }
        val old = cards[idx]
        val flags = JSONObject(old.json).optJSONObject("flags") ?: JSONObject()
        val desc = JSONObject().put("transportUrl", url).put("transportName", "").put("manifest", m).put("flags", flags)
        val l = cards.toMutableList(); l[idx] = Card(url, old.enabled, desc.toString())
        mutate(l.filterIndexed { i, c -> i == idx || c.url != url })
        message = "Replaced with configured ${m.optString("name")}"
    }
    fun pushUndo() { undo.addLast(cards); if (undo.size > 50) undo.removeFirst(); undoCount = undo.size }
    private fun mutate(l: List<Card>) { pushUndo(); commitCards(l) }
    fun undoLast() { if (undo.isNotEmpty()) { commitCards(undo.removeLast()); undoCount = undo.size } }

    private fun buildClean(rc: List<Card>, local: List<Card>): MutableList<Card> {
        val base = rc.map { if (it.url in soft) it.copy(enabled = false) else it }.toMutableList()
        val rUrls = rc.map { it.url }.toSet()
        local.forEachIndexed { i, c -> if (!c.enabled && c.url !in rUrls) base.add(minOf(i, base.size), c) }
        return base
    }

    fun discard() { commitCards(buildClean(remote, cards)); selected = emptySet(); undo.clear(); undoCount = 0 }

    // ---------- sync ----------
    fun refresh() = go { doRefresh() }

    private suspend fun doRefresh(force: Boolean = false) {
        val key = authKey ?: return
        val rc = io { Api.get(key).objs().map { Card(it.getString("transportUrl"), true, it.toString()) } }
        val rUrls = rc.map { it.url }.toSet()
        val wasClean = !dirty
        soft = soft.filter { it in rUrls }.toSet()
        val merged: List<Card> = if (cards.isEmpty() || wasClean || force) {
            val base = rc.map { if (it.url in soft) it.copy(enabled = false) else it }.toMutableList()
            cards.forEachIndexed { i, c -> if (!c.enabled && c.url !in rUrls) base.add(minOf(i, base.size), c) }
            base
        } else {
            val out = ArrayList<Card>()
            for (c in cards) {
                val r = rc.firstOrNull { it.url == c.url }
                if (r != null) out.add(Card(c.url, c.enabled, r.json)) else if (!c.enabled) out.add(c)
            }
            val have = out.map { it.url }.toSet()
            rc.filter { it.url !in have }.forEach { out.add(it) }
            out
        }
        remote = rc
        commitCards(merged)
    }

    // ---------- selection / editing ----------
    fun tap(url: String) {
        selected = if (multi) (if (url in selected) selected - url else selected + url)
        else (if (selected == setOf(url)) emptySet() else setOf(url))
    }
    fun toggle(urls: Set<String>) {
        val anyOn = cards.any { it.url in urls && it.enabled }
        mutate(cards.map { if (it.url in urls) it.copy(enabled = !anyOn) else it })
    }
    fun delete(urls: Set<String>) {
        val ok = urls.filter { u -> cards.firstOrNull { it.url == u }?.isProtected != true }.toSet()
        if (ok.size < urls.size) message = "Protected add-ons can't be deleted (turn them off instead)"
        if (ok.isNotEmpty()) mutate(cards.filter { it.url !in ok })
        selected = emptySet()
    }
    fun move(url: String, delta: Int) {
        val i = cards.indexOfFirst { it.url == url }; if (i < 0) return
        val j = (i + delta).coerceIn(0, cards.size - 1); if (i == j) return
        val l = cards.toMutableList(); l.add(j, l.removeAt(i)); mutate(l)
    }
    fun moveTo(url: String, pos: Int) { val i = cards.indexOfFirst { it.url == url }; if (i >= 0) move(url, (pos - 1) - i) }
    fun dragMove(from: String, to: String) {
        val i = cards.indexOfFirst { it.url == from }; val j = cards.indexOfFirst { it.url == to }
        if (i < 0 || j < 0 || i == j) return
        val l = cards.toMutableList(); l.add(j, l.removeAt(i)); commitCards(l)
    }
    fun setColumns(n: Int) { cols = n; Store.setCols(ctx, n) }

    // ---------- install ----------
    fun install(raw: String) = go {
        val url = Api.normalize(raw)
        val m = io { Api.manifest(url) }
        val desc = JSONObject().put("transportUrl", url).put("transportName", "").put("manifest", m).put("flags", JSONObject())
        val idx = cards.indexOfFirst { it.url == url }
        if (idx >= 0) {
            val old = cards[idx]
            JSONObject(old.json).optJSONObject("flags")?.let { desc.put("flags", it) }
            val l = cards.toMutableList(); l[idx] = Card(url, old.enabled, desc.toString()); mutate(l)
        } else mutate(cards + Card(url, true, desc.toString()))
        message = "Added ${m.optString("name")} – tap Apply to sync"
    }

    // ---------- apply ----------
    fun apply() = go {
        val key = authKey ?: return@go
        val w = cards
        val enabled = w.filter { it.enabled }
        if (enabled.isEmpty()) { message = "Nothing enabled – refusing to push an empty list"; return@go }
        val latest = io { Api.get(key) }
        dao.addBackup(Backup(ts = System.currentTimeMillis(), note = "Before apply", json = latest.toString())); dao.pruneBackups()
        val wUrls = w.map { it.url }.toSet(); val oldRemote = remote.map { it.url }.toSet()
        val extras = latest.objs().filter { it.getString("transportUrl") !in wUrls && it.getString("transportUrl") !in oldRemote }
            .map { Card(it.getString("transportUrl"), true, it.toString()) }
        val list = enabled + extras
        io { Api.set(key, JSONArray(list.map { JSONObject(it.json) })) }
        var after = io { Api.get(key).objs() }
        val parked = w.filter { !it.enabled }.map { it.url }.toSet()
        val stuck = after.filter { it.getString("transportUrl") in parked }
        if (stuck.isNotEmpty()) {
            io { Api.set(key, JSONArray(list.map { JSONObject(it.json) } + stuck.map { JSONObject(it.toString()) })) }
            after = io { Api.get(key).objs() }
            soft = stuck.map { it.getString("transportUrl") }.toSet()
            message = "Server kept ${stuck.joinToString { it.getJSONObject("manifest").optString("name") }} – moved to the bottom instead of removed"
        } else { soft = emptySet(); message = "Applied ${list.size} add-ons" }
        remote = after.map { Card(it.getString("transportUrl"), true, it.toString()) }
        commitCards(cards + extras.filter { e -> cards.none { it.url == e.url } })
        selected = emptySet(); undo.clear(); undoCount = 0
    }

    // ---------- backups ----------
    fun loadBackups() { viewModelScope.launch { backups = dao.backups() } }
    fun restore(b: Backup) = go {
        val key = authKey ?: return@go
        val cur = io { Api.get(key) }
        dao.addBackup(Backup(ts = System.currentTimeMillis(), note = "Before restore", json = cur.toString()))
        io { Api.set(key, JSONArray(b.json)) }
        doRefresh(force = true)
        backups = dao.backups(); message = "Backup restored"
    }

    // ---------- layout export / profiles ----------
    fun layoutJson(): String = JSONObject().put("version", 1).put("slots", JSONArray(cards.map {
        JSONObject().put("enabled", it.enabled).put("descriptor", JSONObject(it.json))
    })).toString(2)

    fun importLayout(txt: String) {
        try {
            val l = JSONObject(txt).getJSONArray("slots").objs().map {
                val d = it.getJSONObject("descriptor")
                Card(d.getString("transportUrl"), it.optBoolean("enabled", true), d.toString())
            }
            if (l.isEmpty()) { message = "Layout file is empty"; return }
            mutate(l); selected = emptySet(); message = "Layout loaded – review, then tap Apply"
        } catch (e: Exception) { message = "Invalid layout file" }
    }
    fun loadProfiles() { viewModelScope.launch { profiles = dao.profiles() } }
    fun saveProfile(name: String) { viewModelScope.launch { dao.addProfile(Profile(name = name.ifBlank { "Profile" }, json = layoutJson())); profiles = dao.profiles() } }
    fun deleteProfile(p: Profile) { viewModelScope.launch { dao.deleteProfile(p.id); profiles = dao.profiles() } }
}
