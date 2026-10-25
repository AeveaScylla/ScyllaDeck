package com.stremioaddons

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import coil.compose.SubcomposeAsyncImage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val vm: VM by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            AddonTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { if (vm.authKey == null) Login(vm) else Main(vm) }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }
    private fun handle(i: Intent?) {
        val t = when (i?.action) { Intent.ACTION_VIEW -> i.dataString; Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT); else -> null } ?: return
        Regex("(https?|stremio)://\\S+").find(t)?.value?.let { vm.pendingInstall = it }
    }
}

sealed interface Dlg {
    data class Details(val c: Card) : Dlg
    data class Delete(val urls: Set<String>) : Dlg
    data class MoveTo(val url: String) : Dlg
    data class WarnOff(val urls: Set<String>) : Dlg
    object Backups : Dlg
    object Profiles : Dlg
    object Dupes : Dlg
}

// ---------- small building blocks ----------
@Composable
fun Pill(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun Key(t: String, danger: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (danger) cs.errorContainer else cs.surfaceVariant).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(t, fontWeight = FontWeight.SemiBold, color = if (danger) cs.onErrorContainer else cs.onSurface)
    }
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp, shadowElevation = 10.dp
    ) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

@Composable
fun ColSwitch(cur: Int, onSel: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(cs.surfaceVariant).padding(2.dp)) {
        for (n in 1..3) {
            val on = n == cur
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) cs.primary else Color.Transparent).clickable { onSel(n) }.padding(horizontal = 11.dp, vertical = 6.dp)) {
                Text("$n", color = if (on) cs.onPrimary else cs.onSurfaceVariant, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun Login(vm: VM) {
    var e by remember { mutableStateOf("") }; var p by remember { mutableStateOf("") }; var k by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("ScyllaDeck", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Arrange, park and install your Stremio add-ons.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(e, { e = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(p, { p = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button({ vm.login(e.trim(), p) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Log in") }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Facebook/Apple login? Paste your authKey instead (web.stremio.com → browser dev tools → Local Storage → profile → auth key).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(k, { k = it }, label = { Text("authKey") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton({ vm.useKey(k.trim()) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Use authKey") }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Main(vm: VM) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    val host = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var cfg by remember { mutableStateOf<Card?>(null) }
    var captured by remember { mutableStateOf<Pair<Card, String>?>(null) }
    var discover by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vm.message) { vm.message?.let { host.showSnackbar(it); vm.message = null } }

    val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        u?.let { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(vm.layoutJson().toByteArray()) }; vm.message = "Layout exported" }
    }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { val t = ctx.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }; if (t != null) vm.importLayout(t) }
    }
    val scan = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let { vm.pendingInstall = it } }

    fun toggleReq(urls: Set<String>) {
        val off = vm.cards.filter { it.url in urls && it.enabled }
        if (off.any { it.id.contains("cinemeta", true) }) dlg = Dlg.WarnOff(urls) else vm.toggle(urls)
    }

    val dupSet = remember(vm.cards) { vm.dupGroups().flatten().map { it.url }.toSet() }
    val vis = vm.cards.filter {
        (!vm.onlyEnabled || it.enabled) && it.name.contains(vm.query, true) &&
            (!vm.onlyProblems || vm.health[it.url].let { ms -> ms != null && (ms < 0 || ms > Api.SLOW_MS) })
    }
    val pos = vm.cards.withIndex().associate { it.value.url to it.index + 1 }

    Scaffold(
        containerColor = cs.background,
        snackbarHost = { SnackbarHost(host) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                title = {
                    if (search) TextField(
                        vm.query, { vm.query = it }, singleLine = true, placeholder = { Text("Search add-ons…") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                    else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ScyllaDeck", fontWeight = FontWeight.Bold)
                        Pill("${vm.cards.count { it.enabled }}/${vm.cards.size}", cs.primaryContainer, cs.onPrimaryContainer)
                    }
                },
                actions = {
                    ColSwitch(vm.cols) { vm.setColumns(it) }
                    TextButton({ search = !search; if (!search) vm.query = "" }) { Text(if (search) "✕" else "🔍") }
                    Box {
                        TextButton({ menu = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                        DropdownMenu(menu, { menu = false }) {
                            @Composable fun item(t: String, f: () -> Unit) = DropdownMenuItem(text = { Text(t) }, onClick = { menu = false; f() })
                            item(if (vm.multi) "Exit multi-select" else "Multi-select") { vm.multi = !vm.multi; vm.selected = emptySet() }
                            item(if (vm.onlyEnabled) "Show all" else "Show enabled only") { vm.onlyEnabled = !vm.onlyEnabled }
                            item("Refresh from Stremio") { vm.refresh() }
                            item(if (vm.checking) "Checking…" else "Check add-ons health") { vm.checkHealth() }
                            item(if (vm.onlyProblems) "Show all add-ons" else "Show slow/dead only") { vm.onlyProblems = !vm.onlyProblems }
                            item("Find duplicates (${dupSet.size})") { dlg = Dlg.Dupes }
                            item("Auto-apply: ${if (vm.autoApply) "ON" else "off"}") { vm.setAuto(!vm.autoApply) }
                            item("Discover add-ons") { discover = true }
                            item("Backups") { dlg = Dlg.Backups }
                            item("Profiles") { dlg = Dlg.Profiles }
                            item("Export layout") { exp.launch("stremio-layout.json") }
                            item("Import layout") { imp.launch(arrayOf("*/*")) }
                            item("Log out") { vm.logout() }
                        }
                    }
                })
        },
        floatingActionButton = {
            FloatingActionButton({ vm.pendingInstall = "" }, containerColor = cs.primary, contentColor = cs.onPrimary, shape = RoundedCornerShape(18.dp)) {
                Text("＋", style = MaterialTheme.typography.headlineSmall)
            }
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                if (vm.dirty) Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (vm.autoApply) "Auto-applying…" else "Unsaved changes", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        TextButton({ vm.undoLast() }, enabled = vm.undoCount > 0) { Text("Undo") }
                        TextButton({ vm.discard() }) { Text("Discard") }
                        Spacer(Modifier.width(4.dp))
                        Button({ vm.apply() }, enabled = !vm.busy) { Text("Apply") }
                    }
                }
                if (vm.selected.isNotEmpty()) Panel {
                    val one = vm.selected.singleOrNull()
                    Text(if (one != null) "Move" else "${vm.selected.size} selected", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    if (one != null) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Key("◀") { vm.move(one, -1) }
                        Key("▶") { vm.move(one, 1) }
                        Key("▲") { vm.move(one, -vm.cols) }
                        Key("▼") { vm.move(one, vm.cols) }
                        Key("⤒") { vm.move(one, -100000) }
                        Key("⤓") { vm.move(one, 100000) }
                        Key("#…") { dlg = Dlg.MoveTo(one) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Key("On / Off") { toggleReq(vm.selected) }
                        Key("Delete", danger = true) { dlg = Dlg.Delete(vm.selected) }
                        Key("Clear") { vm.selected = emptySet() }
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val gs = rememberLazyGridState()
            val rs = rememberReorderableLazyGridState(gs) { from, to -> vm.dragMove(from.key as String, to.key as String) }
            LazyVerticalGrid(
                GridCells.Fixed(vm.cols), state = gs, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(vis, key = { _, c -> c.url }) { _, c ->
                    ReorderableItem(rs, key = c.url) {
                        CardView(
                            c, pos[c.url] ?: 0, c.url in vm.selected, c.url in vm.soft, vm.health[c.url], c.url in dupSet,
                            Modifier.longPressDraggableHandle(onDragStarted = { vm.pushUndo() }),
                            onTap = { vm.tap(c.url) }, onToggle = { toggleReq(setOf(c.url)) },
                            onMenu = { act ->
                                when (act) {
                                    "configure" -> cfg = c
                                    "browser" -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.configureUrl)))
                                    "details" -> dlg = Dlg.Details(c)
                                    "copy" -> { clip.setText(AnnotatedString(c.url)); vm.message = "URL copied" }
                                    "delete" -> dlg = Dlg.Delete(setOf(c.url))
                                }
                            })
                    }
                }
            }
            if (vm.cards.isEmpty() && !vm.busy) Text("No add-ons yet.\nUse Refresh from the ⋮ menu, or tap ＋", Modifier.align(Alignment.Center).padding(24.dp), color = cs.onSurfaceVariant)
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    // ---------- dialogs ----------
    vm.pendingInstall?.let { init ->
        var t by remember(init) { mutableStateOf(init) }
        AlertDialog(
            onDismissRequest = { vm.pendingInstall = null }, title = { Text("Install add-on") },
            text = {
                Column {
                    OutlinedTextField(t, { t = it }, label = { Text("manifest.json or stremio:// link") }, modifier = Modifier.fillMaxWidth())
                    Row {
                        TextButton({ clip.getText()?.text?.let { t = it } }) { Text("Paste") }
                        TextButton({ scan.launch(ScanOptions().setBeepEnabled(false).setPrompt("Scan add-on QR")) }) { Text("Scan QR") }
                    }
                }
            },
            confirmButton = { TextButton({ vm.pendingInstall = null; if (t.isNotBlank()) vm.install(t) }) { Text("Install") } },
            dismissButton = { TextButton({ vm.pendingInstall = null }) { Text("Cancel") } })
    }

    if (discover) DiscoverScreen(vm, onLink = { found = it }, onPaste = { clip.getText()?.text }, onClose = { discover = false })
    found?.let { link ->
        AlertDialog(
            onDismissRequest = { found = null }, title = { Text("Add this add-on?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ScyllaDeck will add it to your list. Tap Apply afterwards to sync it to Stremio.")
                    Text(link, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton({ found = null; vm.install(link) }) { Text("Add") } },
            dismissButton = { TextButton({ found = null }) { Text("Cancel") } })
    }
    cfg?.let { c -> ConfigWebView(c, onLink = { link -> cfg = null; captured = c to link }, onClose = { cfg = null }) }
    captured?.let { (c, link) ->
        AlertDialog(
            onDismissRequest = { captured = null }, title = { Text("New link found") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Replace ${c.name} with the configured version? It keeps its position.")
                    Text(link, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton({ captured = null; vm.replaceAddon(c.url, link) }) { Text("Replace") } },
            dismissButton = {
                Row {
                    TextButton({ captured = null; vm.install(link) }) { Text("Add as new") }
                    TextButton({ captured = null }) { Text("Cancel") }
                }
            })
    }

    when (val d = dlg) {
        is Dlg.Details -> ModalBottomSheet(onDismissRequest = { dlg = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(d.c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("v${d.c.version}", cs.primaryContainer, cs.onPrimaryContainer)
                    if (d.c.isProtected) Pill("protected", cs.errorContainer, cs.onErrorContainer)
                    if (d.c.configurable) Pill("configurable", cs.secondaryContainer, cs.onSecondaryContainer)
                }
                if (d.c.description.isNotBlank()) Text(d.c.description)
                Text("ID", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant); Text(d.c.id)
                Text("Types", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant); Text(d.c.types.joinToString().ifBlank { "—" })
                Text("Resources", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant); Text(d.c.resources.joinToString().ifBlank { "—" })
                Text("URL", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant); Text(d.c.url, style = MaterialTheme.typography.bodySmall)
            }
        }
        is Dlg.Delete -> AlertDialog(
            onDismissRequest = { dlg = null }, title = { Text("Delete ${d.urls.size} add-on(s)?") },
            text = { Text("They're removed from your list when you tap Apply. Protected add-ons are skipped (use the switch to turn them off).") },
            confirmButton = { TextButton({ vm.delete(d.urls); dlg = null }) { Text("Delete") } },
            dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } })
        is Dlg.WarnOff -> AlertDialog(
            onDismissRequest = { dlg = null }, title = { Text("Turn off Cinemeta?") },
            text = { Text("Without Cinemeta, many catalogs lose posters, titles and metadata. You can turn it back on any time; it returns to its same slot.") },
            confirmButton = { TextButton({ vm.toggle(d.urls); dlg = null }) { Text("Turn off") } },
            dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } })
        is Dlg.MoveTo -> {
            var n by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dlg = null }, title = { Text("Move to position (1–${vm.cards.size})") },
                text = { OutlinedTextField(n, { n = it.filter(Char::isDigit) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
                confirmButton = { TextButton({ n.toIntOrNull()?.let { vm.moveTo(d.url, it.coerceIn(1, vm.cards.size)) }; dlg = null }) { Text("Move") } },
                dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } })
        }
        Dlg.Dupes -> ModalBottomSheet(onDismissRequest = { dlg = null }) {
            val groups = remember(vm.cards) { vm.dupGroups() }
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Possible duplicates", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Same add-on ID, or same name with the same resources. Two different configs of one add-on can be legitimate.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                if (groups.isEmpty()) Text("No duplicates found.")
                groups.forEach { g ->
                    Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer) {
                        Column(Modifier.padding(12.dp)) {
                            g.forEach { c ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("#${pos[c.url]}  ${c.name} v${c.version}", fontWeight = FontWeight.SemiBold)
                                        Text(c.url.substringAfter("://").take(40), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                                        if (!c.enabled) Text("parked", style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton({ vm.keepOnly(c.url, g.map { it.url }) }) { Text("Keep only") }
                                    TextButton({ vm.delete(setOf(c.url)) }, enabled = !c.isProtected) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
        Dlg.Backups -> {
            LaunchedEffect(Unit) { vm.loadBackups() }
            AlertDialog(
                onDismissRequest = { dlg = null }, title = { Text("Backups") },
                text = {
                    if (vm.backups.isEmpty()) Text("No backups yet. One is saved before every Apply.")
                    else LazyColumn { items(vm.backups) { b ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(DateFormat.getDateTimeInstance().format(Date(b.ts))); Text(b.note, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                            TextButton({ vm.restore(b); dlg = null }) { Text("Restore") }
                        }
                    } }
                }, confirmButton = { TextButton({ dlg = null }) { Text("Close") } })
        }
        Dlg.Profiles -> {
            var name by remember { mutableStateOf("") }
            LaunchedEffect(Unit) { vm.loadProfiles() }
            AlertDialog(
                onDismissRequest = { dlg = null }, title = { Text("Profiles") },
                text = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                            TextButton({ vm.saveProfile(name); name = "" }) { Text("Save current") }
                        }
                        LazyColumn { items(vm.profiles) { p ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, Modifier.weight(1f))
                                TextButton({ vm.importLayout(p.json); dlg = null }) { Text("Load") }
                                TextButton({ vm.deleteProfile(p) }) { Text("✕") }
                            }
                        } }
                    }
                }, confirmButton = { TextButton({ dlg = null }) { Text("Close") } })
        }
        null -> {}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardView(c: Card, n: Int, sel: Boolean, soft: Boolean, health: Long?, dup: Boolean, drag: Modifier, onTap: () -> Unit, onToggle: () -> Unit, onMenu: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    val dashColor = cs.outline
    Card(
        Modifier.fillMaxWidth().alpha(if (c.enabled) 1f else 0.55f).then(drag).clickable(onClick = onTap),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (sel) cs.primaryContainer else cs.surfaceContainer),
        border = when {
            sel -> BorderStroke(2.dp, cs.primary)
            c.enabled -> BorderStroke(1.dp, cs.primary.copy(alpha = 0.35f))
            else -> null
        }
    ) {
        Column(
            Modifier.drawWithContent {
                drawContent()
                if (!c.enabled && !sel) drawRoundRect(dashColor, cornerRadius = CornerRadius(20.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
            }.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("#$n", cs.primary, cs.onPrimary)
                Spacer(Modifier.weight(1f))
                Switch(c.enabled, { onToggle() }, modifier = Modifier.scale(0.75f))
                Box {
                    Text("⋮", Modifier.clip(CircleShape).clickable { menu = true }.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.titleMedium)
                    DropdownMenu(menu, { menu = false }) {
                        if (c.configurable) {
                            DropdownMenuItem({ Text("Configure") }, { menu = false; onMenu("configure") })
                            DropdownMenuItem({ Text("Configure in browser") }, { menu = false; onMenu("browser") })
                        }
                        DropdownMenuItem({ Text("Details") }, { menu = false; onMenu("details") })
                        DropdownMenuItem({ Text("Copy URL") }, { menu = false; onMenu("copy") })
                        DropdownMenuItem({ Text(if (c.isProtected) "Delete (protected)" else "Delete") }, { menu = false; onMenu("delete") }, enabled = !c.isProtected)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val initial: @Composable () -> Unit = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(c.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = cs.primary, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant)) {
                    if (c.logo.isBlank()) initial()
                    else SubcomposeAsyncImage(c.logo, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit, loading = { initial() }, error = { initial() })
                }
                Column {
                    Text(c.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("v${c.version}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            val hasTags = !c.enabled || soft || c.isProtected || health != null || dup
            if (hasTags) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!c.enabled) Pill("off", cs.surfaceVariant, cs.onSurfaceVariant)
                if (soft) Pill("soft", cs.tertiaryContainer, cs.onTertiaryContainer)
                if (c.isProtected) Pill("locked", cs.secondaryContainer, cs.onSecondaryContainer)
                if (health != null) {
                    if (health < 0) Pill("dead", cs.errorContainer, cs.onErrorContainer)
                    else if (health > Api.SLOW_MS) Pill("slow ${health / 100 / 10.0}s", cs.tertiaryContainer, cs.onTertiaryContainer)
                    else Pill("ok ${health}ms", cs.secondaryContainer, cs.onSecondaryContainer)
                }
                if (dup) Pill("dup?", cs.tertiaryContainer, cs.onTertiaryContainer)
            }
        }
    }
}

fun isAddonLink(u: String, orig: String): Boolean {
    if (u.startsWith("stremio://")) return true
    return u.substringBefore('?').endsWith("manifest.json") && u != orig
}

@Composable
fun ConfigWebView(c: Card, onLink: (String) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Configure ${c.name}", Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClose) { Text("Close") }
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val u = request.url.toString()
                                    return if (isAddonLink(u, c.url)) { onLink(u); true } else false
                                }
                            }
                            loadUrl(c.configureUrl)
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onRelease = { it.destroy() }
                )
            }
        }
    }
}

const val DISCOVER_URL = "https://stremio-addons.net"

fun isInstallLink(u: String): Boolean = u.startsWith("stremio://") || u.substringBefore('?').endsWith("manifest.json")

@Composable
fun DiscoverScreen(vm: VM, onLink: (String) -> Unit, onPaste: () -> String?, onClose: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var last by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm.message) { vm.message?.let { last = it } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Discover add-ons", Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton({ web?.goBack() }) { Text("Back") }
                    TextButton({ val t = onPaste(); if (!t.isNullOrBlank()) onLink(t.trim()) }) { Text("Paste link") }
                    TextButton(onClose) { Text("Close") }
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val u = request.url.toString()
                                    return if (isInstallLink(u)) { onLink(u); true } else false
                                }
                            }
                            loadUrl(DISCOVER_URL)
                        }
                    },
                    update = { web = it },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onRelease = { it.destroy() }
                )
                last?.let { Text(it, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
